package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.SLauncherApp
import com.example.data.AppLanguage
import com.example.data.AppModel
import com.example.data.AppRepository
import com.example.data.HomeAppsLayout
import com.example.data.LauncherSettings
import com.example.data.LauncherUiState
import com.example.data.LockAuthMode
import com.example.data.LyricsState
import com.example.data.MusicPlaybackInfo
import com.example.data.MusicRepository
import com.example.data.WallpaperChoice
import com.example.data.WeatherRepository
import com.example.data.WeatherUiState
import com.example.debug.DebugLogStore
import com.example.notifications.DynamicNotificationStore
import com.example.shizuku.ShizukuBridge
import com.example.shizuku.ShizukuCommandResult
import com.example.shizuku.SystemControlRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing installed apps, hidden apps filtering, universal Music Player & Lyrics
 * (via MediaSessionManager), instant search filtering, A–Z section index mapping, wallpaper
 * selection, live weather, Shizuku-backed Quick Settings, permissions status, and persistent settings.
 */
class LauncherViewModel(
  private val repository: AppRepository,
  private val weatherRepository: WeatherRepository,
  private val musicRepository: MusicRepository,
  private val systemControlRepository: SystemControlRepository,
) : ViewModel() {

  private var lastLyricsTrackKey: String? = null
  private var lyricsFetchJob: Job? = null

  private val _uiState =
    MutableStateFlow(
      run {
        val initialSettings = repository.loadSettings()
        val hasLoc = weatherRepository.hasLocationPermission()
        val cachedWeather = if (hasLoc) weatherRepository.loadCachedWeather() else null
        val shouldPlayBootWelcome =
          initialSettings.bootWelcomeEnabled &&
            (repository.consumePendingBootWelcome() || !SLauncherApp.hasShownBootWelcomeInProcess)
        if (shouldPlayBootWelcome) {
          SLauncherApp.hasShownBootWelcomeInProcess = true
        }
        LauncherUiState(
          settings = initialSettings,
          isDefaultHome = repository.isDefaultHomeLauncher(),
          isLocationGranted = hasLoc,
          weatherState =
            when {
              !hasLoc -> WeatherUiState.NeedsPermission
              cachedWeather != null -> WeatherUiState.Ready(cachedWeather)
              else -> WeatherUiState.Loading
            },
          isPermissionsDialogOpen = !initialSettings.permissionsPromptShown,
          isBootWelcomeVisible = shouldPlayBootWelcome,
          welcomeTriggerId = if (initialSettings.welcomePopupEnabled) 1L else 0L,
        )
      }
    )
  val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

  init {
    refreshApps()
    refreshWeather(forceRefresh = false)
    observeMusicSessions()
    observeShizukuStatus()
    refreshQuickSettings()
    syncSelectedWallpaperIfNeeded(force = false)
  }

  private fun observeShizukuStatus() {
    viewModelScope.launch {
      ShizukuBridge.connectionState.collect { connState ->
        _uiState.update { state ->
          state.copy(
            quickSettingsState = state.quickSettingsState.copy(shizukuState = connState)
          )
        }
      }
    }
  }

  private fun syncSelectedWallpaperIfNeeded(force: Boolean) {
    val settings = _uiState.value.settings
    if (settings.wallpaperChoice == WallpaperChoice.SYSTEM) return
    val syncKey =
      if (settings.wallpaperChoice == WallpaperChoice.CUSTOM) {
        "custom:${settings.selectedCustomWallpaperPath.orEmpty()}"
      } else {
        "bundled:${settings.wallpaperChoice.key}"
      }
    if (!force && repository.getSyncedSystemWallpaperKey() == syncKey) return

    viewModelScope.launch {
      val ok = repository.applySystemWallpaper(settings.wallpaperChoice, settings.selectedCustomWallpaperPath)
      if (ok) {
        repository.setSyncedSystemWallpaperKey(syncKey)
      }
    }
  }

  private fun observeMusicSessions() {
    musicRepository.startOrRefreshObserving()
    viewModelScope.launch {
      combine(
        musicRepository.currentTrack,
        musicRepository.requiresNotificationAccess,
      ) { track, requiresAccess ->
        track to requiresAccess
      }.collect { (track, requiresAccess) ->
        val trackChanged = track?.trackCacheKey != lastLyricsTrackKey
        _uiState.update { state ->
          state.copy(
            musicState =
              state.musicState.copy(
                activeTrack = track,
                requiresNotificationAccess = requiresAccess,
                lyricsState =
                  when {
                    track == null -> LyricsState.Idle
                    trackChanged -> LyricsState.Idle
                    else -> state.musicState.lyricsState
                  },
              )
          )
        }
        if (track == null) {
          lastLyricsTrackKey = null
          lyricsFetchJob?.cancel()
        } else if (trackChanged) {
          lastLyricsTrackKey = track.trackCacheKey
          val currentMusic = _uiState.value.musicState
          if (currentMusic.isLyricsExpandedInline || currentMusic.isFullLyricsSheetOpen) {
            fetchLyricsForTrack(track)
          }
        }
      }
    }
  }

  private fun fetchLyricsForTrack(track: MusicPlaybackInfo) {
    lyricsFetchJob?.cancel()
    val targetKey = track.trackCacheKey
    _uiState.update { it.copy(musicState = it.musicState.copy(lyricsState = LyricsState.Loading)) }
    lyricsFetchJob =
      viewModelScope.launch {
        val result = musicRepository.fetchLyrics(track)
        if (_uiState.value.musicState.activeTrack?.trackCacheKey == targetKey) {
          _uiState.update { it.copy(musicState = it.musicState.copy(lyricsState = result)) }
        }
      }
  }

  override fun onCleared() {
    super.onCleared()
    musicRepository.stopObserving()
  }

  fun refreshApps() {
    viewModelScope.launch {
      val installed = repository.loadInstalledApps()
      repository.pruneCache(installed.map { it.componentKey }.toSet())

      val currentSettings = _uiState.value.settings
      val hiddenKeys = currentSettings.hiddenComponentKeys
      val visibleApps = installed.filterNot { it.componentKey in hiddenKeys }

      val indexMap = buildAlphabetIndexMap(visibleApps)
      val letters = indexMap.keys.toList()

      _uiState.update { state ->
        val (results, isSecret) =
          computeSearchMatch(
            installedApps = installed,
            visibleApps = visibleApps,
            hiddenKeys = state.settings.hiddenComponentKeys,
            hiddenAppsCode = state.settings.hiddenAppsCode,
            query = state.searchQuery,
          )
        state.copy(
          isLoading = false,
          allInstalledApps = installed,
          allApps = visibleApps,
          alphabetIndexMap = indexMap,
          availableLetters = letters,
          searchResults = results,
          isSecretVaultMatched = isSecret,
        )
      }
    }
  }

  fun refreshWeather(forceRefresh: Boolean = false) {
    if (!weatherRepository.hasLocationPermission()) {
      _uiState.update { it.copy(isLocationGranted = false, weatherState = WeatherUiState.NeedsPermission) }
      return
    }

    val cached = weatherRepository.loadCachedWeather()
    _uiState.update { state ->
      state.copy(
        isLocationGranted = true,
        weatherState =
          if (cached != null) WeatherUiState.Ready(cached)
          else WeatherUiState.Loading,
      )
    }

    viewModelScope.launch {
      val fresh = weatherRepository.fetchWeather(forceRefresh = forceRefresh)
      _uiState.update { state ->
        state.copy(
          weatherState =
            when {
              !weatherRepository.hasLocationPermission() -> WeatherUiState.NeedsPermission
              fresh != null -> WeatherUiState.Ready(fresh)
              cached != null -> WeatherUiState.Ready(cached)
              else -> WeatherUiState.Unavailable
            }
        )
      }
    }
  }

  fun refreshPermissionStatuses(
    postNotificationsGranted: Boolean,
    notificationAccessGranted: Boolean,
    overlayPermissionGranted: Boolean,
    locationGranted: Boolean,
  ) {
    val wasLocationGranted = _uiState.value.isLocationGranted
    _uiState.update {
      it.copy(
        isPostNotificationsGranted = postNotificationsGranted,
        isNotificationAccessGranted = notificationAccessGranted,
        isOverlayPermissionGranted = overlayPermissionGranted,
        isLocationGranted = locationGranted,
        isDefaultHome = repository.isDefaultHomeLauncher(),
      )
    }
    ShizukuBridge.refreshStatus()
    musicRepository.startOrRefreshObserving()
    if (locationGranted && (!wasLocationGranted || _uiState.value.weatherState !is WeatherUiState.Ready)) {
      refreshWeather(forceRefresh = true)
    } else if (!locationGranted) {
      _uiState.update { it.copy(weatherState = WeatherUiState.NeedsPermission) }
    }
  }

  fun onLauncherResumed() {
    musicRepository.startOrRefreshObserving()
    val refreshedSettings = repository.loadSettings()
    if (refreshedSettings != _uiState.value.settings) {
      _uiState.update { it.copy(settings = refreshedSettings) }
    }
    refreshApps()
    if (refreshedSettings.bootWelcomeEnabled && repository.consumePendingBootWelcome()) {
      SLauncherApp.hasShownBootWelcomeInProcess = true
      _uiState.update { it.copy(isBootWelcomeVisible = true) }
    }
    if (DynamicNotificationStore.consumePhoneUnlocked()) {
      triggerWelcomePopup()
    }
  }

  fun onBootIntentReceived() {
    if (_uiState.value.settings.bootWelcomeEnabled) {
      repository.consumePendingBootWelcome()
      SLauncherApp.hasShownBootWelcomeInProcess = true
      _uiState.update { it.copy(isBootWelcomeVisible = true) }
    }
  }

  fun dismissBootWelcome() {
    _uiState.update { it.copy(isBootWelcomeVisible = false) }
  }

  fun triggerWelcomePopup() {
    if (_uiState.value.settings.welcomePopupEnabled) {
      _uiState.update { it.copy(welcomeTriggerId = it.welcomeTriggerId + 1L) }
    }
  }

  // Music Player & Lyrics Actions
  fun toggleMusicPlayPause() = musicRepository.togglePlayPause()

  fun skipMusicPrevious() = musicRepository.skipToPrevious()

  fun skipMusicNext() = musicRepository.skipToNext()

  fun seekMusicTo(positionMs: Long) = musicRepository.seekTo(positionMs)

  fun openMusicPlayerApp(packageName: String) {
    musicRepository.openPlayerApp(packageName)
  }

  fun toggleInlineLyrics() {
    val nextExpanded = !_uiState.value.musicState.isLyricsExpandedInline
    _uiState.update {
      it.copy(musicState = it.musicState.copy(isLyricsExpandedInline = nextExpanded))
    }
    val track = _uiState.value.musicState.activeTrack
    val currentLyrics = _uiState.value.musicState.lyricsState
    if (nextExpanded && track != null && currentLyrics is LyricsState.Idle) {
      fetchLyricsForTrack(track)
    }
  }

  fun openFullLyricsSheet() {
    _uiState.update {
      it.copy(musicState = it.musicState.copy(isFullLyricsSheetOpen = true))
    }
    val track = _uiState.value.musicState.activeTrack
    val currentLyrics = _uiState.value.musicState.lyricsState
    if (track != null && currentLyrics is LyricsState.Idle) {
      fetchLyricsForTrack(track)
    }
  }

  fun closeFullLyricsSheet() {
    _uiState.update {
      it.copy(musicState = it.musicState.copy(isFullLyricsSheetOpen = false))
    }
  }

  fun onSearchQueryChange(query: String) {
    _uiState.update { state ->
      val (results, isSecret) =
        computeSearchMatch(
          installedApps = state.allInstalledApps,
          visibleApps = state.allApps,
          hiddenKeys = state.settings.hiddenComponentKeys,
          hiddenAppsCode = state.settings.hiddenAppsCode,
          query = query,
        )
      state.copy(
        searchQuery = query,
        searchResults = results,
        isSecretVaultMatched = isSecret,
      )
    }
  }

  fun clearSearch() {
    onSearchQueryChange("")
  }

  fun openDrawer() {
    _uiState.update {
      it.copy(isDrawerOpen = true, isSettingsOpen = false, isQuickSettingsOpen = false)
    }
  }

  fun closeDrawer() {
    _uiState.update { it.copy(isDrawerOpen = false) }
  }

  fun openQuickSettings() {
    refreshQuickSettings()
    _uiState.update {
      it.copy(isQuickSettingsOpen = true, isDrawerOpen = false, isSettingsOpen = false)
    }
  }

  fun closeQuickSettings() {
    _uiState.update { it.copy(isQuickSettingsOpen = false) }
  }

  fun refreshQuickSettings() {
    viewModelScope.launch {
      val snapshot = systemControlRepository.readQuickSettingsSnapshot()
      _uiState.update { it.copy(quickSettingsState = snapshot) }
    }
  }

  fun toggleWifi() {
    val current = _uiState.value.quickSettingsState.wifiEnabled
    viewModelScope.launch {
      val toggled = systemControlRepository.toggleWifi(current)
      if (toggled) {
        _uiState.update {
          it.copy(quickSettingsState = it.quickSettingsState.copy(wifiEnabled = !current))
        }
      }
      refreshQuickSettings()
    }
  }

  fun toggleMobileData() {
    val current = _uiState.value.quickSettingsState.mobileDataEnabled
    viewModelScope.launch {
      val toggled = systemControlRepository.toggleMobileData(current)
      if (toggled) {
        _uiState.update {
          it.copy(quickSettingsState = it.quickSettingsState.copy(mobileDataEnabled = !current))
        }
      }
      refreshQuickSettings()
    }
  }

  fun toggleBluetooth() {
    val current = _uiState.value.quickSettingsState.bluetoothEnabled
    viewModelScope.launch {
      val toggled = systemControlRepository.toggleBluetooth(current)
      if (toggled) {
        _uiState.update {
          it.copy(quickSettingsState = it.quickSettingsState.copy(bluetoothEnabled = !current))
        }
      }
      refreshQuickSettings()
    }
  }

  fun toggleFlashlight() {
    val ok = systemControlRepository.toggleFlashlight()
    if (ok) {
      _uiState.update {
        it.copy(
          quickSettingsState =
            it.quickSettingsState.copy(flashlightEnabled = !it.quickSettingsState.flashlightEnabled)
        )
      }
    }
  }

  fun toggleDarkMode() {
    val current = _uiState.value.quickSettingsState.darkModeEnabled
    viewModelScope.launch {
      val toggled = systemControlRepository.toggleDarkMode(current)
      if (toggled) {
        _uiState.update {
          it.copy(quickSettingsState = it.quickSettingsState.copy(darkModeEnabled = !current))
        }
      }
      refreshQuickSettings()
    }
  }

  fun toggleAutoRotate() {
    val current = _uiState.value.quickSettingsState.autoRotateEnabled
    viewModelScope.launch {
      val toggled = systemControlRepository.toggleAutoRotate(current)
      if (toggled) {
        _uiState.update {
          it.copy(quickSettingsState = it.quickSettingsState.copy(autoRotateEnabled = !current))
        }
      }
      refreshQuickSettings()
    }
  }

  fun setScreenBrightness(level0To255: Int) {
    _uiState.update {
      it.copy(quickSettingsState = it.quickSettingsState.copy(brightnessLevel = level0To255))
    }
    viewModelScope.launch { systemControlRepository.setBrightness(level0To255) }
  }

  fun setMediaVolumePercent(percent: Int) {
    val updated = systemControlRepository.setMediaVolumePercent(percent)
    _uiState.update {
      it.copy(quickSettingsState = it.quickSettingsState.copy(mediaVolumePercent = updated))
    }
  }

  fun connectShizuku(context: Context) {
    ShizukuBridge.requestPermissionOrOpenManager(context)
    refreshQuickSettings()
  }

  fun autoGrantPrivilegesViaShizuku(onResult: (Boolean) -> Unit) {
    viewModelScope.launch {
      val ok = systemControlRepository.autoGrantPrivilegedPermissionsViaShizuku()
      refreshQuickSettings()
      onResult(ok)
    }
  }

  fun triggerLockDevice(openCustomLockActivity: () -> Unit) {
    val useRealLock = _uiState.value.settings.shizukuRealLockEnabled
    closeQuickSettings()
    if (useRealLock && ShizukuBridge.isConnected()) {
      viewModelScope.launch {
        val locked = systemControlRepository.lockScreenViaShizuku()
        if (!locked) openCustomLockActivity()
      }
    } else {
      openCustomLockActivity()
    }
  }

  fun openHiddenAppsDialog() {
    _uiState.update { it.copy(isHiddenAppsDialogOpen = true) }
  }

  fun closeHiddenAppsDialog() {
    _uiState.update { it.copy(isHiddenAppsDialogOpen = false) }
  }

  fun openDebugConsole() {
    _uiState.update { it.copy(isDebugConsoleOpen = true) }
    refreshDiagnostics()
  }

  fun closeDebugConsole() {
    _uiState.update { it.copy(isDebugConsoleOpen = false) }
  }

  fun refreshDiagnostics() {
    viewModelScope.launch {
      val snapshot = systemControlRepository.collectSystemDiagnostics(_uiState.value.isDefaultHome)
      _uiState.update { it.copy(diagnosticsSnapshot = snapshot) }
    }
  }

  fun forceRebindShizuku(onResult: (ShizukuCommandResult) -> Unit = {}) {
    viewModelScope.launch {
      val result = systemControlRepository.forceRebindShizuku()
      refreshQuickSettings()
      val snapshot = systemControlRepository.collectSystemDiagnostics(_uiState.value.isDefaultHome)
      _uiState.update { it.copy(diagnosticsSnapshot = snapshot) }
      onResult(result)
    }
  }

  fun runDebugShellCommand(command: String, onResult: (ShizukuCommandResult) -> Unit = {}) {
    viewModelScope.launch {
      val result = systemControlRepository.runDebugShellCommand(command)
      val snapshot = systemControlRepository.collectSystemDiagnostics(_uiState.value.isDefaultHome)
      _uiState.update { it.copy(diagnosticsSnapshot = snapshot) }
      onResult(result)
    }
  }

  fun attemptDisableSystemKeyguardViaShizuku(onResult: (ShizukuCommandResult) -> Unit = {}) {
    viewModelScope.launch {
      val result = systemControlRepository.attemptDisableSystemKeyguardViaShizuku()
      val snapshot = systemControlRepository.collectSystemDiagnostics(_uiState.value.isDefaultHome)
      _uiState.update { it.copy(diagnosticsSnapshot = snapshot) }
      onResult(result)
    }
  }

  fun openSettings() {
    refreshQuickSettings()
    refreshDiagnostics()
    _uiState.update {
      it.copy(isSettingsOpen = true, isDrawerOpen = false, isQuickSettingsOpen = false)
    }
  }

  fun closeSettings() {
    _uiState.update { it.copy(isSettingsOpen = false) }
  }

  fun openPermissionsDialog() {
    _uiState.update { it.copy(isPermissionsDialogOpen = true) }
  }

  fun closePermissionsDialog(markPromptShown: Boolean = true) {
    if (markPromptShown && !_uiState.value.settings.permissionsPromptShown) {
      updateSettings { it.copy(permissionsPromptShown = true) }
    }
    _uiState.update { it.copy(isPermissionsDialogOpen = false) }
  }

  fun setLanguage(language: AppLanguage) {
    updateSettings { it.copy(language = language) }
  }

  fun setHomeAppsLayout(layout: HomeAppsLayout) {
    updateSettings { it.copy(homeAppsLayout = layout) }
  }

  fun setWallpaperChoice(choice: WallpaperChoice) {
    updateSettings { it.copy(wallpaperChoice = choice) }
    if (choice != WallpaperChoice.SYSTEM) {
      syncSelectedWallpaperIfNeeded(force = true)
    }
  }

  fun selectCustomWallpaper(path: String) {
    updateSettings {
      it.copy(
        wallpaperChoice = WallpaperChoice.CUSTOM,
        selectedCustomWallpaperPath = path,
      )
    }
    syncSelectedWallpaperIfNeeded(force = true)
  }

  fun importCustomWallpaper(uri: Uri, onResult: (Boolean) -> Unit) {
    viewModelScope.launch {
      val savedPath = repository.importCustomWallpaper(uri)
      if (savedPath != null) {
        updateSettings { current ->
          val updatedList = (listOf(savedPath) + current.customWallpaperPaths).distinct().take(12)
          current.copy(
            wallpaperChoice = WallpaperChoice.CUSTOM,
            customWallpaperPaths = updatedList,
            selectedCustomWallpaperPath = savedPath,
          )
        }
        syncSelectedWallpaperIfNeeded(force = true)
        onResult(true)
      } else {
        onResult(false)
      }
    }
  }

  fun removeCustomWallpaper(path: String) {
    repository.deleteCustomWallpaper(path)
    updateSettings { current ->
      val remaining = current.customWallpaperPaths.filterNot { it == path }
      val nextSelected =
        if (current.selectedCustomWallpaperPath == path) remaining.firstOrNull()
        else current.selectedCustomWallpaperPath
      val nextChoice =
        if (current.wallpaperChoice == WallpaperChoice.CUSTOM && nextSelected == null) {
          WallpaperChoice.ALPINE_PEAKS
        } else {
          current.wallpaperChoice
        }
      current.copy(
        wallpaperChoice = nextChoice,
        customWallpaperPaths = remaining,
        selectedCustomWallpaperPath = nextSelected,
      )
    }
  }

  fun applyWallpaperToSystem(
    choice: WallpaperChoice,
    customPath: String?,
    onResult: (Boolean) -> Unit,
  ) {
    viewModelScope.launch {
      val applied = repository.applySystemWallpaper(choice, customPath)
      onResult(applied)
    }
  }

  fun setWelcomePopupEnabled(enabled: Boolean) {
    updateSettings { it.copy(welcomePopupEnabled = enabled) }
    if (enabled) triggerWelcomePopup()
  }

  fun setCustomWelcomeText(text: String) {
    updateSettings { it.copy(customWelcomeText = text) }
  }

  fun setLockScreenEnabled(enabled: Boolean) {
    updateSettings { it.copy(lockScreenEnabled = enabled) }
    DebugLogStore.log("LockScreen", "Custom Lock Screen ${if (enabled) "enabled" else "disabled"}")
  }

  fun setLockAuthMode(mode: LockAuthMode) {
    updateSettings {
      it.copy(
        lockAuthMode = mode,
        biometricUnlockEnabled = mode.usesBiometric,
      )
    }
    DebugLogStore.log("LockScreen", "Auth mode updated -> ${mode.key}")
  }

  fun saveLockPin(pin: String): Boolean {
    val saved = repository.setLockPin(pin)
    if (saved) {
      updateSettings { it.copy(hasLockPinConfigured = true) }
      DebugLogStore.log("LockScreen", "Numeric PIN configured")
    }
    return saved
  }

  fun clearLockPin() {
    repository.clearLockPin()
    updateSettings { it.copy(hasLockPinConfigured = false) }
    DebugLogStore.log("LockScreen", "Numeric PIN removed")
  }

  fun setBiometricUnlockEnabled(enabled: Boolean) {
    updateSettings { current ->
      val updatedMode =
        if (enabled) {
          if (current.lockAuthMode.usesPin) LockAuthMode.BIOMETRIC_AND_PIN
          else LockAuthMode.BIOMETRIC_ONLY
        } else {
          if (current.lockAuthMode.usesPin) LockAuthMode.PIN_ONLY
          else LockAuthMode.SWIPE_ONLY
        }
      current.copy(biometricUnlockEnabled = enabled, lockAuthMode = updatedMode)
    }
  }

  fun setBiometricAutoPrompt(enabled: Boolean) {
    updateSettings { it.copy(biometricAutoPrompt = enabled) }
  }

  fun setShizukuRealLockEnabled(enabled: Boolean) {
    updateSettings { it.copy(shizukuRealLockEnabled = enabled) }
  }

  fun setDynamicNotificationsEnabled(enabled: Boolean) {
    updateSettings { it.copy(dynamicNotificationsEnabled = enabled) }
  }

  fun setFloatingOverlayPopupEnabled(enabled: Boolean) {
    updateSettings { it.copy(floatingOverlayPopupEnabled = enabled) }
  }

  fun setSuppressSystemHeadsUp(suppress: Boolean, onResult: (Boolean) -> Unit = {}) {
    updateSettings { it.copy(suppressSystemHeadsUp = suppress) }
    viewModelScope.launch {
      val ok = systemControlRepository.setHeadsUpSuppressed(suppress)
      refreshQuickSettings()
      onResult(ok)
    }
  }

  fun setAppIconOpensSettingsOnly(enabled: Boolean) {
    updateSettings { it.copy(appIconOpensSettingsOnly = enabled) }
  }

  fun setBootWelcomeEnabled(enabled: Boolean) {
    updateSettings { it.copy(bootWelcomeEnabled = enabled) }
  }

  fun setHiddenAppsCode(code: String) {
    updateSettings { it.copy(hiddenAppsCode = code) }
    _uiState.update { state ->
      val (results, isSecret) =
        computeSearchMatch(
          installedApps = state.allInstalledApps,
          visibleApps = state.allApps,
          hiddenKeys = state.settings.hiddenComponentKeys,
          hiddenAppsCode = code,
          query = state.searchQuery,
        )
      state.copy(searchResults = results, isSecretVaultMatched = isSecret)
    }
  }

  fun hideApp(app: AppModel) {
    val updatedKeys = _uiState.value.settings.hiddenComponentKeys + app.componentKey
    saveHiddenAppsSelection(updatedKeys)
  }

  fun unhideApp(app: AppModel) {
    val updatedKeys = _uiState.value.settings.hiddenComponentKeys - app.componentKey
    saveHiddenAppsSelection(updatedKeys)
  }

  fun saveHiddenAppsSelection(hiddenKeys: Set<String>) {
    updateSettings { it.copy(hiddenComponentKeys = hiddenKeys) }
    val installed = _uiState.value.allInstalledApps
    val visibleApps = installed.filterNot { it.componentKey in hiddenKeys }
    val indexMap = buildAlphabetIndexMap(visibleApps)

    _uiState.update { state ->
      val (results, isSecret) =
        computeSearchMatch(
          installedApps = installed,
          visibleApps = visibleApps,
          hiddenKeys = hiddenKeys,
          hiddenAppsCode = state.settings.hiddenAppsCode,
          query = state.searchQuery,
        )
      state.copy(
        allApps = visibleApps,
        alphabetIndexMap = indexMap,
        availableLetters = indexMap.keys.toList(),
        searchResults = results,
        isSecretVaultMatched = isSecret,
        isHiddenAppsDialogOpen = false,
      )
    }
  }

  fun openDefaultHomeSettings() {
    repository.openDefaultHomeSettings()
  }

  fun openSystemSecuritySettings() {
    repository.openSystemSecuritySettings()
  }

  private fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
    val updated = transform(_uiState.value.settings)
    repository.saveSettings(updated)
    _uiState.update { it.copy(settings = updated) }
  }

  fun launchApp(app: AppModel): Boolean {
    if (app.packageName == repository.selfPackageName) {
      _uiState.update {
        it.copy(
          searchQuery = "",
          searchResults = emptyList(),
          isDrawerOpen = false,
          isSettingsOpen = true,
        )
      }
      return true
    }

    val launched = repository.launchApp(app)
    if (launched) {
      _uiState.update {
        it.copy(
          searchQuery = "",
          searchResults = emptyList(),
          isDrawerOpen = false,
          isSettingsOpen = false,
        )
      }
    }
    return launched
  }

  fun openAppInfo(app: AppModel) {
    repository.openAppInfo(app.packageName)
  }

  fun uninstallApp(app: AppModel) {
    repository.uninstallApp(app.packageName)
  }

  suspend fun loadIcon(app: AppModel, iconSizePx: Int) = repository.loadAppIcon(app, iconSizePx)

  private fun computeSearchMatch(
    installedApps: List<AppModel>,
    visibleApps: List<AppModel>,
    hiddenKeys: Set<String>,
    hiddenAppsCode: String,
    query: String,
  ): Pair<List<AppModel>, Boolean> {
    val secretCode = hiddenAppsCode.trim()
    val trimmedQuery = query.trim()
    if (secretCode.isNotEmpty() && trimmedQuery == secretCode) {
      val hiddenApps = installedApps.filter { it.componentKey in hiddenKeys }
      return hiddenApps to true
    }
    return filterApps(visibleApps, query) to false
  }

  private fun filterApps(apps: List<AppModel>, query: String): List<AppModel> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()

    val startsWithMatches = mutableListOf<AppModel>()
    val containsMatches = mutableListOf<AppModel>()
    for (app in apps) {
      if (app.label.startsWith(trimmed, ignoreCase = true)) {
        startsWithMatches.add(app)
      } else if (app.label.contains(trimmed, ignoreCase = true)) {
        containsMatches.add(app)
      }
    }
    return startsWithMatches + containsMatches
  }

  private fun buildAlphabetIndexMap(apps: List<AppModel>): Map<Char, Int> {
    val map = LinkedHashMap<Char, Int>()
    apps.forEachIndexed { index, app ->
      if (!map.containsKey(app.sectionLetter)) {
        map[app.sectionLetter] = index
      }
    }
    return map
  }

  class Factory(
    private val repository: AppRepository,
    private val weatherRepository: WeatherRepository,
    private val musicRepository: MusicRepository,
    private val systemControlRepository: SystemControlRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      if (modelClass.isAssignableFrom(LauncherViewModel::class.java)) {
        return LauncherViewModel(
          repository,
          weatherRepository,
          musicRepository,
          systemControlRepository,
        ) as T
      }
      throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
  }
}
