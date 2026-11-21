package com.example.notifications

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether any SLauncher 6 UI activity (MainActivity or LockScreenActivity)
 * is currently resumed in the foreground, so the floating Dynamic Notification overlay
 * appears only while the user is inside other apps and never duplicates over Home/Lock.
 */
object ForegroundActivityTracker {

  private val resumedCount = AtomicInteger(0)

  private val _isLauncherInForeground = MutableStateFlow(false)
  val isLauncherInForeground: StateFlow<Boolean> = _isLauncherInForeground.asStateFlow()

  fun onActivityResumed() {
    val count = resumedCount.incrementAndGet()
    _isLauncherInForeground.value = count > 0
  }

  fun onActivityPaused() {
    val count = resumedCount.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    _isLauncherInForeground.value = count > 0
  }
}
