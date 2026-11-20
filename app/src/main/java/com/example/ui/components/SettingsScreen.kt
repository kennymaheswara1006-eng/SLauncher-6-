package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.app.StatusBarManager
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.platform.LocalContext
import com.example.R
import com.example.data.AppLanguage
import com.example.data.HomeAppsLayout
import com.example.data.LauncherSettings
import com.example.data.WallpaperChoice
import com.example.quicksettings.DynamicAlertsTileService
import com.example.quicksettings.LauncherSettingsTileService
import com.example.quicksettings.LockScreenTileService
import com.example.quicksettings.SystemQuickSettingsHelper
import com.example.quicksettings.WelcomePopupTileService
import com.example.ui.theme.SLauncherColors

/**
 * Comprehensive SLauncher 6 Settings hub for configuring:
 * - Language (Follow System / Bahasa Indonesia / English)
 * - Wallpaper Picker (System Live + 3 bundled photo wallpapers + Apply to System Wallpaper)
 * - Permissions & Setup checklist
 * - Welcome Dynamic Pop-Up (custom message, default localized "Halo Selamat Datang", 3s test)
 * - Native Lock Screen (no overlay, showWhenLocked activity + system security shortcut)
 * - Dynamic Notifications (NotificationListener access + live preview trigger)
 * - Home Screen layout, favorites, and default Home launcher status
 */
@Composable
fun SettingsScreen(
  visible: Boolean,
  settings: LauncherSettings,
  isPostNotificationsGranted: Boolean,
  isLocationGranted: Boolean,
  isNotificationAccessGranted: Boolean,
  isDefaultHome: Boolean,
  onSelectLanguage: (AppLanguage) -> Unit,
  onSelectWallpaper: (WallpaperChoice) -> Unit,
  onSelectCustomWallpaper: (String) -> Unit,
  onAddCustomWallpaper: () -> Unit,
  onRemoveCustomWallpaper: (String) -> Unit,
  onApplySystemWallpaper: (WallpaperChoice, String?) -> Unit,
  onSelectHomeLayout: (HomeAppsLayout) -> Unit,
  onToggleWelcomePopup: (Boolean) -> Unit,
  onWelcomeTextChange: (String) -> Unit,
  onTestWelcomePopup: () -> Unit,
  onToggleLockScreen: (Boolean) -> Unit,
  onPreviewLockScreen: () -> Unit,
  onOpenSecuritySettings: () -> Unit,
  onToggleDynamicNotifications: (Boolean) -> Unit,
  onRequestNotificationAccess: () -> Unit,
  onTestDynamicNotification: () -> Unit,
  onToggleAppIconSettingsOnly: (Boolean) -> Unit,
  onToggleBootWelcome: (Boolean) -> Unit,
  onOpenHiddenAppsEditor: () -> Unit,
  onOpenPermissionsDialog: () -> Unit,
  onSetDefaultHome: () -> Unit,
  onClose: () -> Unit,
) {
  AnimatedVisibility(
    visible = visible,
    enter =
      slideInVertically(
        animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow),
        initialOffsetY = { it / 5 },
      ) + fadeIn(),
    exit = slideOutVertically(targetOffsetY = { it / 5 }) + fadeOut(),
  ) {
    val defaultWelcomeText = stringResource(R.string.default_welcome_message)
    val allCoreReady =
      isPostNotificationsGranted && isLocationGranted && isNotificationAccessGranted && isDefaultHome

    Box(
      contentAlignment = Alignment.TopCenter,
      modifier =
        Modifier.fillMaxSize()
          .background(SLauncherColors.DarkGlassDrawer)
          .statusBarsPadding()
          .navigationBarsPadding(),
    ) {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .widthIn(max = 600.dp)
            .padding(horizontal = 20.dp),
      ) {
        // Header
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.settings_title),
              style = MaterialTheme.typography.headlineSmall,
              color = SLauncherColors.TextPrimary,
            )
            Text(
              text = stringResource(R.string.settings_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = SLauncherColors.TextSecondary,
            )
          }
          IconButton(
            onClick = onClose,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(SLauncherColors.DarkGlassPill)
                .border(1.dp, SLauncherColors.GlassBorder, CircleShape)
                .testTag("close_settings_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(R.string.action_done),
              tint = SLauncherColors.TextPrimary,
            )
          }
        }

        Column(
          verticalArrangement = Arrangement.spacedBy(14.dp),
          modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        ) {
          // 1. Permissions & Setup Banner
          SettingsCard(
            icon = Icons.Rounded.VerifiedUser,
            title = stringResource(R.string.permissions_title),
          ) {
            Text(
              text =
                if (allCoreReady) stringResource(R.string.perm_all_ready)
                else stringResource(R.string.permissions_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color =
                if (allCoreReady) SLauncherColors.BatteryCharging
                else SLauncherColors.TextSecondary,
            )
            Button(
              onClick = onOpenPermissionsDialog,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor =
                    if (allCoreReady) SLauncherColors.AccentContainer
                    else SLauncherColors.AccentPrimary,
                  contentColor =
                    if (allCoreReady) SLauncherColors.AccentPrimary
                    else MaterialTheme.colorScheme.onPrimary,
                ),
              shape = RoundedCornerShape(50),
              modifier = Modifier.fillMaxWidth().testTag("open_permissions_dialog_button"),
            ) {
              Text(stringResource(R.string.perm_banner_button))
            }
          }

          // 2. Wallpaper Picker (Add Photo + Custom Photos + 9 Bundled Photos + System Live)
          SettingsCard(
            icon = Icons.Rounded.Wallpaper,
            title = stringResource(R.string.settings_section_wallpaper),
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
              AddCustomWallpaperTile(onClick = onAddCustomWallpaper)

              settings.customWallpaperPaths.forEachIndexed { index, path ->
                CustomWallpaperChoiceTile(
                  path = path,
                  label = stringResource(R.string.wallpaper_custom_label, index + 1),
                  selected =
                    settings.wallpaperChoice == WallpaperChoice.CUSTOM &&
                      settings.selectedCustomWallpaperPath == path,
                  onClick = { onSelectCustomWallpaper(path) },
                  onRemove = { onRemoveCustomWallpaper(path) },
                  index = index,
                )
              }

              WallpaperChoiceTile(
                choice = WallpaperChoice.ALPINE_PEAKS,
                label = stringResource(R.string.wallpaper_alpine_peaks),
                selected = settings.wallpaperChoice == WallpaperChoice.ALPINE_PEAKS,
                onClick = { onSelectWallpaper(WallpaperChoice.ALPINE_PEAKS) },
              )
              WallpaperChoiceTile(
                choice = WallpaperChoice.OCEAN_SHELLS,
                label = stringResource(R.string.wallpaper_ocean_shells),
                selected = settings.wallpaperChoice == WallpaperChoice.OCEAN_SHELLS,
                onClick = { onSelectWallpaper(WallpaperChoice.OCEAN_SHELLS) },
              )
              WallpaperChoiceTile(
                choice = WallpaperChoice.TWILIGHT_BRANCHES,
                label = stringResource(R.string.wallpaper_twilight_branches),
                selected = settings.wallpaperChoice == WallpaperChoice.TWILIGHT_BRANCHES,
                onClick = { onSelectWallpaper(WallpaperChoice.TWILIGHT_BRANCHES) },
              )
              WallpaperChoiceTile(
                choice = WallpaperChoice.SUNLIT_CEDAR,
                label = stringResource(R.string.wallpaper_sunlit_cedar),
                selected = settings.wallpaperChoice == WallpaperChoice.SUNLIT_CEDAR,
                onClick = { onSelectWallpaper(WallpaperChoice.SUNLIT_CEDAR) },
              )
              WallpaperChoiceTile(
                choice = WallpaperChoice.PASTEL_SHORE,
                label = stringResource(R.string.wallpaper_pastel_shore),
                selected = settings.wallpaperChoice == WallpaperChoice.PASTEL_SHORE,
                onClick = { onSelectWallpaper(WallpaperChoice.PASTEL_SHORE) },
              )
              WallpaperChoiceTile(
                choice = WallpaperChoice.STARRY_PINES,
                label = stringResource(R.string.wallpaper_starry_pines),
                selected = settings.wallpaperChoice == WallpaperChoice.STARRY_PINES,
                onClick = { onSelectWallpaper(WallpaperChoice.STARRY_PINES) },
              )
              WallpaperChoiceTile(
                choice = WallpaperChoice.COBALT_OAK,
                label = stringResource(R.string.wallpaper_cobalt_oak),
                selected = settings.wallpaperChoice == WallpaperChoice.COBALT_OAK,
                onClick = { onSelectWallpaper(WallpaperChoice.COBALT_OAK) },
              )
              WallpaperChoiceTile(
                choice = WallpaperChoice.TROPICAL_PALM,
                label = stringResource(R.string.wallpaper_tropical_palm),
                selected = settings.wallpaperChoice == WallpaperChoice.TROPICAL_PALM,
                onClick = { onSelectWallpaper(WallpaperChoice.TROPICAL_PALM) },
              )
              WallpaperChoiceTile(
                choice = WallpaperChoice.MISTY_FOREST,
                label = stringResource(R.string.wallpaper_misty_forest),
                selected = settings.wallpaperChoice == WallpaperChoice.MISTY_FOREST,
                onClick = { onSelectWallpaper(WallpaperChoice.MISTY_FOREST) },
              )
              WallpaperChoiceTile(
                choice = WallpaperChoice.SYSTEM,
                label = stringResource(R.string.wallpaper_system),
                selected = settings.wallpaperChoice == WallpaperChoice.SYSTEM,
                onClick = { onSelectWallpaper(WallpaperChoice.SYSTEM) },
              )
            }

            val canApplySystem =
              settings.wallpaperChoice.drawableRes != null ||
                (settings.wallpaperChoice == WallpaperChoice.CUSTOM &&
                  !settings.selectedCustomWallpaperPath.isNullOrBlank())
            if (canApplySystem) {
              OutlinedButton(
                onClick = {
                  onApplySystemWallpaper(
                    settings.wallpaperChoice,
                    settings.selectedCustomWallpaperPath,
                  )
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().testTag("apply_system_wallpaper_button"),
              ) {
                Text(
                  text = stringResource(R.string.wallpaper_apply_system),
                  color = SLauncherColors.AccentPrimary,
                )
              }
            }
          }

          // 3. Language Setting
          SettingsCard(
            icon = Icons.Rounded.Language,
            title = stringResource(R.string.settings_section_language),
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                LanguageChip(
                  label = stringResource(R.string.lang_system),
                  selected = settings.language == AppLanguage.SYSTEM,
                  onClick = { onSelectLanguage(AppLanguage.SYSTEM) },
                  modifier = Modifier.weight(1f).testTag("lang_system_chip"),
                )
              }
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                LanguageChip(
                  label = stringResource(R.string.lang_indonesian),
                  selected = settings.language == AppLanguage.INDONESIAN,
                  onClick = { onSelectLanguage(AppLanguage.INDONESIAN) },
                  modifier = Modifier.weight(1f).testTag("lang_id_chip"),
                )
                LanguageChip(
                  label = stringResource(R.string.lang_english),
                  selected = settings.language == AppLanguage.ENGLISH,
                  onClick = { onSelectLanguage(AppLanguage.ENGLISH) },
                  modifier = Modifier.weight(1f).testTag("lang_en_chip"),
                )
              }
            }
          }

          // 4. Welcome Dynamic Pop-Up
          SettingsCard(
            icon = Icons.Rounded.AutoAwesome,
            title = stringResource(R.string.settings_section_welcome),
          ) {
            SettingsToggleRow(
              title = stringResource(R.string.settings_welcome_toggle),
              subtitle = stringResource(R.string.settings_welcome_desc),
              checked = settings.welcomePopupEnabled,
              onCheckedChange = onToggleWelcomePopup,
              testTag = "welcome_popup_switch",
            )

            SettingsToggleRow(
              title = stringResource(R.string.settings_boot_welcome_toggle),
              subtitle = stringResource(R.string.settings_boot_welcome_desc),
              checked = settings.bootWelcomeEnabled,
              onCheckedChange = onToggleBootWelcome,
              testTag = "boot_welcome_switch",
            )

            OutlinedTextField(
              value = settings.customWelcomeText,
              onValueChange = onWelcomeTextChange,
              singleLine = true,
              label = { Text(stringResource(R.string.settings_welcome_input_label)) },
              placeholder = {
                Text(
                  text = stringResource(R.string.settings_welcome_input_hint, defaultWelcomeText),
                  color = SLauncherColors.TextMuted,
                )
              },
              shape = RoundedCornerShape(16.dp),
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = SLauncherColors.AccentPrimary,
                  unfocusedBorderColor = SLauncherColors.GlassBorder,
                  focusedTextColor = SLauncherColors.TextPrimary,
                  unfocusedTextColor = SLauncherColors.TextPrimary,
                  focusedLabelColor = SLauncherColors.AccentPrimary,
                  unfocusedLabelColor = SLauncherColors.TextSecondary,
                  cursorColor = SLauncherColors.AccentPrimary,
                ),
              modifier = Modifier.fillMaxWidth().testTag("welcome_text_input"),
            )

            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Button(
                onClick = onTestWelcomePopup,
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = SLauncherColors.AccentPrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                  ),
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f).testTag("test_welcome_button"),
              ) {
                Text(stringResource(R.string.settings_welcome_test_btn))
              }
              if (settings.customWelcomeText.isNotEmpty()) {
                TextButton(
                  onClick = { onWelcomeTextChange("") },
                  modifier = Modifier.testTag("reset_welcome_button"),
                ) {
                  Text(
                    text = stringResource(R.string.settings_welcome_reset_btn),
                    color = SLauncherColors.TextSecondary,
                  )
                }
              }
            }
          }

          // 5. Native Lock Screen (Non-Overlay)
          SettingsCard(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.settings_section_lockscreen),
          ) {
            SettingsToggleRow(
              title = stringResource(R.string.settings_lockscreen_toggle),
              subtitle = stringResource(R.string.settings_lockscreen_desc),
              checked = settings.lockScreenEnabled,
              onCheckedChange = onToggleLockScreen,
              testTag = "lockscreen_switch",
            )
            Button(
              onClick = onPreviewLockScreen,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = SLauncherColors.AccentPrimary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
              shape = RoundedCornerShape(50),
              modifier = Modifier.fillMaxWidth().testTag("preview_lockscreen_button"),
            ) {
              Text(stringResource(R.string.settings_lockscreen_preview_btn))
            }
            OutlinedButton(
              onClick = onOpenSecuritySettings,
              shape = RoundedCornerShape(50),
              modifier = Modifier.fillMaxWidth().testTag("system_security_button"),
            ) {
              Text(
                text = stringResource(R.string.settings_lockscreen_system_btn),
                color = SLauncherColors.TextPrimary,
              )
            }
          }

          // 6. Dynamic Notifications
          SettingsCard(
            icon = Icons.Rounded.NotificationsActive,
            title = stringResource(R.string.settings_section_notifications),
          ) {
            SettingsToggleRow(
              title = stringResource(R.string.settings_notif_toggle),
              subtitle =
                if (isNotificationAccessGranted) stringResource(R.string.settings_notif_status_granted)
                else stringResource(R.string.settings_notif_status_missing),
              checked = settings.dynamicNotificationsEnabled,
              onCheckedChange = onToggleDynamicNotifications,
              testTag = "dynamic_notif_switch",
            )
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              if (!isNotificationAccessGranted) {
                Button(
                  onClick = onRequestNotificationAccess,
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = SLauncherColors.AccentPrimary,
                      contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                  shape = RoundedCornerShape(50),
                  modifier = Modifier.weight(1f).testTag("grant_notif_access_button"),
                ) {
                  Text(stringResource(R.string.settings_notif_grant_btn))
                }
              }
              OutlinedButton(
                onClick = onTestDynamicNotification,
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f).testTag("test_dynamic_notif_button"),
              ) {
                Text(
                  text = stringResource(R.string.settings_notif_test_btn),
                  color = SLauncherColors.TextPrimary,
                )
              }
            }
          }

          // 7. Built-In System Quick Settings Tile Editor
          SystemQuickSettingsCard()

          // 8. Home Screen & Launcher Behavior
          SettingsCard(
            icon = Icons.Rounded.Home,
            title = stringResource(R.string.settings_section_home),
          ) {
            Text(
              text = stringResource(R.string.settings_home_layout_label),
              style = MaterialTheme.typography.bodyMedium,
              color = SLauncherColors.TextSecondary,
            )
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              LanguageChip(
                label = stringResource(R.string.layout_grid),
                selected = settings.homeAppsLayout == HomeAppsLayout.GRID,
                onClick = { onSelectHomeLayout(HomeAppsLayout.GRID) },
                modifier = Modifier.weight(1f).testTag("layout_grid_chip"),
              )
              LanguageChip(
                label = stringResource(R.string.layout_list),
                selected = settings.homeAppsLayout == HomeAppsLayout.LIST,
                onClick = { onSelectHomeLayout(HomeAppsLayout.LIST) },
                modifier = Modifier.weight(1f).testTag("layout_list_chip"),
              )
            }

            SettingsToggleRow(
              title = stringResource(R.string.settings_app_icon_only_toggle),
              subtitle = stringResource(R.string.settings_app_icon_only_desc),
              checked = settings.appIconOpensSettingsOnly,
              onCheckedChange = onToggleAppIconSettingsOnly,
              testTag = "app_icon_settings_only_switch",
            )

            OutlinedButton(
              onClick = onOpenHiddenAppsEditor,
              shape = RoundedCornerShape(50),
              modifier = Modifier.fillMaxWidth().testTag("settings_hide_apps_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.VisibilityOff,
                contentDescription = null,
                tint = SLauncherColors.AccentPrimary,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text =
                  stringResource(
                    R.string.settings_hide_apps_btn,
                    settings.hiddenComponentKeys.size,
                  ),
                color = SLauncherColors.TextPrimary,
              )
            }

            Button(
              onClick = onSetDefaultHome,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = SLauncherColors.AccentContainer,
                  contentColor = SLauncherColors.AccentPrimary,
                ),
              shape = RoundedCornerShape(50),
              modifier = Modifier.fillMaxWidth().testTag("set_default_home_button"),
            ) {
              Text(stringResource(R.string.settings_set_default_home_btn))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AddCustomWallpaperTile(onClick: () -> Unit) {
  val shape = RoundedCornerShape(18.dp)
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier =
      Modifier.width(96.dp)
        .height(142.dp)
        .clip(shape)
        .background(SLauncherColors.AccentContainer)
        .border(1.4.dp, SLauncherColors.AccentPrimary.copy(alpha = 0.65f), shape)
        .clickable(onClick = onClick)
        .padding(8.dp)
        .testTag("add_custom_wallpaper_tile"),
  ) {
    Icon(
      imageVector = Icons.Rounded.AddPhotoAlternate,
      contentDescription = stringResource(R.string.wallpaper_add_photo),
      tint = SLauncherColors.AccentPrimary,
      modifier = Modifier.size(30.dp),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = stringResource(R.string.wallpaper_add_photo),
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
      color = SLauncherColors.AccentPrimary,
      textAlign = TextAlign.Center,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun CustomWallpaperChoiceTile(
  path: String,
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  onRemove: () -> Unit,
  index: Int,
) {
  val shape = RoundedCornerShape(18.dp)
  val thumbState = rememberLocalWallpaperBitmap(path = path, maxDimensionPx = 280)
  Box(
    modifier =
      Modifier.width(96.dp)
        .height(142.dp)
        .clip(shape)
        .border(
          width = if (selected) 2.5.dp else 1.dp,
          color = if (selected) SLauncherColors.AccentPrimary else SLauncherColors.GlassBorder,
          shape = shape,
        )
        .clickable(onClick = onClick)
        .testTag("wallpaper_choice_custom_$index"),
  ) {
    val bmp = thumbState.value
    if (bmp != null) {
      Image(
        bitmap = bmp,
        contentDescription = label,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      Box(modifier = Modifier.fillMaxSize().background(SLauncherColors.DarkGlassPill))
    }

    Box(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))
          )
          .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = SLauncherColors.TextPrimary,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.align(Alignment.TopStart)
          .padding(6.dp)
          .size(22.dp)
          .clip(CircleShape)
          .background(Color.Black.copy(alpha = 0.65f))
          .clickable(onClick = onRemove)
          .testTag("remove_custom_wallpaper_$index"),
    ) {
      Icon(
        imageVector = Icons.Rounded.Close,
        contentDescription = stringResource(R.string.wallpaper_remove_custom_cd),
        tint = SLauncherColors.TextPrimary,
        modifier = Modifier.size(14.dp),
      )
    }

    if (selected) {
      Icon(
        imageVector = Icons.Rounded.CheckCircle,
        contentDescription = null,
        tint = SLauncherColors.AccentPrimary,
        modifier =
          Modifier.align(Alignment.TopEnd)
            .padding(6.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f)),
      )
    }
  }
}

@Composable
private fun WallpaperChoiceTile(
  choice: WallpaperChoice,
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(18.dp)
  Box(
    modifier =
      Modifier.width(96.dp)
        .height(142.dp)
        .clip(shape)
        .border(
          width = if (selected) 2.5.dp else 1.dp,
          color = if (selected) SLauncherColors.AccentPrimary else SLauncherColors.GlassBorder,
          shape = shape,
        )
        .clickable(onClick = onClick)
        .testTag("wallpaper_choice_${choice.key}"),
  ) {
    val resId = choice.drawableRes
    if (resId != null) {
      Image(
        painter = painterResource(id = resId),
        contentDescription = label,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(SLauncherColors.DarkGlassPill),
      ) {
        Icon(
          imageVector = Icons.Rounded.Wallpaper,
          contentDescription = null,
          tint = SLauncherColors.TextSecondary,
          modifier = Modifier.size(28.dp),
        )
      }
    }

    // Bottom gradient caption & selected badge
    Box(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))
          )
          .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = SLauncherColors.TextPrimary,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    if (selected) {
      Icon(
        imageVector = Icons.Rounded.CheckCircle,
        contentDescription = null,
        tint = SLauncherColors.AccentPrimary,
        modifier =
          Modifier.align(Alignment.TopEnd)
            .padding(6.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f)),
      )
    }
  }
}

@Composable
private fun SettingsCard(
  icon: ImageVector,
  title: String,
  content: @Composable ColumnScope.() -> Unit,
) {
  val cardShape = RoundedCornerShape(24.dp)
  Column(
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier =
      Modifier.fillMaxWidth()
        .clip(cardShape)
        .background(SLauncherColors.DarkGlassCard)
        .border(1.dp, SLauncherColors.GlassBorder, cardShape)
        .padding(18.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = SLauncherColors.AccentPrimary,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = SLauncherColors.TextPrimary,
      )
    }
    content()
  }
}

@Composable
private fun SettingsToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable { onCheckedChange(!checked) }
        .padding(vertical = 4.dp),
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        color = SLauncherColors.TextPrimary,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = SLauncherColors.TextSecondary,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
          checkedTrackColor = SLauncherColors.AccentPrimary,
          uncheckedThumbColor = SLauncherColors.TextSecondary,
          uncheckedTrackColor = SLauncherColors.DarkGlassPill,
        ),
      modifier = Modifier.testTag(testTag),
    )
  }
}

@Composable
private fun LanguageChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = {
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
      )
    },
    shape = RoundedCornerShape(50),
    colors =
      FilterChipDefaults.filterChipColors(
        selectedContainerColor = SLauncherColors.AccentContainer,
        selectedLabelColor = SLauncherColors.AccentPrimary,
        containerColor = SLauncherColors.DarkGlassPill,
        labelColor = SLauncherColors.TextSecondary,
      ),
    border =
      FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = selected,
        borderColor = SLauncherColors.GlassBorder,
        selectedBorderColor = SLauncherColors.AccentPrimary,
      ),
    modifier = modifier,
  )
}

@Composable
private fun SystemQuickSettingsCard() {
  val context = LocalContext.current

  fun addTile(serviceClass: Class<out TileService>, labelRes: Int, iconRes: Int) {
    val label = context.getString(labelRes)
    val prompted =
      SystemQuickSettingsHelper.requestAddTile(
        context = context,
        serviceClass = serviceClass,
        label = label,
        iconRes = iconRes,
      ) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
              Toast.makeText(context, context.getString(R.string.qs_tile_added_toast), Toast.LENGTH_SHORT).show()
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
              Toast.makeText(context, context.getString(R.string.qs_tile_already_toast), Toast.LENGTH_SHORT).show()
          }
        }
      }
    if (!prompted) {
      Toast.makeText(context, context.getString(R.string.qs_tile_manual_hint), Toast.LENGTH_LONG).show()
    }
  }

  SettingsCard(
    icon = Icons.Rounded.Tune,
    title = stringResource(R.string.settings_section_system_qs),
  ) {
    Text(
      text = stringResource(R.string.settings_system_qs_desc),
      style = MaterialTheme.typography.bodyMedium,
      color = SLauncherColors.TextSecondary,
    )

    SystemQsTileRow(
      title = stringResource(R.string.qs_tile_lock_label),
      onAddClick = {
        addTile(LockScreenTileService::class.java, R.string.qs_tile_lock_label, R.drawable.ic_qs_lock)
      },
      testTag = "add_qs_lock_tile",
    )

    SystemQsTileRow(
      title = stringResource(R.string.qs_tile_welcome_label),
      onAddClick = {
        addTile(WelcomePopupTileService::class.java, R.string.qs_tile_welcome_label, R.drawable.ic_qs_welcome)
      },
      testTag = "add_qs_welcome_tile",
    )

    SystemQsTileRow(
      title = stringResource(R.string.qs_tile_dynamic_label),
      onAddClick = {
        addTile(DynamicAlertsTileService::class.java, R.string.qs_tile_dynamic_label, R.drawable.ic_qs_dynamic)
      },
      testTag = "add_qs_dynamic_tile",
    )

    SystemQsTileRow(
      title = stringResource(R.string.qs_tile_settings_label),
      onAddClick = {
        addTile(LauncherSettingsTileService::class.java, R.string.qs_tile_settings_label, R.drawable.ic_qs_slauncher)
      },
      testTag = "add_qs_settings_tile",
    )

    Button(
      onClick = { SystemQuickSettingsHelper.expandSystemQuickSettings(context) },
      colors =
        ButtonDefaults.buttonColors(
          containerColor = SLauncherColors.AccentContainer,
          contentColor = SLauncherColors.AccentPrimary,
        ),
      shape = RoundedCornerShape(50),
      modifier = Modifier.fillMaxWidth().testTag("open_system_qs_button"),
    ) {
      Text(stringResource(R.string.settings_system_qs_open_btn))
    }
  }
}

@Composable
private fun SystemQsTileRow(
  title: String,
  onAddClick: () -> Unit,
  testTag: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(SLauncherColors.DarkGlassPill)
        .border(0.8.dp, SLauncherColors.GlassBorder, RoundedCornerShape(14.dp))
        .clickable(onClick = onAddClick)
        .padding(horizontal = 14.dp, vertical = 10.dp)
        .testTag(testTag),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
      color = SLauncherColors.TextPrimary,
      modifier = Modifier.weight(1f),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Rounded.AddCircleOutline,
        contentDescription = null,
        tint = SLauncherColors.AccentPrimary,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = stringResource(R.string.qs_tile_add_btn),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = SLauncherColors.AccentPrimary,
      )
    }
  }
}
