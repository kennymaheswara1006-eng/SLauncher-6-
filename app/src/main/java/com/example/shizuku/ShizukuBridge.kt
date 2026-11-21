package com.example.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.example.BuildConfig
import com.example.debug.DebugLogStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * UserService binding, non-root ADB-level system actions, and live DebugLogStore diagnostics.
 */
object ShizukuBridge {

  private const val SHIZUKU_MANAGER_PKG = "moe.shizuku.manager"
  private const val REQUEST_CODE_SHIZUKU = 6021
  private const val BIND_TIMEOUT_MS = 3500L

  private val initialized = AtomicBoolean(false)
  private val commandMutex = Mutex()

  private var appContext: Context? = null
  @Volatile private var shellService: IShizukuShellService? = null
  @Volatile private var isBinding = false
  @Volatile private var lastBindAttemptElapsedMs = 0L

  private val _connectionState = MutableStateFlow(ShizukuConnectionState.NOT_RUNNING)
  val connectionState: StateFlow<ShizukuConnectionState> = _connectionState.asStateFlow()

  private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
    DebugLogStore.log("Shizuku", "Binder received from Shizuku Manager")
    refreshStatus()
  }
  private val binderDeadListener = Shizuku.OnBinderDeadListener {
    shellService = null
    isBinding = false
    DebugLogStore.log("Shizuku", "Binder dead / Shizuku service stopped", isError = true)
    refreshStatus()
  }
  private val permissionResultListener =
    Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
      if (requestCode == REQUEST_CODE_SHIZUKU) {
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        DebugLogStore.log(
          "Shizuku",
          if (granted) "API_V23 permission granted" else "API_V23 permission denied",
          isError = !granted,
        )
        if (granted) {
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
        DebugLogStore.log("Shizuku", "UserService connected: ${name?.shortClassName ?: "shell"}")
        refreshStatus()
      }

      override fun onServiceDisconnected(name: ComponentName?) {
        shellService = null
        isBinding = false
        DebugLogStore.log("Shizuku", "UserService disconnected", isError = true)
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
        DebugLogStore.log("Shizuku", "Bridge initialized (pkg=${context.packageName})")
      }.onFailure { err ->
        DebugLogStore.log("Shizuku", "Init listener failed: ${err.message}", isError = true)
      }
    }
    refreshStatus()
  }

  fun isInstalled(context: Context? = appContext): Boolean =
    context != null && isShizukuManagerInstalled(context)

  fun isBinderAlive(): Boolean = safePingBinder()

  fun hasPermission(): Boolean = safeHasPermission()

  fun isUserServiceBound(): Boolean = shellService?.asBinder()?.isBinderAlive == true

  fun refreshStatus(): ShizukuConnectionState {
    val ctx = appContext
    val next =
      when {
        ctx != null && !isShizukuManagerInstalled(ctx) -> ShizukuConnectionState.NOT_INSTALLED
        !safePingBinder() -> ShizukuConnectionState.NOT_RUNNING
        !safeHasPermission() -> ShizukuConnectionState.NEEDS_PERMISSION
        shellService?.asBinder()?.isBinderAlive == true -> ShizukuConnectionState.CONNECTED
        else -> {
          bindShellUserService()
          if (shellService?.asBinder()?.isBinderAlive == true) ShizukuConnectionState.CONNECTED
          else ShizukuConnectionState.CONNECTING
        }
      }
    if (_connectionState.value != next) {
      DebugLogStore.log("Shizuku", "State -> $next", isError = next == ShizukuConnectionState.NOT_RUNNING)
    }
    _connectionState.value = next
    return next
  }

  fun isConnected(): Boolean =
    refreshStatus() == ShizukuConnectionState.CONNECTED && shellService?.asBinder()?.isBinderAlive == true

  fun requestPermissionOrOpenManager(context: Context) {
    init(context)
    if (!isShizukuManagerInstalled(context)) {
      DebugLogStore.log("Shizuku", "Shizuku Manager not installed -> opening website", isError = true)
      openShizukuManager(context)
      return
    }
    if (!safePingBinder()) {
      DebugLogStore.log("Shizuku", "Shizuku binder not alive -> opening Shizuku Manager", isError = true)
      openShizukuManager(context)
      return
    }
    if (!safeHasPermission()) {
      DebugLogStore.log("Shizuku", "Requesting Shizuku permission dialog")
      runCatching {
        Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
      }.onFailure { err ->
        DebugLogStore.log("Shizuku", "Permission request failed: ${err.message}", isError = true)
        openShizukuManager(context)
      }
    } else {
      bindShellUserService(forceReset = true)
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

  private fun buildUserServiceArgs(ctx: Context): Shizuku.UserServiceArgs =
    Shizuku.UserServiceArgs(
        ComponentName(ctx.packageName, ShizukuShellUserService::class.java.name)
      )
      .daemon(false)
      .processNameSuffix("shizuku_shell")
      .tag("slauncher6_shell_service")
      .debuggable(BuildConfig.DEBUG)
      .version(BuildConfig.VERSION_CODE)

  private fun bindShellUserService(forceReset: Boolean = false) {
    val ctx = appContext ?: return
    val now = SystemClock.elapsedRealtime()
    if (forceReset) {
      runCatching { Shizuku.unbindUserService(buildUserServiceArgs(ctx), serviceConnection, true) }
      shellService = null
      isBinding = false
    } else {
      if (shellService?.asBinder()?.isBinderAlive == true) return
      if (isBinding && (now - lastBindAttemptElapsedMs) < BIND_TIMEOUT_MS) return
    }
    if (!safePingBinder() || !safeHasPermission()) return

    isBinding = true
    lastBindAttemptElapsedMs = now
    DebugLogStore.log("Shizuku", "Binding ShizukuShellUserService...")
    runCatching {
      Shizuku.bindUserService(buildUserServiceArgs(ctx), serviceConnection)
    }.onFailure { err ->
      isBinding = false
      DebugLogStore.log("Shizuku", "bindUserService threw: ${err.message}", isError = true)
    }
  }

  /**
   * Actively unbinds any stale UserService, forces a fresh bind, waits up to 2.5s for binder
   * readiness, and probes `id` to verify ADB UID 2000 shell execution.
   */
  suspend fun forceRebindAndProbe(): ShizukuCommandResult =
    withContext(Dispatchers.IO) {
      val ctx = appContext
        ?: return@withContext ShizukuCommandResult(false, -1, "Missing application context")
      if (!isShizukuManagerInstalled(ctx)) {
        DebugLogStore.log("Diagnosa", "Shizuku Manager belum terpasang", isError = true)
        return@withContext ShizukuCommandResult(false, -1, "Shizuku Manager not installed")
      }
      if (!safePingBinder()) {
        DebugLogStore.log("Diagnosa", "Shizuku Binder mati — pastikan Shizuku berjalan via Wireless Debugging/ADB", isError = true)
        return@withContext ShizukuCommandResult(false, -1, "Binder offline (Start Shizuku first)")
      }
      if (!safeHasPermission()) {
        DebugLogStore.log("Diagnosa", "Izin Shizuku belum diberikan untuk SLauncher 6", isError = true)
        return@withContext ShizukuCommandResult(false, -1, "Permission not granted for SLauncher 6")
      }

      bindShellUserService(forceReset = true)
      var waitedMs = 0L
      while (shellService?.asBinder()?.isBinderAlive != true && waitedMs < 2500L) {
        delay(100L)
        waitedMs += 100L
      }
      refreshStatus()

      if (shellService?.asBinder()?.isBinderAlive != true) {
        DebugLogStore.log("Diagnosa", "Timeout menunggu UserService terhubung (${waitedMs}ms)", isError = true)
        return@withContext ShizukuCommandResult(false, -1, "UserService bind timed out")
      }
      val probe = runShell("id")
      DebugLogStore.log(
        "Diagnosa",
        "Probe id -> exit=${probe.exitCode}: ${probe.output}",
        isError = !probe.success,
      )
      probe
    }

  suspend fun runDebugShellCommand(command: String): ShizukuCommandResult {
    val trimmed = command.trim()
    if (trimmed.isEmpty()) {
      return ShizukuCommandResult(false, 1, "Empty command")
    }
    DebugLogStore.log("Console", "$ $trimmed")
    val res = runShell(trimmed)
    DebugLogStore.log(
      "Console",
      "exit=${res.exitCode} | ${res.output.ifBlank { "(no output)" }}",
      isError = !res.success,
    )
    return res
  }

  private suspend fun runShell(command: String): ShizukuCommandResult =
    withContext(Dispatchers.IO) {
      commandMutex.withLock {
        val service = shellService
        if (service == null || !service.asBinder().isBinderAlive) {
          bindShellUserService()
          DebugLogStore.log("Shell", "Skipped '$command': UserService not bound", isError = true)
          return@withLock ShizukuCommandResult(false, -1, "Shizuku service not connected")
        }
        try {
          val raw = service.execCommand(command).orEmpty()
          val newlineIdx = raw.indexOf('\n')
          val header = if (newlineIdx >= 0) raw.substring(0, newlineIdx) else raw
          val body = if (newlineIdx >= 0) raw.substring(newlineIdx + 1) else ""
          val exitCode = header.removePrefix("EXIT:").trim().toIntOrNull() ?: 1
          val result = ShizukuCommandResult(success = exitCode == 0, exitCode = exitCode, output = body.trim())
          if (!result.success) {
            DebugLogStore.log("Shell", "'$command' failed ($exitCode): ${result.output}", isError = true)
          }
          result
        } catch (t: Throwable) {
          shellService = null
          refreshStatus()
          DebugLogStore.log("Shell", "Exception running '$command': ${t.message}", isError = true)
          ShizukuCommandResult(false, -1, t.message.orEmpty())
        }
      }
    }

  suspend fun probeUid(): ShizukuCommandResult = runShell("id")

  suspend fun querySystemLockscreenDisabledSetting(): String {
    val res = runShell("settings get secure lockscreen.disabled")
    return if (res.success) res.output.trim().ifBlank { "null" } else "unavailable"
  }

  suspend fun attemptDisableSystemKeyguard(): ShizukuCommandResult {
    val cmd =
      "settings put secure lockscreen.disabled 1 && (locksettings set-disabled true 2>/dev/null || true) && settings get secure lockscreen.disabled"
    val res = runShell(cmd)
    DebugLogStore.log(
      "AntiDoubleLock",
      "Disable keyguard attempt exit=${res.exitCode}: ${res.output}",
      isError = !res.success,
    )
    return res
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
    runShell("input keyevent 223").success

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
    DebugLogStore.log(
      "Shizuku",
      "Auto-grant result: overlay=$overlayOk, notifListener=$listenerOk",
      isError = !(overlayOk || listenerOk),
    )
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
