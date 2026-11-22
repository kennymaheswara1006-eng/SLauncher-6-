package com.example.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class DynamicNotificationItem(
  val key: String,
  val packageName: String,
  val appName: String,
  val title: String,
  val text: String,
  val postTime: Long,
  val contentIntent: PendingIntent?,
  val isPreview: Boolean = false,
)

/**
 * Lightweight in-memory store fed directly by `SLauncherNotificationListener` callbacks
 * and device unlock signals.
 *
 * Separates:
 * - `notifications`: live active notification list (for Lock Screen card list)
 * - `bannerNotification` + `bannerTriggerId`: deduplicated transient pop-up event
 *   so notifications never pop up twice when switching between Home and other apps.
 */
object DynamicNotificationStore {

  private const val DUPLICATE_SIGNATURE_WINDOW_MS = 8_000L

  private val _notifications = MutableStateFlow<List<DynamicNotificationItem>>(emptyList())
  val notifications: StateFlow<List<DynamicNotificationItem>> = _notifications.asStateFlow()

  private val _bannerNotification = MutableStateFlow<DynamicNotificationItem?>(null)
  val bannerNotification: StateFlow<DynamicNotificationItem?> = _bannerNotification.asStateFlow()

  private val _bannerTriggerId = MutableStateFlow(0L)
  val bannerTriggerId: StateFlow<Long> = _bannerTriggerId.asStateFlow()

  private val _unlockEventCount = MutableStateFlow(0L)
  val unlockEventCount: StateFlow<Long> = _unlockEventCount.asStateFlow()

  private val pendingUnlockWelcome = AtomicBoolean(false)

  // Deduplication state guarded cleanly under syncLock
  private val syncLock = Any()
  private val lastPostTimeByKey = HashMap<String, Long>()
  private val lastSigByKey = HashMap<String, String>()
  private val recentPoppedBySignature = LinkedHashMap<String, Long>()

  // Tiny 16-icon LruCache for notification source app icons (memory-safe on 2GB RAM)
  private val packageIconCache = LruCache<String, ImageBitmap>(16)

  fun markPhoneUnlocked() {
    pendingUnlockWelcome.set(true)
    _unlockEventCount.update { it + 1L }
  }

  fun consumePhoneUnlocked(): Boolean = pendingUnlockWelcome.getAndSet(false)

  fun setAll(items: List<DynamicNotificationItem>) {
    synchronized(syncLock) {
      val sorted = items.sortedByDescending { it.postTime }.take(12)
      _notifications.value = sorted
      lastPostTimeByKey.clear()
      lastSigByKey.clear()
      sorted.forEach { item ->
        lastPostTimeByKey[item.key] = item.postTime
        lastSigByKey[item.key] = contentSignature(item)
      }
    }
  }

  fun upsert(item: DynamicNotificationItem) {
    upsertWithTime(item = item, nowMs = System.currentTimeMillis())
  }

  internal fun upsertWithTime(item: DynamicNotificationItem, nowMs: Long): Boolean {
    val sig = contentSignature(item)
    val shouldPopBanner: Boolean
    synchronized(syncLock) {
      val prevTime = lastPostTimeByKey[item.key]
      val prevSig = lastSigByKey[item.key]
      val lastPoppedAt = recentPoppedBySignature[sig]

      val isExactSamePayload = prevTime == item.postTime && prevSig == sig
      val isRapidDuplicateSignature =
        !item.isPreview &&
          lastPoppedAt != null &&
          (nowMs - lastPoppedAt) in 0L until DUPLICATE_SIGNATURE_WINDOW_MS

      shouldPopBanner = item.isPreview || (!isExactSamePayload && !isRapidDuplicateSignature)

      lastPostTimeByKey[item.key] = item.postTime
      lastSigByKey[item.key] = sig

      _notifications.update { current ->
        val filtered = current.filterNot { it.key == item.key }
        (listOf(item) + filtered).sortedByDescending { it.postTime }.take(12)
      }

      if (shouldPopBanner) {
        if (!item.isPreview) {
          recentPoppedBySignature[sig] = nowMs
          if (recentPoppedBySignature.size > 48) {
            val oldestKey = recentPoppedBySignature.keys.firstOrNull()
            if (oldestKey != null) recentPoppedBySignature.remove(oldestKey)
          }
        }
        _bannerNotification.value = item
        _bannerTriggerId.update { it + 1L }
      }
    }
    return shouldPopBanner
  }

  /**
   * Hides only the currently active floating/Home Dynamic Pop-Up banner for `key`,
   * preserving the notification inside Lock Screen & Android's notification shade.
   */
  fun clearBanner(key: String) {
    synchronized(syncLock) {
      if (_bannerNotification.value?.key == key) {
        _bannerNotification.value = null
      }
    }
  }

  fun remove(key: String) {
    synchronized(syncLock) {
      lastPostTimeByKey.remove(key)
      lastSigByKey.remove(key)
      if (_bannerNotification.value?.key == key) {
        _bannerNotification.value = null
      }
      _notifications.update { current -> current.filterNot { it.key == key } }
    }
  }

  fun resetForTesting() {
    synchronized(syncLock) {
      lastPostTimeByKey.clear()
      lastSigByKey.clear()
      recentPoppedBySignature.clear()
      _notifications.value = emptyList()
      _bannerNotification.value = null
      _bannerTriggerId.value = 0L
    }
  }

  private fun contentSignature(item: DynamicNotificationItem): String =
    "${item.packageName}|${item.title.trim().lowercase()}|${item.text.trim().lowercase()}"

  fun triggerPreviewNotification(title: String, body: String, appName: String) {
    upsert(
      DynamicNotificationItem(
        key = "preview_${System.currentTimeMillis()}",
        packageName = "com.aistudio.slauncher6",
        appName = appName,
        title = title,
        text = body,
        postTime = System.currentTimeMillis(),
        contentIntent = null,
        isPreview = true,
      )
    )
  }

  suspend fun loadPackageIcon(
    context: Context,
    packageName: String,
    iconSizePx: Int = 96,
  ): ImageBitmap? {
    if (packageName.isBlank()) return null
    packageIconCache.get(packageName)?.let { return it }

    return withContext(Dispatchers.IO) {
      packageIconCache.get(packageName)?.let { return@withContext it }
      try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val bitmap = drawableToScaledBitmap(drawable, iconSizePx)
        val imageBitmap = bitmap.asImageBitmap()
        packageIconCache.put(packageName, imageBitmap)
        imageBitmap
      } catch (_: Exception) {
        null
      } catch (_: OutOfMemoryError) {
        packageIconCache.evictAll()
        null
      }
    }
  }

  private fun drawableToScaledBitmap(drawable: Drawable, targetSizePx: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
      val src = drawable.bitmap
      if (src.width <= targetSizePx && src.height <= targetSizePx) {
        return src
      }
      return Bitmap.createScaledBitmap(src, targetSizePx, targetSizePx, true)
    }
    val bitmap = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, targetSizePx, targetSizePx)
    drawable.draw(canvas)
    return bitmap
  }

  fun isNotificationAccessGranted(context: Context): Boolean {
    val flat =
      Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        .orEmpty()
    if (flat.isBlank()) return false
    val expected = ComponentName(context, SLauncherNotificationListener::class.java)
    return flat.split(":").any { item ->
      val parsed = ComponentName.unflattenFromString(item)
      parsed != null &&
        TextUtils.equals(parsed.packageName, expected.packageName) &&
        TextUtils.equals(parsed.className, expected.className)
    }
  }

  fun hasNotificationPolicyAccess(context: Context): Boolean {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      ?: return false
    return nm.isNotificationPolicyAccessGranted
  }

  fun openNotificationAccessSettings(context: Context) {
    try {
      val intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
    } catch (_: Exception) {
      // Fallback safely if settings screen is unavailable
    }
  }

  fun openNotificationPolicyAccessSettings(context: Context) {
    try {
      val intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
    } catch (_: Exception) {
      openNotificationAccessSettings(context)
    }
  }

  /**
   * Native non-ADB fallback to suppress Android's stock floating heads-up (peek) banner
   * while still delivering calls, alarms, messages, and notifications to `SLauncherNotificationListener`.
   */
  fun applyHeadsUpPeekPolicy(context: Context, suppress: Boolean): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      ?: return false
    if (!nm.isNotificationPolicyAccessGranted) return false
    return runCatching {
        if (suppress) {
          val categories =
            NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
              NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES or
              NotificationManager.Policy.PRIORITY_CATEGORY_EVENTS or
              NotificationManager.Policy.PRIORITY_CATEGORY_REMINDERS or
              NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
              NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA or
              NotificationManager.Policy.PRIORITY_CATEGORY_SYSTEM
          nm.notificationPolicy =
            NotificationManager.Policy(
              categories,
              NotificationManager.Policy.PRIORITY_SENDERS_ANY,
              NotificationManager.Policy.PRIORITY_SENDERS_ANY,
              NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK,
            )
          nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        } else {
          nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        true
      }
      .getOrDefault(false)
  }
}
