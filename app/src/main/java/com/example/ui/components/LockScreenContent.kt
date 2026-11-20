package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.LauncherSettings
import com.example.data.WeatherUiState
import com.example.notifications.DynamicNotificationItem
import com.example.ui.theme.SLauncherColors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Full-screen native Lock Screen UI rendered inside LockScreenActivity (no floating overlay).
 * Welcome ("Hello") pop-up intentionally appears only after unlocking into the phone.
 * Notifications feature Glassmorphism cards, real app icons, timestamps, and swipe-right dismissal (no X button).
 */
@Composable
fun LockScreenContent(
  settings: LauncherSettings,
  weatherState: WeatherUiState,
  isNotificationAccessGranted: Boolean,
  notifications: List<DynamicNotificationItem>,
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

  val unlockProgress = ((-dragOffsetY.value) / 420f).coerceIn(0f, 1f)
  val contentAlpha = (1f - (unlockProgress * 0.8f)).coerceIn(0.15f, 1f)

  val scrim =
    Brush.verticalGradient(
      0.0f to Color.Black.copy(alpha = 0.36f),
      0.5f to Color.Black.copy(alpha = 0.20f),
      1.0f to Color.Black.copy(alpha = 0.45f),
    )

  Box(
    contentAlignment = Alignment.TopCenter,
    modifier =
      Modifier.fillMaxSize()
        .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
        .alpha(contentAlpha)
        .pointerInput(Unit) {
          detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
              change.consume()
              coroutineScope.launch {
                val next = (dragOffsetY.value + dragAmount).coerceAtMost(0f)
                dragOffsetY.snapTo(next)
              }
            },
            onDragEnd = {
              coroutineScope.launch {
                if (dragOffsetY.value < -220f) {
                  dragOffsetY.animateTo(-1400f, tween(240))
                  onUnlock()
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

    Box(
      contentAlignment = Alignment.TopCenter,
      modifier =
        Modifier.fillMaxSize()
          .background(scrim)
          .statusBarsPadding()
          .navigationBarsPadding(),
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
          Modifier.fillMaxSize()
            .widthIn(max = 560.dp)
            .padding(horizontal = 24.dp, vertical = 20.dp),
      ) {
        // Dynamic Notifications capsule only (Hello/Welcome pop-up appears after unlocking)
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

        Spacer(modifier = Modifier.height(22.dp))

        ClockDateBatteryHeader(
          weatherState = weatherState,
          onRequestLocationPermission = onRequestLocationPermission,
          onRefreshWeather = onRefreshWeather,
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (notifications.isEmpty()) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SLauncherColors.GlassSurfaceGradient)
                .border(1.dp, SLauncherColors.GlassBorderGradient, RoundedCornerShape(22.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.DoneAll,
              contentDescription = null,
              tint = SLauncherColors.AccentPrimary,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = stringResource(R.string.lockscreen_no_notifications),
              style = MaterialTheme.typography.bodyMedium,
              color = SLauncherColors.TextSecondary,
            )
          }
        } else {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = SLauncherColors.AccentPrimary,
                modifier = Modifier.size(15.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = stringResource(R.string.lockscreen_notifications_header, notifications.size),
                style = MaterialTheme.typography.labelLarge,
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
              val cardShape = RoundedCornerShape(24.dp)

              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                  Modifier.fillMaxWidth()
                    .offset { IntOffset(itemSwipeX.value.roundToInt(), 0) }
                    .alpha(itemAlpha)
                    .clip(cardShape)
                    .background(SLauncherColors.GlassFloatingCardGradient)
                    .border(1.dp, SLauncherColors.GlassBorderGradient, cardShape)
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
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("lockscreen_notif_${item.key}"),
              ) {
                NotificationAppIcon(
                  item = item,
                  size = 40.dp,
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                      ),
                    color = SLauncherColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  if (item.text.isNotBlank()) {
                    Text(
                      text = item.text,
                      style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                      color = SLauncherColors.TextSecondary,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }
            }
          }
        }

        if (notifications.isEmpty()) {
          Spacer(modifier = Modifier.weight(1f))
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
          modifier =
            Modifier.padding(bottom = 12.dp)
              .clip(RoundedCornerShape(50))
              .background(SLauncherColors.GlassSurfaceGradient)
              .border(1.dp, SLauncherColors.GlassBorderStrong, RoundedCornerShape(50))
              .clickable { onUnlock() }
              .padding(horizontal = 20.dp, vertical = 10.dp)
              .testTag("lockscreen_unlock_pill"),
        ) {
          Icon(
            imageVector = Icons.Rounded.KeyboardArrowUp,
            contentDescription = null,
            tint = SLauncherColors.AccentPrimary,
            modifier = Modifier.size(20.dp),
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
