package com.example.adb

import android.app.KeyguardManager
import android.app.NotificationManager
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
import androidx.biometric.BiometricManager
import com.example.data.QuickSettingsState
import com.example.debug.DebugLogStore
import com.example.debug.SystemDiagnosticsSnapshot
import com.example.notifications.DynamicNotificationStore
import com.example.notifications.FloatingDynamicOverlayController
import com.example.notifications.SLauncherNotificationListener
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordinates real hardware and OS control for the custom Quick Settings Control Center:
 * - Native Android APIs for Flashlight (CameraManager), Media Volume (AudioManager),
 *   and Notification Policy Peek Suppression (NotificationManager.Policy)
 * - Built-in Self-Contained USB & Wireless ADB (`LocalAdbBridge`) for Wi-Fi, Mobile Data,
 *   Bluetooth, Dark Mode, Auto-Rotate, Screen Brightness, Heads-Up control, and Real Screen Lock
 * - Safe Android Settings Panel fallbacks when built-in ADB is not connected
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
    LocalAdbBridge.init(appContext)
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
      val adbState = LocalAdbBridge.refreshStatus()
      val wifiEnabled = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false)
      val bluetoothEnabled =
        runCatching {
            val btMgr =
              appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            btMgr?.adapter?.isEnabled == true
          }
          .getOrDefault(false)

      val mobileDataEnabled =
        if (adbState == AdbConnectionState.CONNECTED) {
          LocalAdbBridge.queryMobileDataEnabled() ?: readNativeMobileData()
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
      val headsUpSuppressed = isSystemHeadsUpEffectivelySuppressed()

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
        adbState = adbState,
        headsUpSuppressed = headsUpSuppressed,
      )
    }

  private fun isSystemHeadsUpEffectivelySuppressed(): Boolean {
    val globalZero =
      runCatching {
          Settings.Global.getInt(appContext.contentResolver, "heads_up_notifications_enabled", 1) == 0
        }
        .getOrDefault(false)
    if (globalZero) return true

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      if (nm != null && nm.isNotificationPolicyAccessGranted) {
        val peekSuppressed =
          (nm.notificationPolicy.suppressedVisualEffects and NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK) != 0
        if (peekSuppressed && nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
          return true
        }
      }
    }
    return false
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
    if (LocalAdbBridge.isConnected() && LocalAdbBridge.setWifiEnabled(target)) {
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
    if (LocalAdbBridge.isConnected() && LocalAdbBridge.setMobileDataEnabled(target)) {
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
    if (LocalAdbBridge.isConnected() && LocalAdbBridge.setBluetoothEnabled(target)) {
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
    if (LocalAdbBridge.isConnected() && LocalAdbBridge.setDarkModeEnabled(target)) {
      return true
    }
    openSettingsFallback(Settings.ACTION_DISPLAY_SETTINGS)
    return false
  }

  suspend fun toggleAutoRotate(currentlyEnabled: Boolean): Boolean {
    val target = !currentlyEnabled
    if (LocalAdbBridge.isConnected() && LocalAdbBridge.setAutoRotateEnabled(target)) {
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
    if (LocalAdbBridge.isConnected() && LocalAdbBridge.setScreenBrightness(clamped)) {
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

  /**
   * Suppresses stock Android heads-up peek notifications so notifications never appear doubled
   * alongside SLauncher 6's Floating Dynamic Capsule:
   * 1) Updates `heads_up_notifications_enabled` via built-in ADB when connected.
   * 2) Also applies Android's native `NotificationManager.Policy(SUPPRESSED_EFFECT_PEEK)` when
   *    Notification Policy Access (`ACCESS_NOTIFICATION_POLICY`) is granted (works without ADB!).
   */
  suspend fun setHeadsUpSuppressed(suppress: Boolean): Boolean {
    val adbOk =
      if (LocalAdbBridge.isConnected()) {
        LocalAdbBridge.setHeadsUpNotificationsSuppressed(suppress)
      } else {
        false
      }
    val policyOk = DynamicNotificationStore.applyHeadsUpPeekPolicy(appContext, suppress)
    return adbOk || policyOk
  }

  suspend fun autoGrantPrivilegedPermissionsViaAdb(): Boolean {
    if (!LocalAdbBridge.isConnected()) return false
    val listenerComponent =
      ComponentName(appContext, SLauncherNotificationListener::class.java).flattenToString()
    return LocalAdbBridge.grantOverlayAndNotificationPermissions(
      packageName = appContext.packageName,
      listenerComponentFlattened = listenerComponent,
    )
  }

  suspend fun lockScreenViaAdb(): Boolean {
    if (!LocalAdbBridge.isConnected()) return false
    return LocalAdbBridge.lockDeviceScreen()
  }

  suspend fun collectSystemDiagnostics(isDefaultHome: Boolean): SystemDiagnosticsSnapshot =
    withContext(Dispatchers.IO) {
      val env = AdbDiscoveryHelper.readEnvironmentStatus(appContext)
      val adbState = LocalAdbBridge.refreshStatus()
      val isConnected = adbState == AdbConnectionState.CONNECTED

      val uidRes =
        if (isConnected) LocalAdbBridge.probeUid()
        else AdbCommandResult(false, -1, "ADB not connected")

      val lockscreenDisabledSetting =
        if (isConnected) LocalAdbBridge.querySystemLockscreenDisabledSetting()
        else {
          runCatching {
              Settings.Secure.getInt(appContext.contentResolver, "lockscreen.disabled", 0)
                .toString()
            }
            .getOrDefault("unknown")
        }

      val keyguardManager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
      val keyguardLocked = keyguardManager?.isKeyguardLocked == true
      val keyguardSecure = keyguardManager?.isDeviceSecure == true

      val bioCode =
        runCatching {
            BiometricManager.from(appContext)
              .canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                  BiometricManager.Authenticators.BIOMETRIC_WEAK
              )
          }
          .getOrDefault(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)
      val bioLabel =
        when (bioCode) {
          BiometricManager.BIOMETRIC_SUCCESS -> "READY"
          BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "NOT_ENROLLED"
          BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "NO_HARDWARE"
          BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "HW_UNAVAILABLE"
          else -> "CODE_$bioCode"
        }

      val snapshot =
        SystemDiagnosticsSnapshot(
          developerOptionsEnabled = env.developerOptionsEnabled,
          usbDebuggingEnabled = env.usbDebuggingEnabled,
          wirelessDebuggingEnabled = env.wirelessDebuggingEnabled,
          adbConnected = isConnected,
          adbEndpoint = "${LocalAdbBridge.targetHost.value}:${LocalAdbBridge.targetPort.value}",
          localIpAddress = env.localIpv4,
          usbOtgAdbCount = env.usbOtgAdbDevices.size,
          adbShellUidOutput = uidRes.output.ifBlank { "not connected" },
          isDefaultHome = isDefaultHome,
          hasOverlayPermission = FloatingDynamicOverlayController.canDrawOverlays(appContext),
          hasNotificationAccess = DynamicNotificationStore.isNotificationAccessGranted(appContext),
          hasNotificationPolicyAccess =
            DynamicNotificationStore.hasNotificationPolicyAccess(appContext),
          systemHeadsUpSuppressed = isSystemHeadsUpEffectivelySuppressed(),
          biometricStatusLabel = bioLabel,
          systemKeyguardLocked = keyguardLocked,
          systemKeyguardSecure = keyguardSecure,
          systemLockscreenDisabledSetting = lockscreenDisabledSetting,
        )
      DebugLogStore.log(
        "Diagnosa",
        "ADB(${LocalAdbBridge.targetHost.value}:${LocalAdbBridge.targetPort.value}, conn=$isConnected, usb=${env.usbDebuggingEnabled}, wifi=${env.wirelessDebuggingEnabled}) HeadsUpOff=${snapshot.systemHeadsUpSuppressed} Bio=$bioLabel",
      )
      snapshot
    }

  suspend fun connectBuiltInAdb(host: String, port: Int): AdbCommandResult =
    LocalAdbBridge.connect(host, port)

  suspend fun autoConnectBuiltInAdb(): AdbCommandResult =
    LocalAdbBridge.autoConnectBestEndpoint()

  suspend fun runDebugShellCommand(command: String): AdbCommandResult =
    LocalAdbBridge.runDebugShellCommand(command)

  suspend fun attemptDisableSystemKeyguardViaAdb(): AdbCommandResult =
    LocalAdbBridge.attemptDisableSystemKeyguard()

  private fun openSettingsFallback(action: String) {
    runCatching {
      appContext.startActivity(
        Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
      )
    }
  }
}
