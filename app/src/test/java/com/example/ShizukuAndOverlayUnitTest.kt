package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppRepository
import com.example.data.LauncherSettings
import com.example.data.MusicRepository
import com.example.data.WeatherRepository
import com.example.notifications.ForegroundActivityTracker
import com.example.shizuku.ShizukuBridge
import com.example.shizuku.ShizukuConnectionState
import com.example.shizuku.SystemControlRepository
import com.example.ui.LauncherViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShizukuAndOverlayUnitTest {

  @Test
  fun `foreground tracker toggles cleanly without underflow`() {
    ForegroundActivityTracker.onActivityPaused()
    assertFalse(ForegroundActivityTracker.isLauncherInForeground.value)

    ForegroundActivityTracker.onActivityResumed()
    assertTrue(ForegroundActivityTracker.isLauncherInForeground.value)

    ForegroundActivityTracker.onActivityResumed()
    ForegroundActivityTracker.onActivityPaused()
    assertTrue(ForegroundActivityTracker.isLauncherInForeground.value)

    ForegroundActivityTracker.onActivityPaused()
    assertFalse(ForegroundActivityTracker.isLauncherInForeground.value)
  }

  @Test
  fun `repository persists biometric floating overlay hidden code and shizuku lock settings`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = AppRepository(context)
    val updated =
      LauncherSettings(
        biometricUnlockEnabled = false,
        biometricAutoPrompt = false,
        shizukuRealLockEnabled = true,
        floatingOverlayPopupEnabled = true,
        suppressSystemHeadsUp = true,
        hiddenComponentKeys = setOf("com.secret.app/.MainActivity"),
        hiddenAppsCode = "#7788",
      )
    repo.saveSettings(updated)

    val loaded = repo.loadSettings()
    assertFalse(loaded.biometricUnlockEnabled)
    assertFalse(loaded.biometricAutoPrompt)
    assertTrue(loaded.shizukuRealLockEnabled)
    assertTrue(loaded.floatingOverlayPopupEnabled)
    assertTrue(loaded.suppressSystemHeadsUp)
    assertEquals("#7788", loaded.hiddenAppsCode)
    assertTrue(loaded.hiddenComponentKeys.contains("com.secret.app/.MainActivity"))
  }

  @Test
  fun `secret code in search activates hidden apps vault only on exact match`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vm =
      LauncherViewModel(
        repository = AppRepository(context),
        weatherRepository = WeatherRepository(context),
        musicRepository = MusicRepository(context),
        systemControlRepository = SystemControlRepository(context),
      )
    ShadowLooper.idleMainLooper()

    vm.setHiddenAppsCode("#4242")
    vm.onSearchQueryChange("something_else")
    assertFalse(vm.uiState.value.isSecretVaultMatched)

    vm.onSearchQueryChange(" #4242 ")
    assertTrue(vm.uiState.value.isSecretVaultMatched)

    vm.clearSearch()
    assertFalse(vm.uiState.value.isSecretVaultMatched)
  }

  @Test
  fun `shizuku bridge reports not installed safely in test environment`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    ShizukuBridge.init(context)
    assertEquals(ShizukuConnectionState.NOT_INSTALLED, ShizukuBridge.refreshStatus())
  }
}
