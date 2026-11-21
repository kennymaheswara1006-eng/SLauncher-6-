package com.example

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppRepository
import com.example.data.LocaleHelper
import com.example.data.WallpaperChoice
import com.example.data.WeatherRepository
import com.example.data.WeatherUiState
import com.example.notifications.DynamicNotificationItem
import com.example.notifications.DynamicNotificationStore
import com.example.notifications.ForegroundActivityTracker
import com.example.ui.components.BiometricAvailability
import com.example.ui.components.LockScreenContent
import com.example.ui.theme.SLauncher6Theme
import kotlinx.coroutines.launch

/**
 * Native full-screen Lock Screen Activity using Android's showWhenLocked + turnScreenOn,
 * upgraded with hardware Fingerprint / BiometricPrompt authentication on FragmentActivity.
 */
class LockScreenActivity : FragmentActivity() {

  private var biometricPrompt: BiometricPrompt? = null
  private var pendingAfterAuth: (() -> Unit)? = null
  private val biometricStatusHintState = mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
          WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
      )
    }

    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
    )

    val repository = AppRepository(applicationContext)
    val weatherRepository = WeatherRepository(applicationContext)

    setContent {
      val settings = remember { repository.loadSettings() }
      LaunchedEffect(settings.wallpaperChoice) {
        if (settings.wallpaperChoice == WallpaperChoice.SYSTEM) {
          window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
          window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        } else {
          window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
          window.setBackgroundDrawable(ColorDrawable(AndroidColor.parseColor("#090C14")))
        }
      }

      val localizedContext =
        remember(settings.language) {
          LocaleHelper.localizedContext(this@LockScreenActivity, settings.language)
        }

      val biometricAvailability = remember { checkBiometricAvailability() }

      LaunchedEffect(biometricAvailability, settings.biometricUnlockEnabled, settings.biometricAutoPrompt) {
        if (
          settings.biometricUnlockEnabled &&
            settings.biometricAutoPrompt &&
            biometricAvailability == BiometricAvailability.AVAILABLE
        ) {
          launchBiometricPrompt(localizedContext) { unlockAndFinish() }
        }
      }

      val notifications by DynamicNotificationStore.notifications.collectAsStateWithLifecycle()
      val isAccessGranted = remember {
        DynamicNotificationStore.isNotificationAccessGranted(applicationContext)
      }
      val coroutineScope = rememberCoroutineScope()
      var weatherState by remember {
        mutableStateOf(
          when {
            !weatherRepository.hasLocationPermission() -> WeatherUiState.NeedsPermission
            else -> weatherRepository.loadCachedWeather()?.let { WeatherUiState.Ready(it) }
              ?: WeatherUiState.Loading
          }
        )
      }

      LaunchedEffect(Unit) {
        if (weatherRepository.hasLocationPermission()) {
          val fresh = weatherRepository.fetchWeather(forceRefresh = false)
          weatherState =
            when {
              fresh != null -> WeatherUiState.Ready(fresh)
              weatherState is WeatherUiState.Ready -> weatherState
              else -> WeatherUiState.Unavailable
            }
        }
      }

      // Disable system Back button from bypassing the lock screen
      BackHandler(enabled = true) {}

      CompositionLocalProvider(LocalContext provides localizedContext) {
        SLauncher6Theme {
          LockScreenContent(
            settings = settings,
            weatherState = weatherState,
            isNotificationAccessGranted = isAccessGranted,
            notifications = notifications,
            biometricAvailability = biometricAvailability,
            biometricStatusHint = biometricStatusHintState.value,
            onRequestBiometricPrompt = {
              launchBiometricPrompt(localizedContext) { unlockAndFinish() }
            },
            onRequestLocationPermission = { unlockAndFinish() },
            onRefreshWeather = {
              if (weatherRepository.hasLocationPermission()) {
                coroutineScope.launch {
                  weatherState = WeatherUiState.Loading
                  val fresh = weatherRepository.fetchWeather(forceRefresh = true)
                  weatherState =
                    if (fresh != null) WeatherUiState.Ready(fresh) else WeatherUiState.Unavailable
                }
              }
            },
            onRequestNotificationAccess = {
              unlockAndRun { DynamicNotificationStore.openNotificationAccessSettings(this) }
            },
            onOpenNotification = { item -> openNotificationAfterUnlock(item) },
            onDismissNotification = { item -> DynamicNotificationStore.remove(item.key) },
            onUnlock = { unlockAndFinish() },
          )
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    ForegroundActivityTracker.onActivityResumed()
  }

  override fun onPause() {
    ForegroundActivityTracker.onActivityPaused()
    super.onPause()
  }

  private fun checkBiometricAvailability(): BiometricAvailability {
    val manager = BiometricManager.from(this)
    val result =
      manager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
          BiometricManager.Authenticators.BIOMETRIC_WEAK
      )
    return when (result) {
      BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
      else -> BiometricAvailability.UNAVAILABLE
    }
  }

  private fun launchBiometricPrompt(localizedContext: Context, onAuthenticated: () -> Unit) {
    if (checkBiometricAvailability() != BiometricAvailability.AVAILABLE) return
    pendingAfterAuth = onAuthenticated
    biometricStatusHintState.value = null

    val executor = ContextCompat.getMainExecutor(this)
    val prompt =
      BiometricPrompt(
        this,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            biometricStatusHintState.value = null
            val action = pendingAfterAuth
            pendingAfterAuth = null
            action?.invoke() ?: unlockAndFinish()
          }

          override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            biometricStatusHintState.value =
              localizedContext.getString(R.string.lockscreen_biometric_failed)
          }

          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            pendingAfterAuth = null
            if (
              errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                errorCode != BiometricPrompt.ERROR_CANCELED
            ) {
              biometricStatusHintState.value = errString.toString()
            }
          }
        },
      )
    biometricPrompt = prompt

    val promptInfo =
      BiometricPrompt.PromptInfo.Builder()
        .setTitle(localizedContext.getString(R.string.lockscreen_biometric_dialog_title))
        .setSubtitle(localizedContext.getString(R.string.lockscreen_biometric_dialog_subtitle))
        .setNegativeButtonText(localizedContext.getString(R.string.lockscreen_biometric_fallback_btn))
        .setAllowedAuthenticators(
          BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        .setConfirmationRequired(false)
        .build()

    runCatching { prompt.authenticate(promptInfo) }
  }

  private fun openNotificationAfterUnlock(item: DynamicNotificationItem) {
    unlockAndRun {
      try {
        item.contentIntent?.send()
      } catch (_: Exception) {
        // Ignore canceled PendingIntents
      }
    }
  }

  private fun unlockAndFinish() {
    unlockAndRun {
      DynamicNotificationStore.markPhoneUnlocked()
      finish()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
      } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
      }
    }
  }

  private fun unlockAndRun(onUnlocked: () -> Unit) {
    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
      keyguardManager != null &&
      keyguardManager.isKeyguardLocked
    ) {
      keyguardManager.requestDismissKeyguard(
        this,
        object : KeyguardManager.KeyguardDismissCallback() {
          override fun onDismissSucceeded() {
            DynamicNotificationStore.markPhoneUnlocked()
            onUnlocked()
          }

          override fun onDismissCancelled() {
            // Stay locked if user cancels system PIN/pattern prompt
          }
        },
      )
    } else {
      onUnlocked()
    }
  }
}
