package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppRepository
import com.example.data.LauncherSettings
import com.example.data.LockAuthMode
import com.example.data.MusicRepository
import com.example.data.WeatherRepository
import com.example.debug.DebugLogStore
import com.example.notifications.ForegroundActivityTracker
import com.example.security.PinSecurityHelper
import com.example.shizuku.ShizukuBridge
import com.example.shizuku.ShizukuConnectionState
import com.example.shizuku.SystemControlRepository
import com.example.ui.LauncherViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        lockAuthMode = LockAuthMode.PIN_ONLY,
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
    assertEquals(LockAuthMode.PIN_ONLY, loaded.lockAuthMode)
    assertFalse(loaded.biometricUnlockEnabled)
    assertFalse(loaded.biometricAutoPrompt)
    assertTrue(loaded.shizukuRealLockEnabled)
    assertTrue(loaded.floatingOverlayPopupEnabled)
    assertTrue(loaded.suppressSystemHeadsUp)
    assertEquals("#7788", loaded.hiddenAppsCode)
    assertTrue(loaded.hiddenComponentKeys.contains("com.secret.app/.MainActivity"))
  }

  @Test
  fun `pin security helper validates length and verifies salted sha256 hashes`() {
    assertFalse(PinSecurityHelper.isValidPinFormat("123"))
    assertFalse(PinSecurityHelper.isValidPinFormat("1234567"))
    assertFalse(PinSecurityHelper.isValidPinFormat("12ab"))
    assertTrue(PinSecurityHelper.isValidPinFormat("2580"))
    assertTrue(PinSecurityHelper.isValidPinFormat("123456"))

    val salt = PinSecurityHelper.generateSalt()
    val hash = PinSecurityHelper.hashPin("258019", salt)
    assertTrue(PinSecurityHelper.verifyPin("258019", hash, salt))
    assertFalse(PinSecurityHelper.verifyPin("000000", hash, salt))
    assertFalse(PinSecurityHelper.verifyPin("2580", hash, salt))
  }

  @Test
  fun `repository and viewmodel manage lock pin and lock auth mode cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = AppRepository(context)
    val vm =
      LauncherViewModel(
        repository = repo,
        weatherRepository = WeatherRepository(context),
        musicRepository = MusicRepository(context),
        systemControlRepository = SystemControlRepository(context),
      )
    ShadowLooper.idleMainLooper()

    assertTrue(vm.saveLockPin("4826"))
    assertTrue(vm.uiState.value.settings.hasLockPinConfigured)
    assertTrue(repo.verifyLockPin("4826"))
    assertFalse(repo.verifyLockPin("9999"))

    vm.setLockAuthMode(LockAuthMode.PIN_ONLY)
    assertEquals(LockAuthMode.PIN_ONLY, vm.uiState.value.settings.lockAuthMode)
    assertFalse(vm.uiState.value.settings.biometricUnlockEnabled)

    vm.setLockAuthMode(LockAuthMode.BIOMETRIC_AND_PIN)
    assertTrue(vm.uiState.value.settings.biometricUnlockEnabled)

    vm.clearLockPin()
    assertFalse(vm.uiState.value.settings.hasLockPinConfigured)
    assertFalse(repo.verifyLockPin("4826"))
  }

  @Test
  fun `debug log store enforces bounded history and viewmodel opens debug console`() {
    DebugLogStore.clear()
    repeat(220) { idx ->
      DebugLogStore.log("Test", "Entry $idx", isError = idx % 2 == 0)
    }
    assertEquals(200, DebugLogStore.logs.value.size)
    assertTrue(DebugLogStore.formatAllAsText().contains("Entry 219"))

    val context = ApplicationProvider.getApplicationContext<Context>()
    val sysControl = SystemControlRepository(context)
    val snapshot = kotlinx.coroutines.runBlocking { sysControl.collectSystemDiagnostics(isDefaultHome = false) }
    assertNotNull(snapshot)

    val vm =
      LauncherViewModel(
        repository = AppRepository(context),
        weatherRepository = WeatherRepository(context),
        musicRepository = MusicRepository(context),
        systemControlRepository = sysControl,
      )
    vm.openDebugConsole()
    ShadowLooper.idleMainLooper()
    assertTrue(vm.uiState.value.isDebugConsoleOpen)

    vm.closeDebugConsole()
    assertFalse(vm.uiState.value.isDebugConsoleOpen)
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
