package com.example.notifications

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
 * Lightweight in-memory store fed directly by SLauncherNotificationListener callbacks
 * and device unlock signals. Uses zero background threads or polling.
 */
object DynamicNotificationStore {

  private val _notifications = MutableStateFlow<List<DynamicNotificationItem>>(emptyList())
  val notifications: StateFlow<List<DynamicNotificationItem>> = _notifications.asStateFlow()

  private val _bannerTriggerId = MutableStateFlow(0L)
  val bannerTriggerId: StateFlow<Long> = _bannerTriggerId.asStateFlow()

  private val _unlockEventCount = MutableStateFlow(0L)
  val unlockEventCount: StateFlow<Long> = _unlockEventCount.asStateFlow()

  private val pendingUnlockWelcome = AtomicBoolean(false)

  // Tiny 16-icon LruCache for notification source app icons (memory-safe on 2GB RAM)
  private val packageIconCache = LruCache<String, ImageBitmap>(16)

  fun markPhoneUnlocked() {
    pendingUnlockWelcome.set(true)
    _unlockEventCount.update { it + 1L }
  }

  fun consumePhoneUnlocked(): Boolean = pendingUnlockWelcome.getAndSet(false)

  fun setAll(items: List<DynamicNotificationItem>) {
    _notifications.value = items.sortedByDescending { it.postTime }.take(12)
  }

  fun upsert(item: DynamicNotificationItem) {
    _notifications.update { current ->
      val filtered = current.filterNot { it.key == item.key }
      (listOf(item) + filtered).sortedByDescending { it.postTime }.take(12)
    }
    _bannerTriggerId.update { it + 1L }
  }

  fun remove(key: String) {
    _notifications.update { current -> current.filterNot { it.key == key } }
  }

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
}
