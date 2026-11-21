package com.example

import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.example.data.AppRepository
import com.example.debug.DebugLogStore
import com.example.notifications.DynamicNotificationStore

/**
 * Lightweight Application class that listens to system screen/unlock broadcasts
 * (SCREEN_OFF, USER_PRESENT, SCREEN_ON) to launch SLauncher 6's native Lock Screen
 * when enabled and trigger the Hello/Welcome pop-up once the user unlocks into the phone.
 * Zero background services are started.
 */
class SLauncherApp : Application() {

  private var lockScreenEnabled: Boolean = false
  private var screenWentOff: Boolean = false
  private var screenLifecycleReceiver: BroadcastReceiver? = null

  override fun onCreate() {
    super.onCreate()
    syncLockScreenReceiver()
    ensureScreenLifecycleReceiverRegistered()
  }

  fun syncLockScreenReceiver() {
    val prefs = getSharedPreferences(AppRepository.PREFS_NAME, Context.MODE_PRIVATE)
    lockScreenEnabled = prefs.getBoolean(AppRepository.KEY_LOCKSCREEN_ENABLED, false)
    ensureScreenLifecycleReceiverRegistered()
  }

  private fun launchNativeLockScreen(reason: String) {
    val lockIntent =
      Intent(this@SLauncherApp, LockScreenActivity::class.java).apply {
        addFlags(
          Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_NO_ANIMATION
        )
      }
    runCatching { startActivity(lockIntent) }
      .onSuccess {
        DebugLogStore.log("LockScreen", "Launched native LockScreenActivity ($reason)")
      }
      .onFailure { err ->
        DebugLogStore.log("LockScreen", "Failed launch ($reason): ${err.message}", isError = true)
      }
  }

  private fun ensureScreenLifecycleReceiverRegistered() {
    if (screenLifecycleReceiver != null) return
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
          when (intent?.action) {
            Intent.ACTION_SCREEN_OFF -> {
              screenWentOff = true
              if (lockScreenEnabled) {
                DebugLogStore.log("LockScreen", "Screen off detected; LockScreen armed for wake")
              }
            }

            Intent.ACTION_USER_PRESENT -> {
              screenWentOff = false
              DynamicNotificationStore.markPhoneUnlocked()
            }

            Intent.ACTION_SCREEN_ON -> {
              val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
              val isKeyguardLocked = keyguard?.isKeyguardLocked == true
              if (screenWentOff && lockScreenEnabled) {
                screenWentOff = false
                if (!LockScreenActivity.isActiveInstance) {
                  launchNativeLockScreen("SCREEN_ON")
                }
              } else if (screenWentOff && !isKeyguardLocked && !lockScreenEnabled) {
                screenWentOff = false
                DynamicNotificationStore.markPhoneUnlocked()
              }
            }
          }
        }
      }

    val filter =
      IntentFilter().apply {
        addAction(Intent.ACTION_SCREEN_OFF)
        addAction(Intent.ACTION_USER_PRESENT)
        addAction(Intent.ACTION_SCREEN_ON)
      }
    ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    screenLifecycleReceiver = receiver
  }

  companion object {
    /**
     * Tracks whether the Boot-Up Welcome animation already ran in the current OS process,
     * preventing replay when returning from other apps via the Home button.
     */
    @Volatile
    var hasShownBootWelcomeInProcess: Boolean = false
  }
}
