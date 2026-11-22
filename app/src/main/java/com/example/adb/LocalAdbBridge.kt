package com.example.adb

import android.content.Context
import android.os.Build
import com.example.debug.DebugLogStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class AdbConnectionState {
  DISABLED_IN_SETTINGS,
  DISCONNECTED,
  AUTHORIZING,
  CONNECTING,
  CONNECTED,
}

/**
 * Self-contained built-in USB & Wireless ADB client bridge for SLauncher 6.
 * Replaces external Shizuku entirely: speaks native ADB wire protocol directly from inside
 * SLauncher 6 using `AdbKeyStore` (RSA-2048) + `AdbWireClient` (TCP/TLS).
 */
object LocalAdbBridge {

  private const val ADB_PREFS = "slauncher6_adb_prefs"
  private const val KEY_TARGET_HOST = "adb_target_host"
  private const val KEY_TARGET_PORT = "adb_target_port"
  const val DEFAULT_HOST = "127.0.0.1"
  const val DEFAULT_PORT = 5555

  private val initialized = AtomicBoolean(false)
  private val commandMutex = Mutex()

  private var appContext: Context? = null
  private var keyStore: AdbKeyStore? = null
  private var wireClient: AdbWireClient? = null

  private val _connectionState = MutableStateFlow(AdbConnectionState.DISCONNECTED)
  val connectionState: StateFlow<AdbConnectionState> = _connectionState.asStateFlow()

  private val _targetHost = MutableStateFlow(DEFAULT_HOST)
  val targetHost: StateFlow<String> = _targetHost.asStateFlow()

  private val _targetPort = MutableStateFlow(DEFAULT_PORT)
  val targetPort: StateFlow<Int> = _targetPort.asStateFlow()

  fun init(context: Context) {
    val ctx = context.applicationContext
    appContext = ctx
    if (initialized.compareAndSet(false, true)) {
      val ks = AdbKeyStore(ctx)
      keyStore = ks
      wireClient = AdbWireClient(ks)
      val prefs = ctx.getSharedPreferences(ADB_PREFS, Context.MODE_PRIVATE)
      _targetHost.value =
        prefs.getString(KEY_TARGET_HOST, DEFAULT_HOST)?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST
      _targetPort.value =
        prefs.getInt(KEY_TARGET_PORT, DEFAULT_PORT).takeIf { it in 1..65535 } ?: DEFAULT_PORT
      DebugLogStore.log(
        "ADB",
        "Built-in ADB engine ready (${_targetHost.value}:${_targetPort.value})",
      )
    }
    refreshStatus()
  }

  fun setTargetEndpoint(host: String, port: Int) {
    val safeHost = host.trim().ifBlank { DEFAULT_HOST }
    val safePort = port.coerceIn(1, 65535)
    _targetHost.value = safeHost
    _targetPort.value = safePort
    appContext?.getSharedPreferences(ADB_PREFS, Context.MODE_PRIVATE)
      ?.edit()
      ?.putString(KEY_TARGET_HOST, safeHost)
      ?.putInt(KEY_TARGET_PORT, safePort)
      ?.apply()
  }

  fun getPublicKeyFingerprint(): String =
    runCatching { keyStore?.getKeyFingerprintPreview().orEmpty() }.getOrDefault("")

  fun refreshStatus(): AdbConnectionState {
    val ctx = appContext
    val client = wireClient
    val next =
      when {
        client?.isSocketOpen == true -> AdbConnectionState.CONNECTED
        ctx != null && run {
          val env = AdbDiscoveryHelper.readEnvironmentStatus(ctx)
          !env.usbDebuggingEnabled && !env.wirelessDebuggingEnabled
        } -> AdbConnectionState.DISABLED_IN_SETTINGS
        else -> AdbConnectionState.DISCONNECTED
      }
    _connectionState.value = next
    return next
  }

  fun isConnected(): Boolean = wireClient?.isSocketOpen == true

  suspend fun connect(
    host: String = _targetHost.value,
    port: Int = _targetPort.value,
  ): AdbCommandResult =
    withContext(Dispatchers.IO) {
      commandMutex.withLock {
        val ctx = appContext ?: return@withLock AdbCommandResult(false, -1, "Missing context")
        init(ctx)
        val safeHost = host.trim().ifBlank { DEFAULT_HOST }
        val safePort = port.coerceIn(1, 65535)
        setTargetEndpoint(safeHost, safePort)

        val client = wireClient ?: return@withLock AdbCommandResult(false, -1, "ADB client uninitialized")
        _connectionState.value = AdbConnectionState.CONNECTING
        DebugLogStore.log("ADB", "Connecting built-in ADB to $safeHost:$safePort ...")

        return@withLock try {
          client.connect(
            host = safeHost,
            port = safePort,
            onAwaitingAuthorization = {
              _connectionState.value = AdbConnectionState.AUTHORIZING
            },
          )
          _connectionState.value = AdbConnectionState.CONNECTED
          val probe = client.runShell("id")
          DebugLogStore.log(
            "ADB",
            "ADB Online: ${probe.output.ifBlank { client.banner }}",
            isError = !probe.success,
          )
          probe
        } catch (t: Throwable) {
          client.close()
          refreshStatus()
          val reason = t.message.orEmpty().ifBlank { t.javaClass.simpleName }
          DebugLogStore.log(
            "ADB",
            "Connect $safeHost:$safePort failed: $reason",
            isError = true,
          )
          AdbCommandResult(false, -1, reason)
        }
      }
    }

  suspend fun autoConnectBestEndpoint(): AdbCommandResult {
    val ctx = appContext
    val discovered = AdbDiscoveryHelper.discoveredEndpoints.value
    val candidates = buildList {
      add(_targetHost.value to _targetPort.value)
      discovered.forEach { add(it.host to it.port) }
      if (_targetHost.value != DEFAULT_HOST || _targetPort.value != DEFAULT_PORT) {
        add(DEFAULT_HOST to DEFAULT_PORT)
      }
    }.distinct()

    var lastResult = AdbCommandResult(false, -1, "No ADB endpoint reachable")
    for ((h, p) in candidates) {
      val res = connect(h, p)
      if (res.success) return res
      lastResult = res
    }
    if (ctx != null) {
      val env = AdbDiscoveryHelper.readEnvironmentStatus(ctx)
      if (!env.usbDebuggingEnabled && !env.wirelessDebuggingEnabled) {
        DebugLogStore.log(
          "Diagnosa",
          "USB Debugging & Wireless Debugging nonaktif di Opsi Pengembang",
          isError = true,
        )
      }
    }
    return lastResult
  }

  suspend fun disconnect() {
    withContext(Dispatchers.IO) {
      commandMutex.withLock {
        wireClient?.close()
        refreshStatus()
        DebugLogStore.log("ADB", "ADB connection closed")
      }
    }
  }

  suspend fun runDebugShellCommand(command: String): AdbCommandResult {
    val trimmed = command.trim()
    if (trimmed.isEmpty()) {
      return AdbCommandResult(false, 1, "Empty command")
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

  suspend fun runShell(command: String): AdbCommandResult =
    withContext(Dispatchers.IO) {
      commandMutex.withLock {
        val client = wireClient
        if (client == null || !client.isSocketOpen) {
          refreshStatus()
          return@withLock AdbCommandResult(false, -1, "Built-in ADB not connected")
        }
        try {
          val result = client.runShell(command)
          if (!result.success) {
            DebugLogStore.log(
              "Shell",
              "'$command' exit=${result.exitCode}: ${result.output}",
              isError = true,
            )
          }
          result
        } catch (t: Throwable) {
          client.close()
          refreshStatus()
          DebugLogStore.log("Shell", "ADB socket error: ${t.message}", isError = true)
          AdbCommandResult(false, -1, t.message.orEmpty())
        }
      }
    }

  suspend fun probeUid(): AdbCommandResult = runShell("id")

  suspend fun querySystemLockscreenDisabledSetting(): String {
    val res = runShell("settings get secure lockscreen.disabled")
    return if (res.success) res.output.trim().ifBlank { "null" } else "unavailable"
  }

  suspend fun attemptDisableSystemKeyguard(): AdbCommandResult {
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
    runShell("settings put system accelerometer_rotation ${if (enabled) 1 else 0}").success

  suspend fun setScreenBrightness(level0To255: Int): Boolean {
    val clamped = level0To255.coerceIn(10, 255)
    return runShell(
      "settings put system screen_brightness_mode 0 && settings put system screen_brightness $clamped"
    ).success
  }

  suspend fun lockDeviceScreen(): Boolean = runShell("input keyevent 223").success

  suspend fun setHeadsUpNotificationsSuppressed(suppress: Boolean): Boolean {
    val v = if (suppress) 0 else 1
    val g = runShell("settings put global heads_up_notifications_enabled $v").success
    runShell("settings put secure heads_up_notifications_enabled $v")
    return g
  }

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
    runShell("cmd notification set_dnd priority")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      runShell("pm grant $safePkg android.permission.POST_NOTIFICATIONS")
    }
    DebugLogStore.log(
      "ADB",
      "Auto-grant result: overlay=$overlayOk, notifListener=$listenerOk",
      isError = !(overlayOk || listenerOk),
    )
    return overlayOk || listenerOk
  }

  private fun sanitizePackageToken(raw: String): String? =
    raw.takeIf { it.matches(Regex("^[a-zA-Z0-9._]+$")) }

  private fun sanitizeComponentToken(raw: String): String? =
    raw.takeIf { it.matches(Regex("^[a-zA-Z0-9._/$]+$")) }
}
