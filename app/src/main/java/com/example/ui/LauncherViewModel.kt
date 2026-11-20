package com.example.ui

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
import com.example.data.LyricsState
import com.example.data.MusicPlaybackInfo
import com.example.data.MusicRepository
import com.example.data.WallpaperChoice
import com.example.data.WeatherRepository
import com.example.data.WeatherUiState
import com.example.notifications.DynamicNotificationStore
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
 * selection, live weather, permissions status, and persistent settings.
 */
class LauncherViewModel(
  private val repository: AppRepository,
  private val weatherRepository: WeatherRepository,
  private val musicRepository: MusicRepository,
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

      val hiddenKeys = _uiState.value.settings.hiddenComponentKeys
      val visibleApps = installed.filterNot { it.componentKey in hiddenKeys }

      val indexMap = buildAlphabetIndexMap(visibleApps)
      val letters = indexMap.keys.toList()

      _uiState.update { state ->
        state.copy(
          isLoading = false,
          allInstalledApps = installed,
          allApps = visibleApps,
          alphabetIndexMap = indexMap,
          availableLetters = letters,
          searchResults = filterApps(visibleApps, state.searchQuery),
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
    locationGranted: Boolean,
  ) {
    val wasLocationGranted = _uiState.value.isLocationGranted
    _uiState.update {
      it.copy(
        isPostNotificationsGranted = postNotificationsGranted,
        isNotificationAccessGranted = notificationAccessGranted,
        isLocationGranted = locationGranted,
        isDefaultHome = repository.isDefaultHomeLauncher(),
      )
    }
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
      state.copy(
        searchQuery = query,
        searchResults = filterApps(state.allApps, query),
      )
    }
  }

  fun clearSearch() {
    onSearchQueryChange("")
  }

  fun openDrawer() {
    _uiState.update { it.copy(isDrawerOpen = true, isSettingsOpen = false) }
  }

  fun closeDrawer() {
    _uiState.update { it.copy(isDrawerOpen = false) }
  }

  fun openHiddenAppsDialog() {
    _uiState.update { it.copy(isHiddenAppsDialogOpen = true) }
  }

  fun closeHiddenAppsDialog() {
    _uiState.update { it.copy(isHiddenAppsDialogOpen = false) }
  }

  fun openSettings() {
    _uiState.update { it.copy(isSettingsOpen = true, isDrawerOpen = false) }
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
  }

  fun selectCustomWallpaper(path: String) {
    updateSettings {
      it.copy(
        wallpaperChoice = WallpaperChoice.CUSTOM,
        selectedCustomWallpaperPath = path,
      )
    }
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
  }

  fun setDynamicNotificationsEnabled(enabled: Boolean) {
    updateSettings { it.copy(dynamicNotificationsEnabled = enabled) }
  }

  fun setAppIconOpensSettingsOnly(enabled: Boolean) {
    updateSettings { it.copy(appIconOpensSettingsOnly = enabled) }
  }

  fun setBootWelcomeEnabled(enabled: Boolean) {
    updateSettings { it.copy(bootWelcomeEnabled = enabled) }
  }

  fun saveHiddenAppsSelection(hiddenKeys: Set<String>) {
    updateSettings { it.copy(hiddenComponentKeys = hiddenKeys) }
    val installed = _uiState.value.allInstalledApps
    val visibleApps = installed.filterNot { it.componentKey in hiddenKeys }
    val indexMap = buildAlphabetIndexMap(visibleApps)

    _uiState.update { state ->
      state.copy(
        allApps = visibleApps,
        alphabetIndexMap = indexMap,
        availableLetters = indexMap.keys.toList(),
        searchResults = filterApps(visibleApps, state.searchQuery),
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

  suspend fun loadIcon(app: AppModel, iconSizePx: Int) = repository.loadAppIcon(app, iconSizePx)

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
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      if (modelClass.isAssignableFrom(LauncherViewModel::class.java)) {
        return LauncherViewModel(repository, weatherRepository, musicRepository) as T
      }
      throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
  }
}
