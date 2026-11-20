package com.example.data

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import com.example.R

/**
 * Lightweight immutable representation of a launchable installed application.
 * Icons are intentionally excluded from this model and loaded lazily via a bounded LruCache
 * to keep memory overhead minimal on 2GB RAM devices.
 */
data class AppModel(
  val packageName: String,
  val activityName: String,
  val label: String,
  val sectionLetter: Char,
) {
  val componentKey: String
    get() = "$packageName/$activityName"
}

/**
 * Live battery state updated via lifecycle-scoped BroadcastReceiver.
 */
data class BatteryStatus(
  val levelPercent: Int = 100,
  val isCharging: Boolean = false,
)

data class LyricLine(
  val timeMs: Long,
  val text: String,
)

sealed interface LyricsState {
  data object Idle : LyricsState
  data object Loading : LyricsState
  data class Synced(val lines: List<LyricLine>) : LyricsState
  data class Plain(val lines: List<String>) : LyricsState
  data object NotFound : LyricsState
}

data class MusicPlaybackInfo(
  val packageName: String,
  val appName: String,
  val title: String,
  val artist: String,
  val album: String,
  val isPlaying: Boolean,
  val durationMs: Long,
  val positionMs: Long,
  val positionUpdatedAtElapsedMs: Long,
  val artwork: ImageBitmap? = null,
  val embeddedLyrics: String? = null,
) {
  val trackCacheKey: String
    get() = "${artist.trim().lowercase()}|${title.trim().lowercase()}"
}

data class MusicUiState(
  val activeTrack: MusicPlaybackInfo? = null,
  val requiresNotificationAccess: Boolean = false,
  val lyricsState: LyricsState = LyricsState.Idle,
  val isLyricsExpandedInline: Boolean = false,
  val isFullLyricsSheetOpen: Boolean = false,
)

enum class WeatherCondition(val key: String) {
  SUNNY("sunny"),
  PARTLY_CLOUDY("partly_cloudy"),
  CLOUDY("cloudy"),
  DRIZZLE("drizzle"),
  RAIN("rain"),
  THUNDERSTORM("thunderstorm");

  companion object {
    fun fromKey(key: String?): WeatherCondition =
      entries.firstOrNull { it.key == key } ?: PARTLY_CLOUDY

    fun fromWmoCode(code: Int): WeatherCondition =
      when (code) {
        0, 1 -> SUNNY
        2 -> PARTLY_CLOUDY
        3, 45, 48 -> CLOUDY
        51, 53, 55, 56, 57 -> DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> RAIN
        95, 96, 99 -> THUNDERSTORM
        else -> CLOUDY
      }
  }
}

data class WeatherData(
  val locationLabel: String,
  val temperatureCelsius: Int,
  val condition: WeatherCondition,
  val isDay: Boolean = true,
  val updatedAtMillis: Long = System.currentTimeMillis(),
)

sealed interface WeatherUiState {
  data object NeedsPermission : WeatherUiState
  data object Loading : WeatherUiState
  data class Ready(val data: WeatherData) : WeatherUiState
  data object Unavailable : WeatherUiState
}

enum class AppLanguage(val tag: String) {
  SYSTEM("system"),
  INDONESIAN("in"),
  ENGLISH("en");

  companion object {
    fun fromTag(tag: String?): AppLanguage =
      entries.firstOrNull { it.tag == tag } ?: SYSTEM
  }
}

enum class HomeAppsLayout(val key: String) {
  GRID("grid"),
  LIST("list");

  companion object {
    fun fromKey(key: String?): HomeAppsLayout =
      entries.firstOrNull { it.key == key } ?: GRID
  }
}

enum class WallpaperChoice(val key: String, @param:DrawableRes val drawableRes: Int?) {
  SYSTEM("system", null),
  CUSTOM("custom", null),
  ALPINE_PEAKS("alpine_peaks", R.drawable.wallpaper_alpine_peaks),
  OCEAN_SHELLS("ocean_shells", R.drawable.wallpaper_ocean_shells),
  TWILIGHT_BRANCHES("twilight_branches", R.drawable.wallpaper_twilight_branches),
  SUNLIT_CEDAR("sunlit_cedar", R.drawable.wallpaper_sunlit_cedar),
  PASTEL_SHORE("pastel_shore", R.drawable.wallpaper_pastel_shore),
  STARRY_PINES("starry_pines", R.drawable.wallpaper_starry_pines),
  COBALT_OAK("cobalt_oak", R.drawable.wallpaper_cobalt_oak),
  TROPICAL_PALM("tropical_palm", R.drawable.wallpaper_tropical_palm),
  MISTY_FOREST("misty_forest", R.drawable.wallpaper_misty_forest);

  companion object {
    fun fromKey(key: String?): WallpaperChoice =
      entries.firstOrNull { it.key == key } ?: ALPINE_PEAKS
  }
}

/**
 * Persistent user customization settings stored in lightweight SharedPreferences.
 */
data class LauncherSettings(
  val language: AppLanguage = AppLanguage.SYSTEM,
  val homeAppsLayout: HomeAppsLayout = HomeAppsLayout.GRID,
  val wallpaperChoice: WallpaperChoice = WallpaperChoice.ALPINE_PEAKS,
  val customWallpaperPaths: List<String> = emptyList(),
  val selectedCustomWallpaperPath: String? = null,
  val welcomePopupEnabled: Boolean = true,
  val customWelcomeText: String = "",
  val lockScreenEnabled: Boolean = false,
  val dynamicNotificationsEnabled: Boolean = true,
  val appIconOpensSettingsOnly: Boolean = true,
  val bootWelcomeEnabled: Boolean = true,
  val permissionsPromptShown: Boolean = false,
  val hiddenComponentKeys: Set<String> = emptySet(),
)

/**
 * Complete UI state observed by the SLauncher 6 Compose hierarchy.
 */
data class LauncherUiState(
  val isLoading: Boolean = true,
  val allInstalledApps: List<AppModel> = emptyList(),
  val allApps: List<AppModel> = emptyList(),
  val searchQuery: String = "",
  val searchResults: List<AppModel> = emptyList(),
  val alphabetIndexMap: Map<Char, Int> = emptyMap(),
  val availableLetters: List<Char> = emptyList(),
  val isDrawerOpen: Boolean = false,
  val isHiddenAppsDialogOpen: Boolean = false,
  val isSettingsOpen: Boolean = false,
  val isPermissionsDialogOpen: Boolean = false,
  val isBootWelcomeVisible: Boolean = false,
  val settings: LauncherSettings = LauncherSettings(),
  val isPostNotificationsGranted: Boolean = true,
  val isNotificationAccessGranted: Boolean = false,
  val isLocationGranted: Boolean = false,
  val isDefaultHome: Boolean = false,
  val weatherState: WeatherUiState = WeatherUiState.NeedsPermission,
  val musicState: MusicUiState = MusicUiState(),
  val welcomeTriggerId: Long = 0L,
)
