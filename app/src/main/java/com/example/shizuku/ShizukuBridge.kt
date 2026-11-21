package com.example.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import com.example.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

enum class ShizukuConnectionState {
  NOT_INSTALLED,
  NOT_RUNNING,
  NEEDS_PERMISSION,
  CONNECTING,
  CONNECTED,
}

data class ShizukuCommandResult(
  val success: Boolean,
  val exitCode: Int,
  val output: String,
)

/**
 * Lifecycle-safe coordinator for Shizuku (API v13+) connection, permission requests,
 * UserService binding, and non-root ADB-level system actions.
 */
object ShizukuBridge {

  private const val SHIZUKU_MANAGER_PKG = "moe.shizuku.manager"
  private const val REQUEST_CODE_SHIZUKU = 6021

  private val initialized = AtomicBoolean(false)
  private val commandMutex = Mutex()

  private var appContext: Context? = null
  @Volatile private var shellService: IShizukuShellService? = null
  @Volatile private var isBinding = false

  private val _connectionState = MutableStateFlow(ShizukuConnectionState.NOT_RUNNING)
  val connectionState: StateFlow<ShizukuConnectionState> = _connectionState.asStateFlow()

  private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refreshStatus() }
  private val binderDeadListener = Shizuku.OnBinderDeadListener {
    shellService = null
    isBinding = false
    refreshStatus()
  }
  private val permissionResultListener =
    Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
      if (requestCode == REQUEST_CODE_SHIZUKU) {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
          bindShellUserService()
        }
        refreshStatus()
      }
    }

  private val serviceConnection =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        shellService = IShizukuShellService.Stub.asInterface(service)
        isBinding = false
        refreshStatus()
      }

      override fun onServiceDisconnected(name: ComponentName?) {
        shellService = null
        isBinding = false
        refreshStatus()
      }
    }

  fun init(context: Context) {
    appContext = context.applicationContext
    if (initialized.compareAndSet(false, true)) {
      runCatching {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
      }
    }
    refreshStatus()
  }

  fun refreshStatus(): ShizukuConnectionState {
    val ctx = appContext
    val next =
      when {
        ctx != null && !isShizukuManagerInstalled(ctx) -> ShizukuConnectionState.NOT_INSTALLED
        !safePingBinder() -> ShizukuConnectionState.NOT_RUNNING
        !safeHasPermission() -> ShizukuConnectionState.NEEDS_PERMISSION
        shellService != null && shellService?.asBinder()?.isBinderAlive == true ->
          ShizukuConnectionState.CONNECTED
        else -> {
          bindShellUserService()
          if (shellService != null) ShizukuConnectionState.CONNECTED
          else ShizukuConnectionState.CONNECTING
        }
      }
    _connectionState.value = next
    return next
  }

  fun isConnected(): Boolean =
    refreshStatus() == ShizukuConnectionState.CONNECTED && shellService != null

  fun requestPermissionOrOpenManager(context: Context) {
    init(context)
    if (!isShizukuManagerInstalled(context)) {
      openShizukuManager(context)
      return
    }
    if (!safePingBinder()) {
      openShizukuManager(context)
      return
    }
    if (!safeHasPermission()) {
      runCatching {
        if (!Shizuku.shouldShowRequestPermissionRationale()) {
          Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
        } else {
          Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
        }
      }.onFailure { openShizukuManager(context) }
    } else {
      bindShellUserService()
    }
    refreshStatus()
  }

  fun openShizukuManager(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_MANAGER_PKG)
    if (launch != null) {
      launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      runCatching { context.startActivity(launch) }
    } else {
      runCatching {
        val marketIntent =
          Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("https://shizuku.rikka.app/")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        context.startActivity(marketIntent)
      }
    }
  }

  private fun bindShellUserService() {
    val ctx = appContext ?: return
    if (shellService?.asBinder()?.isBinderAlive == true || isBinding) return
    if (!safePingBinder() || !safeHasPermission()) return

    isBinding = true
    runCatching {
      val args =
        Shizuku.UserServiceArgs(
            ComponentName(ctx.packageName, ShizukuShellUserService::class.java.name)
          )
          .daemon(false)
          .processNameSuffix("shizuku_shell")
          .tag("slauncher6_shell_service")
          .debuggable(BuildConfig.DEBUG)
          .version(BuildConfig.VERSION_CODE)
      Shizuku.bindUserService(args, serviceConnection)
    }.onFailure {
      isBinding = false
    }
  }

  private suspend fun runShell(command: String): ShizukuCommandResult =
    withContext(Dispatchers.IO) {
      commandMutex.withLock {
        val service = shellService
        if (service == null || !service.asBinder().isBinderAlive) {
          bindShellUserService()
          return@withLock ShizukuCommandResult(false, -1, "Shizuku service not connected")
        }
        try {
          val raw = service.execCommand(command).orEmpty()
          val newlineIdx = raw.indexOf('\n')
          val header = if (newlineIdx >= 0) raw.substring(0, newlineIdx) else raw
          val body = if (newlineIdx >= 0) raw.substring(newlineIdx + 1) else ""
          val exitCode = header.removePrefix("EXIT:").trim().toIntOrNull() ?: 1
          ShizukuCommandResult(success = exitCode == 0, exitCode = exitCode, output = body.trim())
        } catch (t: Throwable) {
          shellService = null
          refreshStatus()
          ShizukuCommandResult(false, -1, t.message.orEmpty())
        }
      }
    }

  suspend fun setWifiEnabled(enabled: Boolean): Boolean =
    runShell(if (enabled) "svc wifi enable" else "svc wifi disable").success

  suspend fun setMobileDataEnabled(enabled: Boolean): Boolean =
    runShell(if (enabled) "svc data enable" else "svc data disable").success

  suspend fun setBluetoothEnabled(enabled: Boolean): Boolean =
    runShell(
      if (enabled) "cmd bluetooth_manager enable" else "cmd bluetooth_manager disable"
    ).success

  suspend fun setDarkModeEnabled(enabled: Boolean): Boolean =
    runShell(if (enabled) "cmd uimode night yes" else "cmd uimode night no").success

  suspend fun setAutoRotateEnabled(enabled: Boolean): Boolean =
    runShell(
      "settings put system accelerometer_rotation ${if (enabled) 1 else 0}"
    ).success

  suspend fun setScreenBrightness(level0To255: Int): Boolean {
    val clamped = level0To255.coerceIn(10, 255)
    return runShell(
      "settings put system screen_brightness_mode 0 && settings put system screen_brightness $clamped"
    ).success
  }

  suspend fun lockDeviceScreen(): Boolean =
    runShell("input keyevent 26").success

  suspend fun setHeadsUpNotificationsSuppressed(suppress: Boolean): Boolean =
    runShell(
      "settings put global heads_up_notifications_enabled ${if (suppress) 0 else 1}"
    ).success

  suspend fun isHeadsUpNotificationsSuppressed(): Boolean {
    val res = runShell("settings get global heads_up_notifications_enabled")
    return res.success && res.output.trim() == "0"
  }

  suspend fun queryMobileDataEnabled(): Boolean? {
    val res = runShell("settings get global mobile_data")
    return when (res.output.trim()) {
      "1" -> true
      "0" -> false
      else -> null
    }
  }

  suspend fun grantOverlayAndNotificationPermissions(
    packageName: String,
    listenerComponentFlattened: String,
  ): Boolean {
    val safePkg = sanitizePackageToken(packageName) ?: return false
    val safeComponent = sanitizeComponentToken(listenerComponentFlattened) ?: return false

    val overlayOk = runShell("appops set $safePkg SYSTEM_ALERT_WINDOW allow").success
    val listenerOk = runShell("cmd notification allow_listener $safeComponent").success
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      runShell("pm grant $safePkg android.permission.POST_NOTIFICATIONS")
    }
    return overlayOk || listenerOk
  }

  private fun sanitizePackageToken(raw: String): String? =
    raw.takeIf { it.matches(Regex("^[a-zA-Z0-9._]+$")) }

  private fun sanitizeComponentToken(raw: String): String? =
    raw.takeIf { it.matches(Regex("^[a-zA-Z0-9._/$]+$")) }

  private fun isShizukuManagerInstalled(context: Context): Boolean {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
          SHIZUKU_MANAGER_PKG,
          PackageManager.PackageInfoFlags.of(0L),
        )
      } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(SHIZUKU_MANAGER_PKG, 0)
      }
      true
    } catch (_: Exception) {
      false
    }
  }

  private fun safePingBinder(): Boolean =
    try {
      Shizuku.pingBinder()
    } catch (_: Throwable) {
      false
    }

  private fun safeHasPermission(): Boolean =
    try {
      !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
      false
    }
}
