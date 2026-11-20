package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppModel
import com.example.data.HomeAppsLayout
import com.example.data.LauncherUiState
import com.example.notifications.DynamicNotificationItem
import com.example.ui.theme.SLauncherColors
import com.example.ui.theme.WallpaperLegibilityShadow

/**
 * Glassmorphism-polished Home Screen layered cleanly over the selected wallpaper:
 * - Top: Liquid-droplet Dynamic Pop-Up capsule + top-right Lock & SLauncher 6 Logo Settings button
 * - Header: Digital Clock + Date + Battery on the left, live Weather & Kota/Kecamatan on the right
 * - Center: Floating Glassmorphism Spotify-style Music & Lyrics Card followed by all visible apps
 * - Bottom (thumb reach): Floating Inline Search results + Glassmorphism Search capsule
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
  uiState: LauncherUiState,
  notifications: List<DynamicNotificationItem>,
  loadIcon: suspend (AppModel, Int) -> ImageBitmap?,
  onLaunchApp: (AppModel) -> Unit,
  onOpenAppInfo: (AppModel) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onClearSearch: () -> Unit,
  onOpenDrawer: () -> Unit,
  onOpenQuickSettings: () -> Unit,
  onOpenSettings: () -> Unit,
  onPreviewLockScreen: () -> Unit,
  onRequestLocationPermission: () -> Unit,
  onRefreshWeather: () -> Unit,
  onRequestNotificationAccess: () -> Unit,
  onOpenNotification: (DynamicNotificationItem) -> Unit,
  onDismissNotification: (DynamicNotificationItem) -> Unit,
  onMusicPlayPause: () -> Unit,
  onMusicSkipPrevious: () -> Unit,
  onMusicSkipNext: () -> Unit,
  onMusicSeekTo: (Long) -> Unit,
  onOpenMusicPlayerApp: (String) -> Unit,
  onToggleInlineLyrics: () -> Unit,
  onOpenFullLyrics: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var dragAccumulator by remember { mutableFloatStateOf(0f) }

  val defaultWelcome = stringResource(R.string.default_welcome_message)
  val resolvedWelcomeMessage =
    uiState.settings.customWelcomeText.trim().ifEmpty { defaultWelcome }

  val ambientScrim =
    Brush.verticalGradient(
      0.0f to Color.Black.copy(alpha = 0.24f),
      0.24f to Color.Black.copy(alpha = 0.06f),
      0.76f to Color.Black.copy(alpha = 0.10f),
      1.0f to Color.Black.copy(alpha = 0.34f),
    )

  Box(
    contentAlignment = Alignment.TopCenter,
    modifier =
      modifier
        .fillMaxSize()
        .background(ambientScrim)
        .pointerInput(Unit) {
          detectVerticalDragGestures(
            onDragStart = { dragAccumulator = 0f },
            onVerticalDrag = { _, dragAmount ->
              dragAccumulator += dragAmount
              if (dragAccumulator < -140f) {
                dragAccumulator = 0f
                onOpenDrawer()
              } else if (dragAccumulator > 110f) {
                dragAccumulator = 0f
                onOpenQuickSettings()
              }
            },
          )
        }
        .statusBarsPadding()
        .navigationBarsPadding()
        .imePadding(),
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .widthIn(max = 640.dp)
          .padding(horizontal = 20.dp),
    ) {
      // 1. Dynamic Welcome & Notification Capsule (with bouncy liquid droplet exit)
      DynamicPopUpCapsule(
        welcomePopupEnabled = uiState.settings.welcomePopupEnabled,
        welcomeMessage = resolvedWelcomeMessage,
        welcomeTriggerId = uiState.welcomeTriggerId,
        dynamicNotificationsEnabled = uiState.settings.dynamicNotificationsEnabled,
        isNotificationAccessGranted = uiState.isNotificationAccessGranted,
        notifications = notifications,
        onRequestNotificationAccess = onRequestNotificationAccess,
        onOpenNotification = onOpenNotification,
        onDismissNotification = onDismissNotification,
        modifier = Modifier.padding(top = 8.dp),
      )

      // 2. Top-Right Utility Row: Quick Settings + Lock Screen + SLauncher 6 Logo Settings
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
      ) {
        IconButton(
          onClick = onOpenQuickSettings,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .size(40.dp)
              .clip(CircleShape)
              .background(SLauncherColors.GlassSurfaceGradient)
              .border(1.dp, SLauncherColors.GlassBorderGradient, CircleShape)
              .testTag("home_quick_settings_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = stringResource(R.string.cd_quick_settings),
            tint = SLauncherColors.TextPrimary,
            modifier = Modifier.size(18.dp),
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
          onClick = onPreviewLockScreen,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .size(40.dp)
              .clip(CircleShape)
              .background(SLauncherColors.GlassSurfaceGradient)
              .border(1.dp, SLauncherColors.GlassBorderGradient, CircleShape)
              .testTag("home_lockscreen_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = stringResource(R.string.cd_lock_screen),
            tint = SLauncherColors.TextPrimary,
            modifier = Modifier.size(18.dp),
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // SLauncher 6 Logo Button for Launcher Settings
        IconButton(
          onClick = onOpenSettings,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .size(42.dp)
              .clip(CircleShape)
              .background(SLauncherColors.GlassFloatingCardGradient)
              .border(1.2.dp, SLauncherColors.AccentPrimary.copy(alpha = 0.65f), CircleShape)
              .testTag("home_settings_button"),
        ) {
          Image(
            painter = painterResource(id = R.drawable.slauncher_icon_fg_1795168930289),
            contentDescription = stringResource(R.string.cd_open_settings),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(30.dp).clip(CircleShape),
          )
        }
      }

      // 3. Digital Clock + Date/Battery (left) & Weather + Kota, Kecamatan (right)
      ClockDateBatteryHeader(
        weatherState = uiState.weatherState,
        onRequestLocationPermission = onRequestLocationPermission,
        onRefreshWeather = onRefreshWeather,
        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
      )

      // 4. Scrollable Main Area: Spotify-Style Music & Lyrics Card + Many Apps Grid/List
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 80.dp),
        contentPadding = PaddingValues(top = 2.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.weight(1f).fillMaxWidth().testTag("home_apps_grid"),
      ) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "music_player_card") {
          MusicPlayerCard(
            musicState = uiState.musicState,
            onPlayPause = onMusicPlayPause,
            onSkipPrevious = onMusicSkipPrevious,
            onSkipNext = onMusicSkipNext,
            onSeekTo = onMusicSeekTo,
            onOpenPlayerApp = onOpenMusicPlayerApp,
            onToggleInlineLyrics = onToggleInlineLyrics,
            onOpenFullLyrics = onOpenFullLyrics,
            onRequestNotificationAccess = onRequestNotificationAccess,
            modifier = Modifier.padding(bottom = 6.dp),
          )
        }

        if (uiState.isLoading) {
          item(span = { GridItemSpan(maxLineSpan) }, contentType = "loading") {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            ) {
              CircularProgressIndicator(
                color = SLauncherColors.AccentPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = stringResource(R.string.loading_apps),
                style = MaterialTheme.typography.bodyMedium,
                color = SLauncherColors.TextSecondary,
              )
            }
          }
        } else {
          // All Apps on Home Section Header
          item(span = { GridItemSpan(maxLineSpan) }, contentType = "all_apps_header") {
            Text(
              text = stringResource(R.string.home_apps_section_label, uiState.allApps.size).uppercase(),
              style =
                MaterialTheme.typography.labelSmall.copy(
                  letterSpacing = 1.2.sp,
                  shadow = WallpaperLegibilityShadow,
                ),
              color = SLauncherColors.TextSecondary,
              modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 4.dp),
            )
          }

          // Many Apps displayed directly on the Home Screen (Glassmorphic Grid or Vertical List)
          if (uiState.settings.homeAppsLayout == HomeAppsLayout.GRID) {
            items(
              items = uiState.allApps,
              key = { "home_grid_${it.componentKey}" },
              contentType = { "home_grid_app" },
            ) { app ->
              HomeAppGridTile(
                app = app,
                loadIcon = loadIcon,
                onLaunch = { onLaunchApp(app) },
                onOpenInfo = { onOpenAppInfo(app) },
              )
            }
          } else {
            items(
              items = uiState.allApps,
              key = { "home_list_${it.componentKey}" },
              span = { GridItemSpan(maxLineSpan) },
              contentType = { "home_list_app" },
            ) { app ->
              HomeAppListRow(
                app = app,
                loadIcon = loadIcon,
                onLaunch = { onLaunchApp(app) },
                onOpenInfo = { onOpenAppInfo(app) },
                testTag = "home_list_app_${app.packageName}",
              )
            }
          }
        }
      }

      // 5. Bottom Thumb Zone: Floating Inline Search Results + Glassmorphism Search Pill
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
      ) {
        InlineSearchResultsCard(
          visible = uiState.searchQuery.isNotBlank(),
          query = uiState.searchQuery,
          results = uiState.searchResults,
          loadIcon = loadIcon,
          onAppClick = onLaunchApp,
        )

        SearchBar(
          query = uiState.searchQuery,
          onQueryChange = onSearchQueryChange,
          onClearQuery = onClearSearch,
          onSubmitFirstResult = {
            uiState.searchResults.firstOrNull()?.let { onLaunchApp(it) }
          },
          onOpenDrawer = onOpenDrawer,
        )
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeAppGridTile(
  app: AppModel,
  loadIcon: suspend (AppModel, Int) -> ImageBitmap?,
  onLaunch: () -> Unit,
  onOpenInfo: () -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }
  val tileShape = RoundedCornerShape(20.dp)

  Box(contentAlignment = Alignment.Center) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier =
        Modifier.fillMaxWidth()
          .clip(tileShape)
          .background(Color.White.copy(alpha = 0.05f))
          .border(0.6.dp, Color.White.copy(alpha = 0.12f), tileShape)
          .combinedClickable(
            onClick = onLaunch,
            onLongClick = { menuExpanded = true },
          )
          .padding(horizontal = 4.dp, vertical = 10.dp)
          .testTag("home_grid_app_${app.packageName}"),
    ) {
      AppIconImage(app = app, loadIcon = loadIcon, size = 46.dp)
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = app.label,
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.5.sp,
            shadow = WallpaperLegibilityShadow,
          ),
        color = SLauncherColors.TextPrimary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    AppContextDropdown(
      expanded = menuExpanded,
      onDismiss = { menuExpanded = false },
      onOpenInfo = onOpenInfo,
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeAppListRow(
  app: AppModel,
  loadIcon: suspend (AppModel, Int) -> ImageBitmap?,
  onLaunch: () -> Unit,
  onOpenInfo: () -> Unit,
  testTag: String,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Box {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(min = 50.dp)
          .clip(RoundedCornerShape(16.dp))
          .combinedClickable(
            onClick = onLaunch,
            onLongClick = { menuExpanded = true },
          )
          .padding(horizontal = 6.dp, vertical = 6.dp)
          .testTag(testTag),
    ) {
      AppIconImage(app = app, loadIcon = loadIcon, size = 38.dp)
      Spacer(modifier = Modifier.width(14.dp))
      Text(
        text = app.label,
        style =
          MaterialTheme.typography.titleMedium.copy(
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Medium,
            shadow = WallpaperLegibilityShadow,
          ),
        color = SLauncherColors.TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
    }

    AppContextDropdown(
      expanded = menuExpanded,
      onDismiss = { menuExpanded = false },
      onOpenInfo = onOpenInfo,
    )
  }
}

@Composable
private fun AppContextDropdown(
  expanded: Boolean,
  onDismiss: () -> Unit,
  onOpenInfo: () -> Unit,
) {
  DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismiss,
    modifier = Modifier.background(SLauncherColors.DarkGlassElevated),
  ) {
    DropdownMenuItem(
      text = {
        Text(
          text = stringResource(R.string.action_app_info),
          color = SLauncherColors.TextPrimary,
        )
      },
      leadingIcon = {
        Icon(
          imageVector = Icons.Rounded.Info,
          contentDescription = null,
          tint = SLauncherColors.TextSecondary,
        )
      },
      onClick = {
        onDismiss()
        onOpenInfo()
      },
    )
  }
}
