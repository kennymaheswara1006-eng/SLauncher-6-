package com.example.shizuku

import android.app.UiModeManager
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import com.example.data.QuickSettingsState
import com.example.notifications.SLauncherNotificationListener
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordinates real hardware and OS control for the custom Quick Settings Control Center:
 * - Native Android APIs for Flashlight (CameraManager) & Media Volume (AudioManager)
 * - Shizuku (ADB UID 2000) commands for Wi-Fi, Mobile Data, Bluetooth, Dark Mode, Auto-Rotate,
 *   Screen Brightness, Stock Heads-Up control, and Real Screen Lock
 * - Safe Android Settings Panel fallbacks when Shizuku is not active
 */
class SystemControlRepository(context: Context) {

  private val appContext = context.applicationContext
  private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
  private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
  private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
  private val uiModeManager = appContext.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
  private val telephonyManager =
    appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

  @Volatile private var currentTorchOn = false
  private var torchCameraId: String? = null
  private var torchCallbackRegistered = false

  private val torchCallback =
    object : CameraManager.TorchCallback() {
      override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
        if (cameraId == torchCameraId || torchCameraId == null) {
          currentTorchOn = enabled
        }
      }
    }

  init {
    ShizukuBridge.init(appContext)
    registerTorchObserverIfAvailable()
  }

  private fun registerTorchObserverIfAvailable() {
    val cm = cameraManager ?: return
    if (torchCallbackRegistered) return
    runCatching {
      torchCameraId =
        cm.cameraIdList.firstOrNull { id ->
          val chars = cm.getCameraCharacteristics(id)
          val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
          val facingBack =
            chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
          hasFlash && facingBack
        }
          ?: cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
          }
      if (torchCameraId != null) {
        cm.registerTorchCallback(torchCallback, null)
        torchCallbackRegistered = true
      }
    }
  }

  suspend fun readQuickSettingsSnapshot(): QuickSettingsState =
    withContext(Dispatchers.IO) {
      val shizukuState = ShizukuBridge.refreshStatus()
      val wifiEnabled = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false)
      val bluetoothEnabled =
        runCatching {
            val btMgr =
              appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            btMgr?.adapter?.isEnabled == true
          }
          .getOrDefault(false)

      val mobileDataEnabled =
        if (shizukuState == ShizukuConnectionState.CONNECTED) {
          ShizukuBridge.queryMobileDataEnabled() ?: readNativeMobileData()
        } else {
          readNativeMobileData()
        }

      val darkModeEnabled =
        runCatching {
            val nightModeFlags =
              appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightModeFlags == Configuration.UI_MODE_NIGHT_YES ||
              uiModeManager?.nightMode == UiModeManager.MODE_NIGHT_YES
          }
          .getOrDefault(false)

      val autoRotateEnabled =
        runCatching {
            Settings.System.getInt(
              appContext.contentResolver,
              Settings.System.ACCELEROMETER_ROTATION,
              0,
            ) == 1
          }
          .getOrDefault(false)

      val brightness =
        runCatching {
            Settings.System.getInt(
              appContext.contentResolver,
              Settings.System.SCREEN_BRIGHTNESS,
              140,
            )
          }
          .getOrDefault(140)
          .coerceIn(10, 255)

      val mediaVolPercent = readMediaVolumePercent()
      val headsUpSuppressed =
        if (shizukuState == ShizukuConnectionState.CONNECTED) {
          ShizukuBridge.isHeadsUpNotificationsSuppressed()
        } else {
          runCatching {
              Settings.Global.getInt(
                appContext.contentResolver,
                "heads_up_notifications_enabled",
                1,
              ) == 0
            }
            .getOrDefault(false)
        }

      QuickSettingsState(
        wifiEnabled = wifiEnabled,
        mobileDataEnabled = mobileDataEnabled,
        bluetoothEnabled = bluetoothEnabled,
        flashlightEnabled = currentTorchOn,
        flashlightAvailable = torchCameraId != null,
        darkModeEnabled = darkModeEnabled,
        autoRotateEnabled = autoRotateEnabled,
        brightnessLevel = brightness,
        mediaVolumePercent = mediaVolPercent,
        shizukuState = shizukuState,
        headsUpSuppressed = headsUpSuppressed,
      )
    }

  private fun readNativeMobileData(): Boolean {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          @Suppress("MissingPermission")
          telephonyManager?.isDataEnabled == true
        } else {
          Settings.Global.getInt(appContext.contentResolver, "mobile_data", 0) == 1
        }
      }
      .getOrDefault(false)
  }

  private fun readMediaVolumePercent(): Int {
    val am = audioManager ?: return 60
    return runCatching {
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        ((current.toFloat() / max.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
      }
      .getOrDefault(60)
  }

  suspend fun toggleWifi(currentlyEnabled: Boolean): Boolean {
    val target = !currentlyEnabled
    if (ShizukuBridge.isConnected() && ShizukuBridge.setWifiEnabled(target)) {
      return true
    }
    openSettingsFallback(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_WIFI
      else Settings.ACTION_WIFI_SETTINGS
    )
    return false
  }

  suspend fun toggleMobileData(currentlyEnabled: Boolean): Boolean {
    val target = !currentlyEnabled
    if (ShizukuBridge.isConnected() && ShizukuBridge.setMobileDataEnabled(target)) {
      return true
    }
    openSettingsFallback(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_INTERNET_CONNECTIVITY
      else Settings.ACTION_WIRELESS_SETTINGS
    )
    return false
  }

  suspend fun toggleBluetooth(currentlyEnabled: Boolean): Boolean {
    val target = !currentlyEnabled
    if (ShizukuBridge.isConnected() && ShizukuBridge.setBluetoothEnabled(target)) {
      return true
    }
    openSettingsFallback(Settings.ACTION_BLUETOOTH_SETTINGS)
    return false
  }

  fun toggleFlashlight(): Boolean {
    val cm = cameraManager ?: return false
    val camId = torchCameraId ?: return false
    return runCatching {
        val next = !currentTorchOn
        cm.setTorchMode(camId, next)
        currentTorchOn = next
        true
      }
      .getOrDefault(false)
  }

  suspend fun toggleDarkMode(currentlyEnabled: Boolean): Boolean {
    val target = !currentlyEnabled
    if (ShizukuBridge.isConnected() && ShizukuBridge.setDarkModeEnabled(target)) {
      return true
    }
    openSettingsFallback(Settings.ACTION_DISPLAY_SETTINGS)
    return false
  }

  suspend fun toggleAutoRotate(currentlyEnabled: Boolean): Boolean {
    val target = !currentlyEnabled
    if (ShizukuBridge.isConnected() && ShizukuBridge.setAutoRotateEnabled(target)) {
      return true
    }
    if (Settings.System.canWrite(appContext)) {
      return runCatching {
          Settings.System.putInt(
            appContext.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            if (target) 1 else 0,
          )
          true
        }
        .getOrDefault(false)
    }
    openSettingsFallback(Settings.ACTION_DISPLAY_SETTINGS)
    return false
  }

  suspend fun setBrightness(level0To255: Int): Boolean {
    val clamped = level0To255.coerceIn(10, 255)
    if (ShizukuBridge.isConnected() && ShizukuBridge.setScreenBrightness(clamped)) {
      return true
    }
    if (Settings.System.canWrite(appContext)) {
      return runCatching {
          Settings.System.putInt(
            appContext.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
          )
          Settings.System.putInt(
            appContext.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            clamped,
          )
          true
        }
        .getOrDefault(false)
    }
    return false
  }

  fun setMediaVolumePercent(percent: Int): Int {
    val clamped = percent.coerceIn(0, 100)
    val am = audioManager ?: return clamped
    runCatching {
      val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
      val index = ((clamped / 100f) * max).roundToInt().coerceIn(0, max)
      am.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
    }
    return readMediaVolumePercent()
  }

  suspend fun setHeadsUpSuppressed(suppress: Boolean): Boolean {
    if (!ShizukuBridge.isConnected()) return false
    return ShizukuBridge.setHeadsUpNotificationsSuppressed(suppress)
  }

  suspend fun autoGrantPrivilegedPermissionsViaShizuku(): Boolean {
    if (!ShizukuBridge.isConnected()) return false
    val listenerComponent =
      ComponentName(appContext, SLauncherNotificationListener::class.java).flattenToString()
    return ShizukuBridge.grantOverlayAndNotificationPermissions(
      packageName = appContext.packageName,
      listenerComponentFlattened = listenerComponent,
    )
  }

  suspend fun lockScreenViaShizuku(): Boolean {
    if (!ShizukuBridge.isConnected()) return false
    return ShizukuBridge.lockDeviceScreen()
  }

  private fun openSettingsFallback(action: String) {
    runCatching {
      appContext.startActivity(
        Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
      )
    }
  }
}
