package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppRepository

/**
 * Listens for device boot completion (`ACTION_BOOT_COMPLETED`) without root or background services.
 * Flags the pending Boot-Up Welcome animation in SharedPreferences so SLauncher 6 plays the
 * Minimalist Futuristic Pulse sequence cleanly when opened after restart.
 */
class BootReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    if (action != Intent.ACTION_BOOT_COMPLETED && action != ACTION_QUICKBOOT_POWERON) {
      return
    }

    val repository = AppRepository(context.applicationContext)
    if (!repository.isBootWelcomeEnabled()) return

    SLauncherApp.hasShownBootWelcomeInProcess = false
    repository.markPendingBootWelcome()

    // If SLauncher 6 is the default Home launcher, safely request foreground entry if allowed by OS
    if (repository.isDefaultHomeLauncher()) {
      val launchIntent =
        Intent(context.applicationContext, MainActivity::class.java).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
          putExtra(EXTRA_FROM_BOOT, true)
        }
      runCatching { context.applicationContext.startActivity(launchIntent) }
    }
  }

  companion object {
    private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    const val EXTRA_FROM_BOOT = "extra_from_boot"
  }
}
