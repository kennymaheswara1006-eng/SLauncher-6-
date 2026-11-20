package com.example

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppRepository
import com.example.data.LocaleHelper
import com.example.data.WallpaperChoice
import com.example.data.WeatherRepository
import com.example.data.WeatherUiState
import com.example.notifications.DynamicNotificationItem
import com.example.notifications.DynamicNotificationStore
import com.example.ui.components.LockScreenContent
import com.example.ui.theme.SLauncher6Theme
import kotlinx.coroutines.launch

/**
 * Native full-screen Lock Screen Activity using Android's showWhenLocked + turnScreenOn
 * without any floating overlay (SYSTEM_ALERT_WINDOW).
 */
class LockScreenActivity : ComponentActivity() {

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

      val localizedContext =
        remember(settings.language) {
          LocaleHelper.localizedContext(this@LockScreenActivity, settings.language)
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
