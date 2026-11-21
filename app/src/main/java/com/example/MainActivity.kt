package com.example

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppModel
import com.example.data.AppRepository
import com.example.data.LocaleHelper
import com.example.data.MusicRepository
import com.example.data.WallpaperChoice
import com.example.data.WeatherRepository
import com.example.debug.DebugLogStore
import com.example.notifications.DynamicNotificationItem
import com.example.notifications.DynamicNotificationStore
import com.example.notifications.FloatingDynamicOverlayController
import com.example.notifications.ForegroundActivityTracker
import com.example.quicksettings.SystemQuickSettingsHelper
import com.example.shizuku.SystemControlRepository
import com.example.ui.LauncherViewModel
import com.example.ui.components.AppDrawerSheet
import com.example.ui.components.BiometricAvailability
import com.example.ui.components.BootWelcomeScreen
import com.example.ui.components.DebugConsoleDialog
import com.example.ui.components.HiddenAppsDialog
import com.example.ui.components.HomeScreen
import com.example.ui.components.MusicLyricsDialog
import com.example.ui.components.PermissionsDialog
import com.example.ui.components.PinSetupDialog
import com.example.ui.components.QuickSettingsSheet
import com.example.ui.components.SettingsScreen
import com.example.ui.components.WallpaperBackdrop
import com.example.ui.theme.SLauncher6Theme

/**
 * Entry point for SLauncher 6.
 * Supports:
 * - Wallpaper backdrop (9 curated photos, custom photos, or transparent system live wallpaper)
 * - Guided runtime & system permissions setup (POST_NOTIFICATIONS, Notification Access, Overlay, Shizuku, Default Home)
 * - Dynamic localization following in-app Language setting
 * - Dynamic 3-second Welcome Pop-Up & Universal Floating Dynamic Notifications
 * - Shizuku-backed Custom Quick Settings Control Center + Native Biometric Lock Screen
 * - Full-screen Home app grid/list + A–Z App Drawer + Settings hub
 */
class MainActivity : FragmentActivity() {

  private val viewModel: LauncherViewModel by viewModels {
    LauncherViewModel.Factory(
      AppRepository(applicationContext),
      WeatherRepository(applicationContext),
      MusicRepository(applicationContext),
      SystemControlRepository(applicationContext),
    )
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
    )

    handleSettingsOnlyLaunchIntent(intent)
    if (intent?.getBooleanExtra(BootReceiver.EXTRA_FROM_BOOT, false) == true) {
      viewModel.onBootIntentReceived()
    }
    if (
      intent?.action == TileService.ACTION_QS_TILE_PREFERENCES ||
        intent?.getBooleanExtra(SystemQuickSettingsHelper.EXTRA_OPEN_SETTINGS, false) == true
    ) {
      viewModel.openSettings()
    }

    setContent {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      val notifications by DynamicNotificationStore.notifications.collectAsStateWithLifecycle()
      val unlockEventCount by DynamicNotificationStore.unlockEventCount.collectAsStateWithLifecycle()
      val lifecycleOwner = LocalLifecycleOwner.current

      val localizedContext =
        remember(uiState.settings.language) {
          LocaleHelper.localizedContext(this@MainActivity, uiState.settings.language)
        }
      var isPinSetupDialogOpen by remember { mutableStateOf(false) }
      var biometricAvailability by remember { mutableStateOf(checkBiometricAvailability()) }

      val refreshAllPermissions = {
        val postNotifGranted =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
              applicationContext,
              Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
          } else {
            true
          }
        val locationGranted =
          ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
          ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
              applicationContext,
              Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        viewModel.refreshPermissionStatuses(
          postNotificationsGranted = postNotifGranted,
          notificationAccessGranted =
            DynamicNotificationStore.isNotificationAccessGranted(applicationContext),
          overlayPermissionGranted =
            FloatingDynamicOverlayController.canDrawOverlays(applicationContext),
          locationGranted = locationGranted,
        )
      }

      val postNotificationsLauncher =
        rememberLauncherForActivityResult(
          contract = ActivityResultContracts.RequestPermission(),
          onResult = { refreshAllPermissions() },
        )

      val locationPermissionLauncher =
        rememberLauncherForActivityResult(
          contract = ActivityResultContracts.RequestPermission(),
          onResult = { granted ->
            refreshAllPermissions()
            if (granted) viewModel.refreshWeather(forceRefresh = true)
          },
        )

      val requestPostNotificationsPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
      }

      val requestLocationPermission: () -> Unit = {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
      }

      val wallpaperPickerLauncher =
        rememberLauncherForActivityResult(
          contract = ActivityResultContracts.PickVisualMedia(),
          onResult = { uri ->
            if (uri != null) {
              viewModel.importCustomWallpaper(uri) { ok ->
                if (!ok) {
                  Toast.makeText(
                      localizedContext,
                      localizedContext.getString(R.string.wallpaper_import_error),
                      Toast.LENGTH_SHORT,
                    )
                    .show()
                }
              }
            }
          },
        )

      DisposableEffect(lifecycleOwner) {
        val packageReceiver =
          object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
              viewModel.refreshApps()
            }
          }
        val filter =
          IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
          }
        registerReceiver(packageReceiver, filter)

        val observer = LifecycleEventObserver { _, event ->
          when (event) {
            Lifecycle.Event.ON_RESUME -> {
              ForegroundActivityTracker.onActivityResumed()
              biometricAvailability = checkBiometricAvailability()
              refreshAllPermissions()
              viewModel.onLauncherResumed()
            }
            Lifecycle.Event.ON_PAUSE -> {
              ForegroundActivityTracker.onActivityPaused()
            }
            else -> Unit
          }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshAllPermissions()

        onDispose {
          lifecycleOwner.lifecycle.removeObserver(observer)
          runCatching { unregisterReceiver(packageReceiver) }
        }
      }

      LaunchedEffect(unlockEventCount) {
        if (
          unlockEventCount > 0L &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
          viewModel.onLauncherResumed()
        }
      }

      LaunchedEffect(uiState.settings.wallpaperChoice) {
        if (uiState.settings.wallpaperChoice == WallpaperChoice.SYSTEM) {
          window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
          window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        } else {
          window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
          window.setBackgroundDrawable(ColorDrawable(AndroidColor.parseColor("#090C14")))
        }
      }

      // Always intercept Back on the Home Launcher so pressing Back never exits to the stock launcher
      BackHandler(enabled = true) {
        when {
          uiState.isBootWelcomeVisible -> Unit
          isPinSetupDialogOpen -> isPinSetupDialogOpen = false
          uiState.isDebugConsoleOpen -> viewModel.closeDebugConsole()
          uiState.isPermissionsDialogOpen -> viewModel.closePermissionsDialog()
          uiState.musicState.isFullLyricsSheetOpen -> viewModel.closeFullLyricsSheet()
          uiState.isHiddenAppsDialogOpen -> viewModel.closeHiddenAppsDialog()
          uiState.isQuickSettingsOpen -> viewModel.closeQuickSettings()
          uiState.isSettingsOpen -> viewModel.closeSettings()
          uiState.isDrawerOpen -> viewModel.closeDrawer()
          uiState.searchQuery.isNotEmpty() -> viewModel.clearSearch()
          uiState.musicState.isLyricsExpandedInline -> viewModel.toggleInlineLyrics()
        }
      }

      val handleLaunchApp: (AppModel) -> Unit = { app ->
        if (!viewModel.launchApp(app)) {
          Toast.makeText(
              localizedContext,
              localizedContext.getString(R.string.launch_error, app.label),
              Toast.LENGTH_SHORT,
            )
            .show()
        }
      }

      val handleOpenNotification: (DynamicNotificationItem) -> Unit = { item ->
        try {
          item.contentIntent?.send()
        } catch (_: Exception) {
          // Ignore canceled PendingIntent
        }
      }

      val handleHideApp: (AppModel) -> Unit = { app ->
        viewModel.hideApp(app)
        val hasCode = uiState.settings.hiddenAppsCode.trim().isNotEmpty()
        val msg =
          localizedContext.getString(
            if (hasCode) R.string.app_hold_hidden_toast_with_code
            else R.string.app_hold_hidden_toast_no_code,
            app.label,
          )
        Toast.makeText(localizedContext, msg, Toast.LENGTH_LONG).show()
      }

      val handleUnhideApp: (AppModel) -> Unit = { app ->
        viewModel.unhideApp(app)
        Toast.makeText(
            localizedContext,
            localizedContext.getString(R.string.app_hold_unhidden_toast, app.label),
            Toast.LENGTH_SHORT,
          )
          .show()
      }

      CompositionLocalProvider(LocalContext provides localizedContext) {
        SLauncher6Theme {
          Box(modifier = Modifier.fillMaxSize()) {
            // Selected bundled/custom photo wallpaper or transparent system live wallpaper
            WallpaperBackdrop(
              wallpaperChoice = uiState.settings.wallpaperChoice,
              customWallpaperPath = uiState.settings.selectedCustomWallpaperPath,
            )

            HomeScreen(
              uiState = uiState,
              notifications = notifications,
              loadIcon = viewModel::loadIcon,
              onLaunchApp = handleLaunchApp,
              onOpenAppInfo = viewModel::openAppInfo,
              onHideApp = handleHideApp,
              onUnhideApp = handleUnhideApp,
              onUninstallApp = viewModel::uninstallApp,
              onSearchQueryChange = viewModel::onSearchQueryChange,
              onClearSearch = viewModel::clearSearch,
              onOpenDrawer = viewModel::openDrawer,
              onOpenQuickSettings = viewModel::openQuickSettings,
              onOpenSettings = viewModel::openSettings,
              onPreviewLockScreen = { viewModel.triggerLockDevice { openLockScreen() } },
              onRequestLocationPermission = requestLocationPermission,
              onRefreshWeather = { viewModel.refreshWeather(forceRefresh = true) },
              onRequestNotificationAccess = {
                DynamicNotificationStore.openNotificationAccessSettings(this@MainActivity)
              },
              onOpenNotification = handleOpenNotification,
              onDismissNotification = { DynamicNotificationStore.remove(it.key) },
              onMusicPlayPause = viewModel::toggleMusicPlayPause,
              onMusicSkipPrevious = viewModel::skipMusicPrevious,
              onMusicSkipNext = viewModel::skipMusicNext,
              onMusicSeekTo = viewModel::seekMusicTo,
              onOpenMusicPlayerApp = viewModel::openMusicPlayerApp,
              onToggleInlineLyrics = viewModel::toggleInlineLyrics,
              onOpenFullLyrics = viewModel::openFullLyricsSheet,
              onSetDefaultHome = viewModel::openDefaultHomeSettings,
            )

            QuickSettingsSheet(
              visible = uiState.isQuickSettingsOpen,
              state = uiState.quickSettingsState,
              onToggleWifi = viewModel::toggleWifi,
              onToggleMobileData = viewModel::toggleMobileData,
              onToggleBluetooth = viewModel::toggleBluetooth,
              onToggleFlashlight = viewModel::toggleFlashlight,
              onToggleDarkMode = viewModel::toggleDarkMode,
              onToggleAutoRotate = viewModel::toggleAutoRotate,
              onBrightnessChange = viewModel::setScreenBrightness,
              onVolumeChange = viewModel::setMediaVolumePercent,
              onConnectShizuku = { viewModel.connectShizuku(this@MainActivity) },
              onLockScreen = { viewModel.triggerLockDevice { openLockScreen() } },
              onOpenSettings = viewModel::openSettings,
              onOpenSystemQuickSettings = {
                viewModel.closeQuickSettings()
                SystemQuickSettingsHelper.expandSystemQuickSettings(this@MainActivity)
              },
              onClose = viewModel::closeQuickSettings,
            )

            AppDrawerSheet(
              visible = uiState.isDrawerOpen,
              allApps = uiState.allApps,
              alphabetIndexMap = uiState.alphabetIndexMap,
              loadIcon = viewModel::loadIcon,
              onAppClick = handleLaunchApp,
              onOpenAppInfo = viewModel::openAppInfo,
              onHideApp = handleHideApp,
              onUninstallApp = viewModel::uninstallApp,
              onOpenSettings = viewModel::openSettings,
              onClose = viewModel::closeDrawer,
            )

            SettingsScreen(
              visible = uiState.isSettingsOpen,
              settings = uiState.settings,
              shizukuState = uiState.quickSettingsState.shizukuState,
              diagnostics = uiState.diagnosticsSnapshot,
              biometricAvailability = biometricAvailability,
              isPostNotificationsGranted = uiState.isPostNotificationsGranted,
              isLocationGranted = uiState.isLocationGranted,
              isNotificationAccessGranted = uiState.isNotificationAccessGranted,
              isOverlayPermissionGranted = uiState.isOverlayPermissionGranted,
              isDefaultHome = uiState.isDefaultHome,
              onSelectLanguage = viewModel::setLanguage,
              onSelectWallpaper = viewModel::setWallpaperChoice,
              onSelectCustomWallpaper = viewModel::selectCustomWallpaper,
              onAddCustomWallpaper = {
                wallpaperPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              onRemoveCustomWallpaper = viewModel::removeCustomWallpaper,
              onApplySystemWallpaper = { choice, customPath ->
                viewModel.applyWallpaperToSystem(choice, customPath) { ok ->
                  Toast.makeText(
                      localizedContext,
                      localizedContext.getString(
                        if (ok) R.string.wallpaper_applied_toast else R.string.wallpaper_apply_error
                      ),
                      Toast.LENGTH_SHORT,
                    )
                    .show()
                }
              },
              onSelectHomeLayout = viewModel::setHomeAppsLayout,
              onToggleWelcomePopup = viewModel::setWelcomePopupEnabled,
              onWelcomeTextChange = viewModel::setCustomWelcomeText,
              onTestWelcomePopup = {
                viewModel.closeSettings()
                viewModel.triggerWelcomePopup()
              },
              onToggleLockScreen = { enabled ->
                viewModel.setLockScreenEnabled(enabled)
                (application as? SLauncherApp)?.syncLockScreenReceiver()
              },
              onSelectLockAuthMode = viewModel::setLockAuthMode,
              onTestBiometricUnlock = {
                launchFingerprintTestPrompt(localizedContext) { ok ->
                  Toast.makeText(
                      localizedContext,
                      localizedContext.getString(
                        if (ok) R.string.settings_biometric_test_success
                        else R.string.lockscreen_biometric_failed
                      ),
                      Toast.LENGTH_SHORT,
                    )
                    .show()
                }
              },
              onOpenPinSetup = { isPinSetupDialogOpen = true },
              onToggleBiometricAutoPrompt = viewModel::setBiometricAutoPrompt,
              onToggleShizukuRealLock = viewModel::setShizukuRealLockEnabled,
              onDisableSystemKeyguardViaShizuku = {
                viewModel.attemptDisableSystemKeyguardViaShizuku { res ->
                  Toast.makeText(
                      localizedContext,
                      localizedContext.getString(
                        if (res.success) R.string.shizuku_disable_keyguard_ok
                        else R.string.shizuku_disable_keyguard_fail
                      ),
                      Toast.LENGTH_LONG,
                    )
                    .show()
                }
              },
              onPreviewLockScreen = { openLockScreen() },
              onOpenSecuritySettings = viewModel::openSystemSecuritySettings,
              onToggleDynamicNotifications = viewModel::setDynamicNotificationsEnabled,
              onToggleFloatingOverlayPopup = viewModel::setFloatingOverlayPopupEnabled,
              onToggleSuppressSystemHeadsUp = { suppress ->
                viewModel.setSuppressSystemHeadsUp(suppress) { ok ->
                  Toast.makeText(
                      localizedContext,
                      localizedContext.getString(
                        if (ok) R.string.shizuku_headsup_updated
                        else R.string.shizuku_autogrant_need_conn
                      ),
                      Toast.LENGTH_SHORT,
                    )
                    .show()
                }
              },
              onRequestNotificationAccess = {
                DynamicNotificationStore.openNotificationAccessSettings(this@MainActivity)
              },
              onRequestOverlayPermission = {
                FloatingDynamicOverlayController.openOverlayPermissionSettings(this@MainActivity)
              },
              onTestDynamicNotification = {
                viewModel.closeSettings()
                DynamicNotificationStore.triggerPreviewNotification(
                  title = localizedContext.getString(R.string.dynamic_notif_test_title),
                  body = localizedContext.getString(R.string.dynamic_notif_test_body),
                  appName = localizedContext.getString(R.string.app_name),
                )
              },
              onConnectShizuku = { viewModel.connectShizuku(this@MainActivity) },
              onAutoGrantViaShizuku = {
                viewModel.autoGrantPrivilegesViaShizuku { ok ->
                  refreshAllPermissions()
                  Toast.makeText(
                      localizedContext,
                      localizedContext.getString(
                        if (ok) R.string.shizuku_autogrant_success
                        else R.string.shizuku_autogrant_need_conn
                      ),
                      Toast.LENGTH_SHORT,
                    )
                    .show()
                }
              },
              onDiagnoseRebindShizuku = {
                viewModel.forceRebindShizuku { res ->
                  refreshAllPermissions()
                  Toast.makeText(
                      localizedContext,
                      localizedContext.getString(
                        if (res.success) R.string.shizuku_rebind_success
                        else R.string.shizuku_rebind_failed,
                        res.output.ifBlank { res.exitCode.toString() },
                      ),
                      Toast.LENGTH_LONG,
                    )
                    .show()
                }
              },
              onOpenDebugConsole = viewModel::openDebugConsole,
              onToggleAppIconSettingsOnly = viewModel::setAppIconOpensSettingsOnly,
              onToggleBootWelcome = viewModel::setBootWelcomeEnabled,
              onHiddenAppsCodeChange = viewModel::setHiddenAppsCode,
              onOpenHiddenAppsEditor = viewModel::openHiddenAppsDialog,
              onOpenPermissionsDialog = viewModel::openPermissionsDialog,
              onSetDefaultHome = viewModel::openDefaultHomeSettings,
              onClose = viewModel::closeSettings,
            )

            if (uiState.musicState.isFullLyricsSheetOpen) {
              MusicLyricsDialog(
                musicState = uiState.musicState,
                onPlayPause = viewModel::toggleMusicPlayPause,
                onSkipPrevious = viewModel::skipMusicPrevious,
                onSkipNext = viewModel::skipMusicNext,
                onSeekTo = viewModel::seekMusicTo,
                onDismiss = viewModel::closeFullLyricsSheet,
              )
            }

            if (uiState.isHiddenAppsDialogOpen) {
              HiddenAppsDialog(
                allInstalledApps = uiState.allInstalledApps,
                currentlyHiddenKeys = uiState.settings.hiddenComponentKeys,
                loadIcon = viewModel::loadIcon,
                onDismiss = viewModel::closeHiddenAppsDialog,
                onSaveSelection = viewModel::saveHiddenAppsSelection,
              )
            }

            if (uiState.isPermissionsDialogOpen) {
              PermissionsDialog(
                isPostNotificationsGranted = uiState.isPostNotificationsGranted,
                isLocationGranted = uiState.isLocationGranted,
                isNotificationAccessGranted = uiState.isNotificationAccessGranted,
                isOverlayPermissionGranted = uiState.isOverlayPermissionGranted,
                shizukuState = uiState.quickSettingsState.shizukuState,
                isDefaultHome = uiState.isDefaultHome,
                onRequestPostNotifications = requestPostNotificationsPermission,
                onRequestLocation = requestLocationPermission,
                onRequestNotificationAccess = {
                  DynamicNotificationStore.openNotificationAccessSettings(this@MainActivity)
                },
                onRequestOverlayPermission = {
                  FloatingDynamicOverlayController.openOverlayPermissionSettings(this@MainActivity)
                },
                onConnectShizuku = { viewModel.connectShizuku(this@MainActivity) },
                onRequestDefaultHome = viewModel::openDefaultHomeSettings,
                onDismiss = { viewModel.closePermissionsDialog(markPromptShown = true) },
              )
            }

            if (isPinSetupDialogOpen) {
              PinSetupDialog(
                hasExistingPin = uiState.settings.hasLockPinConfigured,
                onSavePin = { pin ->
                  if (viewModel.saveLockPin(pin)) {
                    isPinSetupDialogOpen = false
                    Toast.makeText(
                        localizedContext,
                        localizedContext.getString(R.string.pin_setup_saved_toast),
                        Toast.LENGTH_SHORT,
                      )
                      .show()
                  }
                },
                onRemovePin = {
                  viewModel.clearLockPin()
                  isPinSetupDialogOpen = false
                  Toast.makeText(
                      localizedContext,
                      localizedContext.getString(R.string.pin_setup_removed_toast),
                      Toast.LENGTH_SHORT,
                    )
                    .show()
                },
                onDismiss = { isPinSetupDialogOpen = false },
              )
            }

            if (uiState.isDebugConsoleOpen) {
              DebugConsoleDialog(
                shizukuState = uiState.quickSettingsState.shizukuState,
                diagnostics = uiState.diagnosticsSnapshot,
                onRefreshDiagnostics = viewModel::refreshDiagnostics,
                onRebindShizuku = { callback -> viewModel.forceRebindShizuku(callback) },
                onTestDisableKeyguard = { callback ->
                  viewModel.attemptDisableSystemKeyguardViaShizuku(callback)
                },
                onRunCommand = { cmd, callback -> viewModel.runDebugShellCommand(cmd, callback) },
                onDismiss = viewModel::closeDebugConsole,
              )
            }

            if (uiState.isBootWelcomeVisible) {
              BootWelcomeScreen(
                onAnimationFinished = viewModel::dismissBootWelcome,
              )
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (intent.getBooleanExtra(BootReceiver.EXTRA_FROM_BOOT, false)) {
      viewModel.onBootIntentReceived()
      return
    }
    if (
      intent.action == TileService.ACTION_QS_TILE_PREFERENCES ||
        intent.getBooleanExtra(SystemQuickSettingsHelper.EXTRA_OPEN_SETTINGS, false)
    ) {
      viewModel.openSettings()
      return
    }
    if (handleSettingsOnlyLaunchIntent(intent)) return

    if (Intent.ACTION_MAIN == intent.action) {
      viewModel.closePermissionsDialog(markPromptShown = true)
      viewModel.closeHiddenAppsDialog()
      viewModel.closeFullLyricsSheet()
      viewModel.closeQuickSettings()
      viewModel.closeSettings()
      viewModel.closeDrawer()
      viewModel.clearSearch()
      viewModel.onLauncherResumed()
    }
  }

  private fun handleSettingsOnlyLaunchIntent(launchIntent: Intent?): Boolean {
    val state = viewModel.uiState.value
    val launchedFromIcon =
      launchIntent?.hasCategory(Intent.CATEGORY_LAUNCHER) == true &&
        launchIntent.hasCategory(Intent.CATEGORY_HOME) == false
    if (state.isDefaultHome && state.settings.appIconOpensSettingsOnly && launchedFromIcon) {
      viewModel.openSettings()
      return true
    }
    return false
  }

  private fun openLockScreen() {
    val lockIntent =
      Intent(this, LockScreenActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
      }
    startActivity(lockIntent)
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

  private fun launchFingerprintTestPrompt(localizedContext: Context, onResult: (Boolean) -> Unit) {
    val executor = ContextCompat.getMainExecutor(this)
    val prompt =
      BiometricPrompt(
        this,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            DebugLogStore.log("Biometric", "Settings fingerprint test verified")
            onResult(true)
          }

          override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            DebugLogStore.log("Biometric", "Settings fingerprint test mismatch", isError = true)
            onResult(false)
          }
        },
      )
    val info =
      BiometricPrompt.PromptInfo.Builder()
        .setTitle(localizedContext.getString(R.string.lockscreen_biometric_dialog_title))
        .setSubtitle(localizedContext.getString(R.string.lockscreen_biometric_dialog_subtitle))
        .setNegativeButtonText(localizedContext.getString(R.string.action_cancel))
        .setAllowedAuthenticators(
          BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        .setConfirmationRequired(false)
        .build()
    runCatching { prompt.authenticate(info) }.onFailure { err ->
      DebugLogStore.log("Biometric", "Settings test failed: ${err.message}", isError = true)
    }
  }
}
