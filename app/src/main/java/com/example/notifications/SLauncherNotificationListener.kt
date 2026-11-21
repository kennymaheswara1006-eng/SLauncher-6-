package com.example.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * System-bound NotificationListenerService that receives live notifications
 * only when posted/removed by Android OS without running any polling loops.
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
    DynamicNotificationStore.upsert(item)
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

  private fun toDynamicItem(sbn: StatusBarNotification): DynamicNotificationItem? {
    val notification = sbn.notification ?: return null
    if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null

    val extras = notification.extras ?: return null
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
