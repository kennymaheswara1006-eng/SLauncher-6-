package com.example.quicksettings

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.DrawableRes
import com.example.LockScreenActivity
import com.example.MainActivity
import com.example.R
import com.example.data.AppRepository

/**
 * Integrates directly with Android's built-in System Quick Settings panel:
 * - Expands the native System Quick Settings shade via EXPAND_STATUS_BAR
 * - Requests pinning custom SLauncher 6 tiles into System Quick Settings on Android 13+
 */
object SystemQuickSettingsHelper {

  const val EXTRA_OPEN_SETTINGS = "extra_open_settings"

  /**
   * Pulls down Android's actual built-in System Quick Settings panel.
   * Requires normal permission android.permission.EXPAND_STATUS_BAR.
   */
  @SuppressLint("WrongConstant", "PrivateApi")
  fun expandSystemQuickSettings(context: Context): Boolean {
    return runCatching {
      val statusBarService = context.getSystemService("statusbar") ?: return false
      val statusBarClass = Class.forName("android.app.StatusBarManager")
      val method =
        runCatching { statusBarClass.getMethod("expandSettingsPanel") }.getOrNull()
          ?: statusBarClass.getMethod("expandNotificationsPanel")
      method.invoke(statusBarService)
      true
    }.getOrDefault(false)
  }

  /**
   * Opens the Android 13+ native system prompt allowing one-tap placement of an SLauncher 6
   * tile directly into the phone's built-in Quick Settings panel.
   * Returns false on Android 12 and below (caller opens/expands System Quick Settings instead).
   */
  fun requestAddTile(
    context: Context,
    serviceClass: Class<out TileService>,
    label: String,
    @DrawableRes iconRes: Int,
    onResult: (Int) -> Unit,
  ): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      expandSystemQuickSettings(context)
      return false
    }
    val statusBarManager = context.getSystemService(StatusBarManager::class.java) ?: return false
    statusBarManager.requestAddTileService(
      ComponentName(context, serviceClass),
      label,
      Icon.createWithResource(context, iconRes),
      context.mainExecutor,
    ) { resultCode ->
      onResult(resultCode)
    }
    return true
  }
}

private fun TileService.launchActivityCollapsed(intent: Intent) {
  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    val pendingIntent =
      PendingIntent.getActivity(
        this,
        intent.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    startActivityAndCollapse(pendingIntent)
  } else {
    @Suppress("DEPRECATION")
    startActivityAndCollapse(intent)
  }
}

/**
 * Native System Quick Settings tile to lock screen via SLauncher 6's native Lock Screen.
 */
class LockScreenTileService : TileService() {

  override fun onStartListening() {
    super.onStartListening()
    val tile = qsTile ?: return
    val prefs = getSharedPreferences(AppRepository.PREFS_NAME, Context.MODE_PRIVATE)
    val enabled = prefs.getBoolean(AppRepository.KEY_LOCKSCREEN_ENABLED, false)
    tile.label = getString(R.string.qs_tile_lock_label)
    tile.contentDescription = tile.label
    tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_lock)
    tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      tile.subtitle =
        getString(if (enabled) R.string.qs_on else R.string.qs_tile_tap_lock)
    }
    tile.updateTile()
  }

  override fun onClick() {
    super.onClick()
    val lockIntent =
      Intent(this, LockScreenActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
      }
    launchActivityCollapsed(lockIntent)
  }
}

/**
 * Toggleable System Quick Settings tile for SLauncher 6's post-unlock Welcome Pop-Up.
 */
class WelcomePopupTileService : TileService() {

  override fun onStartListening() {
    super.onStartListening()
    syncTileState()
  }

  override fun onClick() {
    super.onClick()
    val prefs = getSharedPreferences(AppRepository.PREFS_NAME, Context.MODE_PRIVATE)
    val current = prefs.getBoolean(AppRepository.KEY_WELCOME_ENABLED, true)
    prefs.edit().putBoolean(AppRepository.KEY_WELCOME_ENABLED, !current).apply()
    syncTileState()
  }

  private fun syncTileState() {
    val tile = qsTile ?: return
    val prefs = getSharedPreferences(AppRepository.PREFS_NAME, Context.MODE_PRIVATE)
    val enabled = prefs.getBoolean(AppRepository.KEY_WELCOME_ENABLED, true)
    tile.label = getString(R.string.qs_tile_welcome_label)
    tile.contentDescription = tile.label
    tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_welcome)
    tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      tile.subtitle = getString(if (enabled) R.string.qs_on else R.string.qs_off)
    }
    tile.updateTile()
  }
}

/**
 * Toggleable System Quick Settings tile for SLauncher 6's Dynamic Notification capsule.
 */
class DynamicAlertsTileService : TileService() {

  override fun onStartListening() {
    super.onStartListening()
    syncTileState()
  }

  override fun onClick() {
    super.onClick()
    val prefs = getSharedPreferences(AppRepository.PREFS_NAME, Context.MODE_PRIVATE)
    val current = prefs.getBoolean(AppRepository.KEY_DYNAMIC_NOTIF_ENABLED, true)
    prefs.edit().putBoolean(AppRepository.KEY_DYNAMIC_NOTIF_ENABLED, !current).apply()
    syncTileState()
  }

  private fun syncTileState() {
    val tile = qsTile ?: return
    val prefs = getSharedPreferences(AppRepository.PREFS_NAME, Context.MODE_PRIVATE)
    val enabled = prefs.getBoolean(AppRepository.KEY_DYNAMIC_NOTIF_ENABLED, true)
    tile.label = getString(R.string.qs_tile_dynamic_label)
    tile.contentDescription = tile.label
    tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_dynamic)
    tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      tile.subtitle = getString(if (enabled) R.string.qs_on else R.string.qs_off)
    }
    tile.updateTile()
  }
}

/**
 * Native System Quick Settings tile to jump straight to SLauncher 6 Settings.
 */
class LauncherSettingsTileService : TileService() {

  override fun onStartListening() {
    super.onStartListening()
    val tile = qsTile ?: return
    tile.label = getString(R.string.qs_tile_settings_label)
    tile.contentDescription = tile.label
    tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_slauncher)
    tile.state = Tile.STATE_INACTIVE
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      tile.subtitle = getString(R.string.app_name)
    }
    tile.updateTile()
  }

  override fun onClick() {
    super.onClick()
    val settingsIntent =
      Intent(this, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(SystemQuickSettingsHelper.EXTRA_OPEN_SETTINGS, true)
      }
    launchActivityCollapsed(settingsIntent)
  }
}
