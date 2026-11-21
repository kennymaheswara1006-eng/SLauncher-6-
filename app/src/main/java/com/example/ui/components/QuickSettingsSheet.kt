package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.QuickSettingsState
import com.example.shizuku.ShizukuConnectionState
import com.example.ui.theme.SLauncherColors
import com.example.ui.theme.softDeepCapsule
import kotlin.math.roundToInt

/**
 * Custom Shizuku-backed Quick Settings Control Center styled with
 * Soft Modern Capsules and Deep Layered Shadows.
 * Recent Apps remains delegated to the Android system Recents.
 */
@Composable
fun QuickSettingsSheet(
  visible: Boolean,
  state: QuickSettingsState,
  onToggleWifi: () -> Unit,
  onToggleMobileData: () -> Unit,
  onToggleBluetooth: () -> Unit,
  onToggleFlashlight: () -> Unit,
  onToggleDarkMode: () -> Unit,
  onToggleAutoRotate: () -> Unit,
  onBrightnessChange: (Int) -> Unit,
  onVolumeChange: (Int) -> Unit,
  onConnectShizuku: () -> Unit,
  onLockScreen: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenSystemQuickSettings: () -> Unit,
  onClose: () -> Unit,
) {
  AnimatedVisibility(
    visible = visible,
    enter =
      slideInVertically(
        animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow),
        initialOffsetY = { -it / 2 },
      ) + fadeIn(),
    exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
  ) {
    Box(
      contentAlignment = Alignment.TopCenter,
      modifier =
        Modifier.fillMaxSize()
          .background(Color(0x9E04070E))
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClose,
          )
          .statusBarsPadding()
          .navigationBarsPadding()
          .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
          Modifier.widthIn(max = 520.dp)
            .fillMaxWidth()
            .softDeepCapsule(
              cornerRadius = 34.dp,
              elevation = 28.dp,
              surfaceBrush =
                Brush.verticalGradient(
                  colors =
                    listOf(SLauncherColors.SoftCapsulePanelTop, SLauncherColors.SoftCapsulePanelBottom)
                ),
            )
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = {},
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .testTag("custom_quick_settings_sheet"),
      ) {
        // Header Row: Shizuku Connection Status Pill + Close
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          ShizukuStatusCapsule(
            state = state.shizukuState,
            onClick = onConnectShizuku,
            modifier = Modifier.weight(1f),
          )

          Spacer(modifier = Modifier.width(10.dp))

          IconButton(
            onClick = onClose,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .size(42.dp)
                .softDeepCapsule(cornerRadius = 21.dp, elevation = 12.dp)
                .testTag("qs_close_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(R.string.action_dismiss),
              tint = SLauncherColors.TextPrimary,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        // 2x3 Soft Capsule Matrix for System Toggles
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            SoftQuickToggleCapsule(
              icon = Icons.Rounded.Wifi,
              title = stringResource(R.string.qs_wifi),
              active = state.wifiEnabled,
              accentColor = SLauncherColors.AccentPrimary,
              onClick = onToggleWifi,
              testTag = "qs_toggle_wifi",
              modifier = Modifier.weight(1f),
            )
            SoftQuickToggleCapsule(
              icon = Icons.Rounded.CellTower,
              title = stringResource(R.string.qs_mobile_data),
              active = state.mobileDataEnabled,
              accentColor = SLauncherColors.SoftCapsuleEmerald,
              onClick = onToggleMobileData,
              testTag = "qs_toggle_data",
              modifier = Modifier.weight(1f),
            )
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            SoftQuickToggleCapsule(
              icon = Icons.Rounded.Bluetooth,
              title = stringResource(R.string.qs_bluetooth),
              active = state.bluetoothEnabled,
              accentColor = SLauncherColors.AccentPrimary,
              onClick = onToggleBluetooth,
              testTag = "qs_toggle_bluetooth",
              modifier = Modifier.weight(1f),
            )
            SoftQuickToggleCapsule(
              icon = Icons.Rounded.FlashlightOn,
              title = stringResource(R.string.qs_flashlight),
              active = state.flashlightEnabled,
              enabled = state.flashlightAvailable,
              accentColor = SLauncherColors.WarningSoft,
              onClick = onToggleFlashlight,
              testTag = "qs_toggle_flashlight",
              modifier = Modifier.weight(1f),
            )
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            SoftQuickToggleCapsule(
              icon = Icons.Rounded.DarkMode,
              title = stringResource(R.string.qs_dark_mode),
              active = state.darkModeEnabled,
              accentColor = SLauncherColors.StormIndigo,
              onClick = onToggleDarkMode,
              testTag = "qs_toggle_dark_mode",
              modifier = Modifier.weight(1f),
            )
            SoftQuickToggleCapsule(
              icon = Icons.Rounded.ScreenRotation,
              title = stringResource(R.string.qs_auto_rotate),
              active = state.autoRotateEnabled,
              accentColor = SLauncherColors.AccentPrimary,
              onClick = onToggleAutoRotate,
              testTag = "qs_toggle_rotate",
              modifier = Modifier.weight(1f),
            )
          }
        }

        // Brightness Soft Capsule Slider
        var brightnessSlider by
          remember(state.brightnessLevel) {
            mutableFloatStateOf(((state.brightnessLevel / 255f) * 100f).coerceIn(5f, 100f))
          }
        SoftCapsuleSliderRow(
          icon = Icons.Rounded.BrightnessHigh,
          label = stringResource(R.string.qs_brightness),
          valuePercent = brightnessSlider.roundToInt(),
          value = brightnessSlider,
          onValueChange = { brightnessSlider = it },
          onValueChangeFinished = {
            val level = ((brightnessSlider / 100f) * 255f).roundToInt().coerceIn(10, 255)
            onBrightnessChange(level)
          },
          accentColor = SLauncherColors.WarningSoft,
          testTag = "qs_brightness_slider",
        )

        // Media Volume Soft Capsule Slider
        var volumeSlider by
          remember(state.mediaVolumePercent) {
            mutableFloatStateOf(state.mediaVolumePercent.toFloat().coerceIn(0f, 100f))
          }
        SoftCapsuleSliderRow(
          icon = Icons.Rounded.VolumeUp,
          label = stringResource(R.string.qs_media_volume),
          valuePercent = volumeSlider.roundToInt(),
          value = volumeSlider,
          onValueChange = {
            volumeSlider = it
            onVolumeChange(it.roundToInt())
          },
          onValueChangeFinished = { onVolumeChange(volumeSlider.roundToInt()) },
          accentColor = SLauncherColors.AccentPrimary,
          testTag = "qs_volume_slider",
        )

        // Bottom Utility Action Capsules
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          SoftActionCapsuleButton(
            icon = Icons.Rounded.Lock,
            label = stringResource(R.string.qs_action_lock),
            onClick = onLockScreen,
            testTag = "qs_action_lock",
            modifier = Modifier.weight(1f),
          )
          SoftActionCapsuleButton(
            icon = Icons.Rounded.Settings,
            label = stringResource(R.string.qs_action_settings),
            onClick = onOpenSettings,
            testTag = "qs_action_settings",
            modifier = Modifier.weight(1f),
          )
          SoftActionCapsuleButton(
            icon = Icons.Rounded.Tune,
            label = stringResource(R.string.qs_action_system),
            onClick = onOpenSystemQuickSettings,
            testTag = "qs_action_system",
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Composable
private fun ShizukuStatusCapsule(
  state: ShizukuConnectionState,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isConnected = state == ShizukuConnectionState.CONNECTED
  val dotColor =
    when (state) {
      ShizukuConnectionState.CONNECTED -> SLauncherColors.SoftCapsuleEmerald
      ShizukuConnectionState.CONNECTING -> SLauncherColors.AccentPrimary
      else -> SLauncherColors.WarningSoft
    }
  val statusText =
    when (state) {
      ShizukuConnectionState.CONNECTED -> stringResource(R.string.shizuku_status_connected)
      ShizukuConnectionState.CONNECTING -> stringResource(R.string.shizuku_status_connecting)
      ShizukuConnectionState.NEEDS_PERMISSION -> stringResource(R.string.shizuku_status_needs_perm)
      ShizukuConnectionState.NOT_RUNNING -> stringResource(R.string.shizuku_status_not_running)
      ShizukuConnectionState.NOT_INSTALLED -> stringResource(R.string.shizuku_status_not_installed)
    }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .softDeepCapsule(
          cornerRadius = 24.dp,
          elevation = 12.dp,
          isActive = isConnected,
          accentColor = SLauncherColors.SoftCapsuleEmerald,
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 10.dp)
        .testTag("qs_shizuku_status_pill"),
  ) {
    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(dotColor))
    Spacer(modifier = Modifier.width(8.dp))
    Icon(
      imageVector = Icons.Rounded.Terminal,
      contentDescription = null,
      tint = dotColor,
      modifier = Modifier.size(16.dp),
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = statusText,
      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
      color = SLauncherColors.TextPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun SoftQuickToggleCapsule(
  icon: ImageVector,
  title: String,
  active: Boolean,
  accentColor: Color,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (isPressed) 0.96f else 1f,
      animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
      label = "qs_capsule_scale",
    )

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      modifier
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
          alpha = if (enabled) 1f else 0.5f
        }
        .minimumInteractiveComponentSize()
        .softDeepCapsule(
          cornerRadius = 26.dp,
          elevation = if (active) 20.dp else 14.dp,
          isActive = active,
          accentColor = accentColor,
        )
        .clickable(
          enabled = enabled,
          interactionSource = interactionSource,
          indication = null,
          onClick = onClick,
        )
        .padding(horizontal = 13.dp, vertical = 12.dp)
        .testTag(testTag),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.size(36.dp)
          .clip(CircleShape)
          .background(
            if (active) accentColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)
          ),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = if (active) accentColor else SLauncherColors.TextSecondary,
        modifier = Modifier.size(20.dp),
      )
    }

    Spacer(modifier = Modifier.width(10.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
        color = SLauncherColors.TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = stringResource(if (active) R.string.qs_on else R.string.qs_off),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = if (active) accentColor else SLauncherColors.TextMuted,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun SoftCapsuleSliderRow(
  icon: ImageVector,
  label: String,
  valuePercent: Int,
  value: Float,
  onValueChange: (Float) -> Unit,
  onValueChangeFinished: () -> Unit,
  accentColor: Color,
  testTag: String,
) {
  Column(
    verticalArrangement = Arrangement.spacedBy(2.dp),
    modifier =
      Modifier.fillMaxWidth()
        .softDeepCapsule(cornerRadius = 26.dp, elevation = 14.dp)
        .padding(horizontal = 16.dp, vertical = 10.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          color = SLauncherColors.TextPrimary,
        )
      }
      Text(
        text = "$valuePercent%",
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
        color = SLauncherColors.TextSecondary,
      )
    }

    Slider(
      value = value,
      onValueChange = onValueChange,
      onValueChangeFinished = onValueChangeFinished,
      valueRange = 0f..100f,
      colors =
        SliderDefaults.colors(
          thumbColor = SLauncherColors.TextPrimary,
          activeTrackColor = accentColor,
          inactiveTrackColor = Color.White.copy(alpha = 0.14f),
        ),
      modifier = Modifier.fillMaxWidth().height(32.dp).testTag(testTag),
    )
  }
}

@Composable
private fun SoftActionCapsuleButton(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .softDeepCapsule(cornerRadius = 24.dp, elevation = 14.dp)
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 11.dp)
        .testTag(testTag),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = SLauncherColors.AccentPrimary,
      modifier = Modifier.size(16.dp),
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
      color = SLauncherColors.TextPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
