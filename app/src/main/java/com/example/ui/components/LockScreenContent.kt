package com.example.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.LauncherSettings
import com.example.data.LockAuthMode
import com.example.data.WeatherCondition
import com.example.data.WeatherUiState
import com.example.notifications.DynamicNotificationItem
import com.example.security.PinSecurityHelper
import com.example.ui.theme.SLauncherColors
import com.example.ui.theme.softDeepCapsule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class BiometricAvailability {
  AVAILABLE,
  NOT_ENROLLED,
  UNAVAILABLE,
}

/**
 * Full-screen Dark Minimalist native Lock Screen UI rendered inside LockScreenActivity.
 * Features:
 * - Centered editorial thin digital clock, full date, and slim telemetry pill (Weather + Battery)
 * - Clean wallpaper breathing room when quiet (only shows notification stack when notifications exist)
 * - Luminous glowing Fingerprint Scanner dock & Dark-Glass Numeric PIN Keypad
 * - Strict auth enforcement when Fingerprint/PIN mode is selected
 */
@Composable
fun LockScreenContent(
  settings: LauncherSettings,
  weatherState: WeatherUiState,
  isNotificationAccessGranted: Boolean,
  notifications: List<DynamicNotificationItem>,
  biometricAvailability: BiometricAvailability = BiometricAvailability.UNAVAILABLE,
  biometricStatusHint: String? = null,
  onVerifyPin: (String) -> Boolean = { false },
  onRequestBiometricPrompt: () -> Unit = {},
  onRequestLocationPermission: () -> Unit,
  onRefreshWeather: () -> Unit,
  onRequestNotificationAccess: () -> Unit,
  onOpenNotification: (DynamicNotificationItem) -> Unit,
  onDismissNotification: (DynamicNotificationItem) -> Unit,
  onUnlock: () -> Unit,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val dragOffsetY = remember { Animatable(0f) }

  val authMode = settings.lockAuthMode
  val biometricReady = authMode.usesBiometric && biometricAvailability == BiometricAvailability.AVAILABLE
  val pinReady = authMode.usesPin && settings.hasLockPinConfigured
  val requiresAuthentication = biometricReady || pinReady

  var showingPinPad by remember(authMode, pinReady, biometricReady) {
    mutableStateOf(
      when (authMode) {
        LockAuthMode.PIN_ONLY -> true
        LockAuthMode.BIOMETRIC_AND_PIN -> !biometricReady && pinReady
        else -> false
      }
    )
  }
  var unlockSuccessFlash by remember { mutableStateOf(false) }

  val unlockProgress = ((-dragOffsetY.value) / 420f).coerceIn(0f, 1f)
  val contentAlpha = (1f - (unlockProgress * 0.7f)).coerceIn(0.25f, 1f)

  val scrim =
    Brush.verticalGradient(
      0.0f to Color(0x96060912),
      0.45f to Color(0x6E080B14),
      1.0f to Color(0xDC050810),
    )

  fun completeUnlockWithFlash() {
    coroutineScope.launch {
      unlockSuccessFlash = true
      delay(160L)
      onUnlock()
    }
  }

  Box(
    contentAlignment = Alignment.TopCenter,
    modifier =
      Modifier.fillMaxSize()
        .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
        .alpha(contentAlpha)
        .pointerInput(requiresAuthentication, biometricReady, pinReady) {
          detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
              change.consume()
              coroutineScope.launch {
                val resistance = if (requiresAuthentication) 0.35f else 1f
                val next = (dragOffsetY.value + dragAmount * resistance).coerceIn(-260f, 0f)
                dragOffsetY.snapTo(next)
              }
            },
            onDragEnd = {
              coroutineScope.launch {
                if (dragOffsetY.value < -130f) {
                  if (!requiresAuthentication) {
                    dragOffsetY.animateTo(-1400f, tween(220))
                    onUnlock()
                  } else {
                    dragOffsetY.animateTo(0f, spring(dampingRatio = 0.78f))
                    when {
                      biometricReady && !showingPinPad -> onRequestBiometricPrompt()
                      pinReady -> showingPinPad = true
                    }
                  }
                } else {
                  dragOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium),
                  )
                }
              }
            },
            onDragCancel = {
              coroutineScope.launch { dragOffsetY.animateTo(0f, spring()) }
            },
          )
        },
  ) {
    WallpaperBackdrop(
      wallpaperChoice = settings.wallpaperChoice,
      customWallpaperPath = settings.selectedCustomWallpaperPath,
    )

    // Deep Minimalist Dark Scrim + Subtle Luminous Cyan Radial Glow at Bottom
    Box(
      contentAlignment = Alignment.TopCenter,
      modifier =
        Modifier.fillMaxSize()
          .background(scrim)
          .drawBehind {
            val auraColor =
              if (unlockSuccessFlash) SLauncherColors.SoftCapsuleEmerald
              else SLauncherColors.AccentPrimary
            drawCircle(
              brush =
                Brush.radialGradient(
                  colors = listOf(auraColor.copy(alpha = 0.20f), Color.Transparent),
                  center = Offset(size.width * 0.5f, size.height * 0.86f),
                  radius = size.width * 0.75f,
                )
            )
          }
          .statusBarsPadding()
          .navigationBarsPadding(),
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
          Modifier.fillMaxSize()
            .widthIn(max = 540.dp)
            .padding(horizontal = 24.dp, vertical = 14.dp),
      ) {
        if (notifications.isNotEmpty() || (!isNotificationAccessGranted && settings.dynamicNotificationsEnabled)) {
          DynamicPopUpCapsule(
            welcomePopupEnabled = false,
            welcomeMessage = "",
            welcomeTriggerId = 0L,
            dynamicNotificationsEnabled = settings.dynamicNotificationsEnabled,
            isNotificationAccessGranted = isNotificationAccessGranted,
            notifications = notifications,
            onRequestNotificationAccess = onRequestNotificationAccess,
            onOpenNotification = onOpenNotification,
            onDismissNotification = onDismissNotification,
          )
        }

        Spacer(modifier = Modifier.height(if (showingPinPad) 16.dp else 34.dp))

        // Centered Editorial Minimalist Clock + Date + Slim Weather/Battery Telemetry Pill
        CenteredMinimalClockHeader(
          weatherState = weatherState,
          compact = showingPinPad,
          onRequestLocationPermission = onRequestLocationPermission,
          onRefreshWeather = onRefreshWeather,
        )

        Spacer(modifier = Modifier.height(if (showingPinPad) 16.dp else 22.dp))

        if (notifications.isNotEmpty() && !showingPinPad) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = SLauncherColors.AccentPrimary,
                modifier = Modifier.size(14.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = stringResource(R.string.lockscreen_notifications_header, notifications.size),
                style = MaterialTheme.typography.labelMedium,
                color = SLauncherColors.TextPrimary,
              )
            }
            Text(
              text = stringResource(R.string.lockscreen_swipe_hint),
              style = MaterialTheme.typography.labelSmall,
              color = SLauncherColors.TextMuted,
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
          ) {
            items(items = notifications, key = { it.key }) { item ->
              val itemSwipeX = remember(item.key) { Animatable(0f) }
              val itemAlpha = (1f - (itemSwipeX.value / 520f)).coerceIn(0f, 1f)

              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                  Modifier.fillMaxWidth()
                    .offset { IntOffset(itemSwipeX.value.roundToInt(), 0) }
                    .alpha(itemAlpha)
                    .softDeepCapsule(cornerRadius = 24.dp, elevation = 16.dp)
                    .pointerInput(item.key) {
                      detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                          change.consume()
                          coroutineScope.launch {
                            val next = (itemSwipeX.value + dragAmount).coerceAtLeast(0f)
                            itemSwipeX.snapTo(next)
                          }
                        },
                        onDragEnd = {
                          coroutineScope.launch {
                            if (itemSwipeX.value > 160f) {
                              itemSwipeX.animateTo(
                                targetValue = 950f,
                                animationSpec = tween(190, easing = FastOutSlowInEasing),
                              )
                              onDismissNotification(item)
                            } else {
                              itemSwipeX.animateTo(0f, spring())
                            }
                          }
                        },
                        onDragCancel = {
                          coroutineScope.launch { itemSwipeX.animateTo(0f, spring()) }
                        },
                      )
                    }
                    .clickable { onOpenNotification(item) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("lockscreen_notif_${item.key}"),
              ) {
                NotificationAppIcon(
                  item = item,
                  size = 38.dp,
                  showLiveDot = false,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(
                      text = item.appName,
                      style =
                        MaterialTheme.typography.labelSmall.copy(
                          fontWeight = FontWeight.SemiBold,
                          fontSize = 11.sp,
                        ),
                      color = SLauncherColors.AccentPrimary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = "•",
                      style = MaterialTheme.typography.labelSmall,
                      color = SLauncherColors.TextMuted,
                    )
                    Text(
                      text = formatRelativeNotificationTime(context, item.postTime),
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                      color = SLauncherColors.TextMuted,
                      maxLines = 1,
                    )
                  }
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = item.title,
                    style =
                      MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                      ),
                    color = SLauncherColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  if (item.text.isNotBlank()) {
                    Text(
                      text = item.text,
                      style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                      color = SLauncherColors.TextSecondary,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }
            }
          }
        } else {
          // Minimalist Dark breathing space when no notifications (or when PIN pad is open)
          Spacer(modifier = Modifier.weight(1f))
        }

        // Bottom Unlock Dock: Animated switch between Luminous Fingerprint Scanner & Numeric PIN Keypad
        AnimatedContent(
          targetState = showingPinPad,
          transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
          label = "lockscreen_auth_dock",
        ) { pinModeVisible ->
          if (pinModeVisible) {
            LockScreenPinKeypad(
              hasPinConfigured = settings.hasLockPinConfigured,
              canSwitchToBiometric = biometricReady,
              onVerifyPin = onVerifyPin,
              onPinSuccess = { completeUnlockWithFlash() },
              onSwitchToBiometric = {
                showingPinPad = false
                onRequestBiometricPrompt()
              },
              onSwipeUnlockFallback = {
                if (!requiresAuthentication) onUnlock()
              },
            )
          } else {
            LuminousFingerprintDock(
              authMode = authMode,
              biometricAvailability = biometricAvailability,
              biometricStatusHint = biometricStatusHint,
              unlockSuccessFlash = unlockSuccessFlash,
              hasPinConfigured = settings.hasLockPinConfigured,
              requiresAuthentication = requiresAuthentication,
              onFingerprintTap = {
                if (biometricReady) {
                  onRequestBiometricPrompt()
                } else if (pinReady) {
                  showingPinPad = true
                }
              },
              onSwitchToPin = { showingPinPad = true },
              onDirectUnlock = { completeUnlockWithFlash() },
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
      }
    }
  }
}

@Composable
private fun CenteredMinimalClockHeader(
  weatherState: WeatherUiState,
  compact: Boolean,
  onRequestLocationPermission: () -> Unit,
  onRefreshWeather: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
  var batteryStatus by remember { mutableStateOf(readBatteryStatus(context)) }

  DisposableEffect(context, lifecycleOwner) {
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
          when (intent?.action) {
            Intent.ACTION_TIME_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED -> currentTimeMillis = System.currentTimeMillis()
            Intent.ACTION_BATTERY_CHANGED -> batteryStatus = parseBatteryIntent(intent)
          }
        }
      }
    val filter =
      IntentFilter().apply {
        addAction(Intent.ACTION_TIME_TICK)
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
        addAction(Intent.ACTION_DATE_CHANGED)
        addAction(Intent.ACTION_BATTERY_CHANGED)
      }
    var registered = false
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> {
          currentTimeMillis = System.currentTimeMillis()
          batteryStatus = readBatteryStatus(context)
          if (!registered) {
            context.registerReceiver(receiver, filter)
            registered = true
          }
        }
        Lifecycle.Event.ON_STOP -> {
          if (registered) {
            runCatching { context.unregisterReceiver(receiver) }
            registered = false
          }
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
      context.registerReceiver(receiver, filter)
      registered = true
    }
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      if (registered) runCatching { context.unregisterReceiver(receiver) }
    }
  }

  val is24Hour = DateFormat.is24HourFormat(context)
  val locale = remember(context) { context.resources.configuration.locales.get(0) ?: Locale.getDefault() }
  val timeText =
    remember(currentTimeMillis, is24Hour, locale) {
      SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm", locale).format(Date(currentTimeMillis))
    }
  val dateText =
    remember(currentTimeMillis, locale) {
      SimpleDateFormat("EEEE, d MMMM", locale).format(Date(currentTimeMillis))
    }

  val batteryCd = stringResource(R.string.cd_battery_indicator, batteryStatus.levelPercent)

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = timeText,
      style =
        TextStyle(
          fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
          fontWeight = FontWeight.ExtraLight,
          fontSize = if (compact) 56.sp else 74.sp,
          letterSpacing = (-2).sp,
          fontFeatureSettings = "tnum",
          color = Color.White,
          shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 4f), blurRadius = 16f),
        ),
      maxLines = 1,
    )

    Text(
      text = dateText,
      style =
        MaterialTheme.typography.titleMedium.copy(
          fontSize = 15.sp,
          fontWeight = FontWeight.Normal,
          letterSpacing = 0.3.sp,
        ),
      color = SLauncherColors.TextPrimary.copy(alpha = 0.88f),
      maxLines = 1,
    )

    // Slim Minimalist Weather + Battery Telemetry Pill
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier =
        Modifier.padding(top = 4.dp)
          .softDeepCapsule(cornerRadius = 50.dp, elevation = 12.dp)
          .clickable {
            if (weatherState == WeatherUiState.NeedsPermission) onRequestLocationPermission()
            else onRefreshWeather()
          }
          .padding(horizontal = 14.dp, vertical = 6.dp)
          .testTag("lockscreen_telemetry_pill"),
    ) {
      when (weatherState) {
        is WeatherUiState.Ready -> {
          val data = weatherState.data
          WeatherConditionIcon(
            condition = data.condition,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = "${data.locationLabel.substringBefore(",")} · ${data.temperatureCelsius}°",
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = SLauncherColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        WeatherUiState.NeedsPermission -> {
          Icon(
            imageVector = Icons.Rounded.LocationOn,
            contentDescription = null,
            tint = SLauncherColors.AccentPrimary,
            modifier = Modifier.size(13.dp),
          )
          Text(
            text = stringResource(R.string.weather_allow_location),
            style = MaterialTheme.typography.labelSmall,
            color = SLauncherColors.TextSecondary,
            maxLines = 1,
          )
        }
        WeatherUiState.Loading -> {
          WeatherConditionIcon(condition = WeatherCondition.PARTLY_CLOUDY, modifier = Modifier.size(15.dp))
          Text(
            text = stringResource(R.string.weather_loading),
            style = MaterialTheme.typography.labelSmall,
            color = SLauncherColors.TextSecondary,
            maxLines = 1,
          )
        }
        WeatherUiState.Unavailable -> {
          WeatherConditionIcon(condition = WeatherCondition.CLOUDY, modifier = Modifier.size(15.dp))
          Text(
            text = stringResource(R.string.weather_unavailable),
            style = MaterialTheme.typography.labelSmall,
            color = SLauncherColors.TextSecondary,
            maxLines = 1,
          )
        }
      }

      Text(text = "•", style = MaterialTheme.typography.labelSmall, color = SLauncherColors.TextMuted)

      val batteryIcon =
        when {
          batteryStatus.isCharging -> Icons.Rounded.BatteryChargingFull
          batteryStatus.levelPercent >= 90 -> Icons.Rounded.BatteryFull
          else -> Icons.Rounded.BatteryStd
        }
      val batteryTint =
        when {
          batteryStatus.isCharging -> SLauncherColors.BatteryCharging
          batteryStatus.levelPercent <= 15 -> SLauncherColors.WarningSoft
          else -> SLauncherColors.AccentPrimary
        }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = batteryCd },
      ) {
        Icon(
          imageVector = batteryIcon,
          contentDescription = null,
          tint = batteryTint,
          modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
          text =
            if (batteryStatus.isCharging) stringResource(R.string.battery_charging_format, batteryStatus.levelPercent)
            else stringResource(R.string.battery_format, batteryStatus.levelPercent),
          style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
          color = SLauncherColors.TextPrimary,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun LuminousFingerprintDock(
  authMode: LockAuthMode,
  biometricAvailability: BiometricAvailability,
  biometricStatusHint: String?,
  unlockSuccessFlash: Boolean,
  hasPinConfigured: Boolean,
  requiresAuthentication: Boolean,
  onFingerprintTap: () -> Unit,
  onSwitchToPin: () -> Unit,
  onDirectUnlock: () -> Unit,
) {
  val showScanner = authMode.usesBiometric
  val biometricReady = biometricAvailability == BiometricAvailability.AVAILABLE
  val accentColor =
    if (unlockSuccessFlash) SLauncherColors.SoftCapsuleEmerald
    else SLauncherColors.AccentPrimary

  val pulse = rememberInfiniteTransition(label = "luminous_fp_pulse")
  val ringScale by
    pulse.animateFloat(
      initialValue = 0.94f,
      targetValue = 1.18f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(1500, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "ring_scale",
    )
  val ringAlpha by
    pulse.animateFloat(
      initialValue = 0.25f,
      targetValue = 0.65f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(1500, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "ring_alpha",
    )

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
  ) {
    if (!biometricStatusHint.isNullOrBlank()) {
      Text(
        text = biometricStatusHint,
        style = MaterialTheme.typography.labelMedium,
        color = SLauncherColors.WarningSoft,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }

    if (showScanner) {
      // Luminous Circular Fingerprint Scanner with multi-layer glowing halo
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.size(104.dp)
            .drawBehind {
              drawCircle(
                brush =
                  Brush.radialGradient(
                    colors = listOf(accentColor.copy(alpha = ringAlpha * 0.55f), Color.Transparent),
                    center = center,
                    radius = (size.minDimension / 2f) * ringScale,
                  )
              )
            }
            .testTag("lockscreen_biometric_button"),
      ) {
        // Outer luminous halo ring
        Box(
          modifier =
            Modifier.size(84.dp)
              .graphicsLayer {
                scaleX = ringScale
                scaleY = ringScale
              }
              .clip(CircleShape)
              .border(1.2.dp, accentColor.copy(alpha = ringAlpha), CircleShape)
        )

        // Core sensor pad
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.size(68.dp)
              .clip(CircleShape)
              .background(Color(0xD90A1220))
              .border(1.8.dp, accentColor.copy(alpha = 0.85f), CircleShape)
              .clickable(onClick = onFingerprintTap),
        ) {
          Icon(
            imageVector = Icons.Rounded.Fingerprint,
            contentDescription = stringResource(R.string.lockscreen_biometric_prompt),
            tint = accentColor,
            modifier = Modifier.size(38.dp),
          )
        }
      }

      Text(
        text =
          when {
            biometricReady -> stringResource(R.string.lockscreen_biometric_prompt)
            biometricAvailability == BiometricAvailability.NOT_ENROLLED ->
              stringResource(R.string.lockscreen_biometric_need_enroll)
            else -> stringResource(R.string.settings_biometric_status_unavailable)
          },
        style = MaterialTheme.typography.labelMedium,
        color = if (biometricReady) SLauncherColors.TextSecondary else SLauncherColors.WarningSoft,
        textAlign = TextAlign.Center,
      )
    }

    // Secondary dock actions: PIN Switcher or Swipe Unlock Pill
    Row(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (authMode.usesPin || (showScanner && hasPinConfigured)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .softDeepCapsule(cornerRadius = 26.dp, elevation = 14.dp)
              .clickable(onClick = onSwitchToPin)
              .padding(horizontal = 16.dp, vertical = 10.dp)
              .testTag("lockscreen_use_pin_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Dialpad,
            contentDescription = null,
            tint = SLauncherColors.AccentPrimary,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.lockscreen_use_pin_btn),
            style = MaterialTheme.typography.labelLarge,
            color = SLauncherColors.TextPrimary,
          )
        }
      }

      if (!requiresAuthentication || authMode == LockAuthMode.SWIPE_ONLY) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .softDeepCapsule(cornerRadius = 26.dp, elevation = 14.dp)
              .clickable(onClick = onDirectUnlock)
              .padding(horizontal = 18.dp, vertical = 10.dp)
              .testTag("lockscreen_unlock_pill"),
        ) {
          Icon(
            imageVector = Icons.Rounded.KeyboardArrowUp,
            contentDescription = null,
            tint = SLauncherColors.AccentPrimary,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.lockscreen_swipe_unlock),
            style = MaterialTheme.typography.labelLarge,
            color = SLauncherColors.TextPrimary,
          )
        }
      }
    }
  }
}

@Composable
private fun LockScreenPinKeypad(
  hasPinConfigured: Boolean,
  canSwitchToBiometric: Boolean,
  onVerifyPin: (String) -> Boolean,
  onPinSuccess: () -> Unit,
  onSwitchToBiometric: () -> Unit,
  onSwipeUnlockFallback: () -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val shakeOffset = remember { Animatable(0f) }

  var enteredPin by remember { mutableStateOf("") }
  var wrongPinError by remember { mutableStateOf(false) }

  fun triggerWrongPinFeedback() {
    wrongPinError = true
    enteredPin = ""
    scope.launch {
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
      shakeOffset.snapTo(0f)
      for (x in listOf(-20f, 20f, -14f, 14f, -7f, 0f)) {
        shakeOffset.animateTo(x, tween(40))
      }
    }
  }

  fun onDigitTap(digit: String) {
    if (!hasPinConfigured) return
    wrongPinError = false
    if (enteredPin.length >= PinSecurityHelper.MAX_PIN_LENGTH) return
    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    val next = enteredPin + digit
    enteredPin = next

    if (next.length >= PinSecurityHelper.MIN_PIN_LENGTH && onVerifyPin(next)) {
      onPinSuccess()
      return
    }
    if (next.length == PinSecurityHelper.MAX_PIN_LENGTH) {
      triggerWrongPinFeedback()
    }
  }

  fun onSubmitExplicit() {
    if (!hasPinConfigured || enteredPin.length < PinSecurityHelper.MIN_PIN_LENGTH) {
      if (enteredPin.isNotEmpty()) triggerWrongPinFeedback()
      return
    }
    if (onVerifyPin(enteredPin)) {
      onPinSuccess()
    } else {
      triggerWrongPinFeedback()
    }
  }

  fun onBackspace() {
    wrongPinError = false
    if (enteredPin.isNotEmpty()) {
      enteredPin = enteredPin.dropLast(1)
    }
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("lockscreen_pin_keypad"),
  ) {
    Text(
      text =
        when {
          !hasPinConfigured -> stringResource(R.string.lockscreen_pin_not_set_hint)
          wrongPinError -> stringResource(R.string.lockscreen_wrong_pin)
          else -> stringResource(R.string.lockscreen_enter_pin)
        },
      style = MaterialTheme.typography.labelLarge,
      color =
        when {
          !hasPinConfigured || wrongPinError -> SLauncherColors.WarningSoft
          else -> SLauncherColors.TextPrimary
        },
      textAlign = TextAlign.Center,
    )

    // Luminous Dots
    Row(
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier =
        Modifier.padding(vertical = 4.dp)
          .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
    ) {
      for (i in 0 until PinSecurityHelper.MAX_PIN_LENGTH) {
        val filled = i < enteredPin.length
        val optional = i >= PinSecurityHelper.MIN_PIN_LENGTH
        Box(
          modifier =
            Modifier.size(if (filled) 15.dp else 12.dp)
              .clip(CircleShape)
              .background(
                when {
                  wrongPinError -> SLauncherColors.WarningSoft.copy(alpha = 0.7f)
                  filled -> SLauncherColors.AccentPrimary
                  optional -> Color.White.copy(alpha = 0.08f)
                  else -> Color.White.copy(alpha = 0.24f)
                }
              )
              .border(
                width = 1.dp,
                color =
                  if (filled) SLauncherColors.AccentPrimary
                  else SLauncherColors.GlassBorder,
                shape = CircleShape,
              )
        )
      }
    }

    if (hasPinConfigured) {
      val rows =
        listOf(
          listOf("1", "2", "3"),
          listOf("4", "5", "6"),
          listOf("7", "8", "9"),
        )
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        rows.forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            row.forEach { digit ->
              LockPinButton(
                label = digit,
                onClick = { onDigitTap(digit) },
                modifier = Modifier.testTag("lockscreen_pin_key_$digit"),
              )
            }
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
          val canSubmit = enteredPin.length >= PinSecurityHelper.MIN_PIN_LENGTH
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(66.dp)
                .clip(CircleShape)
                .background(
                  if (canSubmit) SLauncherColors.AccentContainer else Color.Transparent
                )
                .border(
                  width = 1.dp,
                  color = if (canSubmit) SLauncherColors.AccentPrimary else Color.Transparent,
                  shape = CircleShape,
                )
                .clickable(enabled = canSubmit) { onSubmitExplicit() }
                .testTag("lockscreen_pin_submit_key"),
          ) {
            if (canSubmit) {
              Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.action_done),
                tint = SLauncherColors.AccentPrimary,
                modifier = Modifier.size(24.dp),
              )
            }
          }

          LockPinButton(
            label = "0",
            onClick = { onDigitTap("0") },
            modifier = Modifier.testTag("lockscreen_pin_key_0"),
          )

          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(66.dp)
                .softDeepCapsule(cornerRadius = 33.dp, elevation = 14.dp)
                .clickable { onBackspace() }
                .testTag("lockscreen_pin_backspace_key"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.Backspace,
              contentDescription = stringResource(R.string.cd_clear_search),
              tint = SLauncherColors.TextPrimary,
              modifier = Modifier.size(22.dp),
            )
          }
        }
      }
    } else {
      // Safe fallback only if user chose PIN_ONLY but has not configured a PIN yet
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .softDeepCapsule(cornerRadius = 26.dp, elevation = 14.dp)
            .clickable(onClick = onSwipeUnlockFallback)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("lockscreen_unlock_pill"),
      ) {
        Icon(
          imageVector = Icons.Rounded.KeyboardArrowUp,
          contentDescription = null,
          tint = SLauncherColors.AccentPrimary,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.lockscreen_swipe_unlock),
          style = MaterialTheme.typography.labelLarge,
          color = SLauncherColors.TextPrimary,
        )
      }
    }

    if (canSwitchToBiometric) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .softDeepCapsule(
              cornerRadius = 26.dp,
              elevation = 14.dp,
              isActive = true,
              accentColor = SLauncherColors.AccentPrimary,
            )
            .clickable(onClick = onSwitchToBiometric)
            .padding(horizontal = 18.dp, vertical = 9.dp)
            .testTag("lockscreen_use_biometric_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Fingerprint,
          contentDescription = null,
          tint = SLauncherColors.AccentPrimary,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.lockscreen_use_biometric_btn),
          style = MaterialTheme.typography.labelLarge,
          color = SLauncherColors.TextPrimary,
        )
      }
    }
  }
}

@Composable
private fun LockPinButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .size(66.dp)
        .softDeepCapsule(cornerRadius = 33.dp, elevation = 14.dp)
        .clickable(onClick = onClick),
  ) {
    Text(
      text = label,
      style =
        MaterialTheme.typography.headlineSmall.copy(
          fontSize = 24.sp,
          fontWeight = FontWeight.Light,
          fontFeatureSettings = "tnum",
        ),
      color = SLauncherColors.TextPrimary,
    )
  }
}
