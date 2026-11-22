package com.example.notifications

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.AppRepository

/**
 * System-bound NotificationListenerService that receives live notifications
 * only when posted/removed by Android OS without running any polling loops.
 *
 * Filters out group summaries, ongoing foreground services, media transport controls,
 * and progress bars so Dynamic Pop-Up banners never double-trigger or spam.
 */
class SLauncherNotificationListener : NotificationListenerService() {

  private var floatingOverlayController: FloatingDynamicOverlayController? = null

  override fun onCreate() {
    super.onCreate()
    floatingOverlayController =
      FloatingDynamicOverlayController(applicationContext) { key ->
        dismissSystemNotification(key)
      }
  }

  override fun onListenerConnected() {
    super.onListenerConnected()
    floatingOverlayController?.start()
    try {
      val active = activeNotifications?.mapNotNull { toDynamicItem(it) }.orEmpty()
      DynamicNotificationStore.setAll(active)
    } catch (_: Exception) {
      // Ignore transient binder exceptions on connect
    }
  }

  override fun onListenerDisconnected() {
    super.onListenerDisconnected()
    floatingOverlayController?.destroy()
  }

  override fun onDestroy() {
    floatingOverlayController?.destroy()
    floatingOverlayController = null
    super.onDestroy()
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn == null) return
    if (sbn.packageName == packageName) return
    val item = toDynamicItem(sbn) ?: return
    val popped = DynamicNotificationStore.upsertWithTime(item, System.currentTimeMillis())

    if (popped && shouldMarkShownToSuppressHeadsUp()) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching { setNotificationsShown(arrayOf(sbn.key)) }
      }
    }
  }

  override fun onNotificationRemoved(sbn: StatusBarNotification?) {
    if (sbn == null) return
    DynamicNotificationStore.remove(sbn.key)
  }

  fun dismissSystemNotification(key: String) {
    try {
      cancelNotification(key)
    } catch (_: Exception) {
      DynamicNotificationStore.remove(key)
    }
  }

  private fun shouldMarkShownToSuppressHeadsUp(): Boolean {
    if (!FloatingDynamicOverlayController.canDrawOverlays(applicationContext)) return false
    val prefs = applicationContext.getSharedPreferences(AppRepository.PREFS_NAME, Context.MODE_PRIVATE)
    val dynamicEnabled = prefs.getBoolean(AppRepository.KEY_DYNAMIC_NOTIF_ENABLED, true)
    val floatingEnabled = prefs.getBoolean(AppRepository.KEY_FLOATING_OVERLAY_POPUP_ENABLED, true)
    val suppressHeadsUp = prefs.getBoolean(AppRepository.KEY_SUPPRESS_SYSTEM_HEADS_UP, false)
    return dynamicEnabled && floatingEnabled && suppressHeadsUp
  }

  private fun toDynamicItem(sbn: StatusBarNotification): DynamicNotificationItem? {
    val notification = sbn.notification ?: return null
    val flags = notification.flags
    if ((flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null
    if ((flags and Notification.FLAG_ONGOING_EVENT) != 0) return null
    if ((flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return null

    when (notification.category) {
      Notification.CATEGORY_TRANSPORT,
      Notification.CATEGORY_PROGRESS,
      Notification.CATEGORY_SERVICE,
      Notification.CATEGORY_STATUS -> return null
    }

    val extras = notification.extras ?: return null
    if (
      extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) ||
        extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0
    ) {
      return null
    }

    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
    val text =
      (extras.getCharSequence(Notification.EXTRA_TEXT)
          ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        ?.toString()
        ?.trim()
        .orEmpty()

    if (title.isEmpty() && text.isEmpty()) return null

    val appLabel =
      try {
        val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
      } catch (_: Exception) {
        sbn.packageName
      }

    return DynamicNotificationItem(
      key = sbn.key,
      packageName = sbn.packageName,
      appName = appLabel,
      title = title.ifEmpty { appLabel },
      text = text,
      postTime = sbn.postTime,
      contentIntent = notification.contentIntent,
      isPreview = false,
    )
  }
}
