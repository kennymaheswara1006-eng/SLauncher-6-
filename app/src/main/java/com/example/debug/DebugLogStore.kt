package com.example.debug

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DebugLogEntry(
  val id: Long,
  val timestampMs: Long,
  val category: String,
  val message: String,
  val isError: Boolean = false,
)

data class SystemDiagnosticsSnapshot(
  val shizukuInstalled: Boolean = false,
  val shizukuBinderAlive: Boolean = false,
  val shizukuPermissionGranted: Boolean = false,
  val userServiceBound: Boolean = false,
  val userServiceUidOutput: String = "",
  val isDefaultHome: Boolean = false,
  val hasOverlayPermission: Boolean = false,
  val hasNotificationAccess: Boolean = false,
  val biometricStatusLabel: String = "",
  val systemKeyguardLocked: Boolean = false,
  val systemKeyguardSecure: Boolean = false,
  val systemLockscreenDisabledSetting: String = "unknown",
  val checkedAtMillis: Long = System.currentTimeMillis(),
)

/**
 * In-memory ring buffer storing live diagnostic events across Shizuku, LockScreen,
 * BiometricPrompt, and ADB shell execution so users can troubleshoot directly inside SLauncher 6.
 */
object DebugLogStore {

  private const val MAX_ENTRIES = 200
  private val idCounter = AtomicLong(1L)

  private val _logs = MutableStateFlow<List<DebugLogEntry>>(emptyList())
  val logs: StateFlow<List<DebugLogEntry>> = _logs.asStateFlow()

  fun log(category: String, message: String, isError: Boolean = false) {
    val entry =
      DebugLogEntry(
        id = idCounter.getAndIncrement(),
        timestampMs = System.currentTimeMillis(),
        category = category,
        message = message,
        isError = isError,
      )
    _logs.update { current ->
      val combined = current + entry
      if (combined.size > MAX_ENTRIES) combined.takeLast(MAX_ENTRIES) else combined
    }
  }

  fun clear() {
    _logs.value = emptyList()
  }

  fun formatAllAsText(): String {
    val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    return _logs.value.joinToString("\n") { entry ->
      val level = if (entry.isError) "ERR" else "OK"
      "[${formatter.format(Date(entry.timestampMs))}] [$level/${entry.category}] ${entry.message}"
    }
  }
}
