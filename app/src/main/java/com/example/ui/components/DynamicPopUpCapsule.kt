package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.notifications.DynamicNotificationItem
import com.example.notifications.DynamicNotificationStore
import com.example.ui.theme.SLauncherColors
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Top Dynamic Island-style capsule supporting:
 * 1. Post-Unlock Dynamic Welcome Pop-Up: displays for 3 seconds when entering the unlocked phone,
 *    then morphs into a bouncy liquid water-droplet bead and pulls upward into the top edge.
 * 2. Polished Live Dynamic Notification capsule: real app icon, relative timestamp, ambient
 *    glass glow, subtle 3s countdown hairline, 1-by-1 smooth direct fade-out (no circle/dot morph),
 *    and swipe-right dismiss (no X button).
 */
@Composable
fun DynamicPopUpCapsule(
  welcomePopupEnabled: Boolean,
  welcomeMessage: String,
  welcomeTriggerId: Long,
  dynamicNotificationsEnabled: Boolean,
  isNotificationAccessGranted: Boolean,
  notifications: List<DynamicNotificationItem>,
  onRequestNotificationAccess: () -> Unit,
  onOpenNotification: (DynamicNotificationItem) -> Unit,
  onDismissNotification: (DynamicNotificationItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var showWelcomeBanner by remember { mutableStateOf(false) }
  val welcomeDropletProgress = remember { Animatable(0f) }
  var isNotifExpanded by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()

  // Show Welcome Pop-Up for 3 seconds, then morph into a bouncy droplet and suck up to top edge
  LaunchedEffect(welcomeTriggerId, welcomePopupEnabled) {
    if (welcomePopupEnabled && welcomeTriggerId > 0L) {
      welcomeDropletProgress.snapTo(0f)
      showWelcomeBanner = true
      delay(3000L)
      welcomeDropletProgress.animateTo(
        targetValue = 0.56f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
      )
      welcomeDropletProgress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
      )
      showWelcomeBanner = false
      welcomeDropletProgress.snapTo(0f)
    } else {
      showWelcomeBanner = false
      welcomeDropletProgress.snapTo(0f)
    }
  }

  val latestNotification = notifications.firstOrNull()

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier.fillMaxWidth(),
  ) {
    // 1. 3-Second Dynamic Welcome Pop-Up with Liquid Droplet Exit
    AnimatedVisibility(
      visible = showWelcomeBanner,
      enter =
        slideInVertically(
          animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
          initialOffsetY = { -it / 2 },
        ) + fadeIn(tween(240)),
      exit = fadeOut(tween(120)),
    ) {
      val progress = welcomeDropletProgress.value
      val contentAlpha = (1f - (progress / 0.28f)).coerceIn(0f, 1f)
      val jellyScaleY =
        if (progress in 0.05f..0.62f) {
          1f + 0.14f * sin(((progress - 0.05f) / 0.57f) * PI).toFloat()
        } else {
          1f
        }

      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.fillMaxWidth()
            .padding(bottom = 8.dp)
            .graphicsLayer { scaleY = jellyScaleY }
            .testTag("dynamic_welcome_popup"),
      ) {
        LiquidDropletCanvasBackdrop(
          exitProgress = progress,
          accentBorder = true,
          timerFraction = 0f,
          modifier = Modifier.matchParentSize(),
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).alpha(contentAlpha),
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(28.dp)
                .clip(CircleShape)
                .background(SLauncherColors.AccentContainer)
                .border(0.8.dp, SLauncherColors.AccentPrimary.copy(alpha = 0.5f), CircleShape),
          ) {
            Icon(
              imageVector = Icons.Rounded.AutoAwesome,
              contentDescription = null,
              tint = SLauncherColors.AccentPrimary,
              modifier = Modifier.size(16.dp),
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = welcomeMessage,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = SLauncherColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    // 2. Dynamic Notification Capsule (1-by-1 3s smooth direct fade-out, or swipe right to dismiss)
    AnimatedVisibility(
      visible =
        !showWelcomeBanner &&
          dynamicNotificationsEnabled &&
          (latestNotification != null || !isNotificationAccessGranted),
      enter =
        slideInVertically(
          animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
          initialOffsetY = { -it / 2 },
        ) + fadeIn(tween(220)),
      exit = fadeOut(tween(160)),
    ) {
      if (latestNotification != null) {
        val swipeOffsetX = remember(latestNotification.key) { Animatable(0f) }
        val notifExitProgress = remember(latestNotification.key) { Animatable(0f) }
        val timerRemaining = remember(latestNotification.key) { Animatable(1f) }

        // Auto-dismiss each notification one-by-one after 3 seconds cleanly (no small circle morph)
        LaunchedEffect(latestNotification.key, isNotifExpanded) {
          if (!isNotifExpanded) {
            notifExitProgress.snapTo(0f)
            timerRemaining.snapTo(1f)
            coroutineScope {
              val countdownJob = launch {
                timerRemaining.animateTo(
                  targetValue = 0f,
                  animationSpec = tween(durationMillis = 3000, easing = LinearEasing),
                )
              }
              countdownJob.join()
            }
            notifExitProgress.animateTo(
              targetValue = 1f,
              animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            )
            isNotifExpanded = false
            onDismissNotification(latestNotification)
          } else {
            timerRemaining.snapTo(0f)
          }
        }

        val exitVal = notifExitProgress.value
        val exitAlpha = (1f - exitVal).coerceIn(0f, 1f)
        val exitScale = lerp(1f, 0.93f, exitVal)
        val swipeAlpha = (1f - (swipeOffsetX.value / 520f)).coerceIn(0f, 1f)

        AnimatedContent(
          targetState = latestNotification,
          transitionSpec = {
            (slideInVertically(tween(240)) { -it / 3 } +
              scaleIn(initialScale = 0.94f, animationSpec = tween(240)) +
              fadeIn(tween(240))) togetherWith fadeOut(tween(140))
          },
          label = "dynamic_notification_queue",
        ) { currentItem ->
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
                .graphicsLayer {
                  alpha = swipeAlpha * exitAlpha
                  scaleX = exitScale
                  scaleY = exitScale
                  translationY = -exitVal * 26.dp.toPx()
                }
                .animateContentSize(
                  animationSpec =
                    spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow)
                )
                .pointerInput(currentItem.key) {
                  detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                      change.consume()
                      coroutineScope.launch {
                        val next = (swipeOffsetX.value + dragAmount).coerceAtLeast(0f)
                        swipeOffsetX.snapTo(next)
                      }
                    },
                    onDragEnd = {
                      coroutineScope.launch {
                        if (swipeOffsetX.value > 150f) {
                          swipeOffsetX.animateTo(
                            targetValue = 950f,
                            animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing),
                          )
                          isNotifExpanded = false
                          onDismissNotification(currentItem)
                        } else {
                          swipeOffsetX.animateTo(
                            targetValue = 0f,
                            animationSpec =
                              spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium),
                          )
                        }
                      }
                    },
                    onDragCancel = {
                      coroutineScope.launch { swipeOffsetX.animateTo(0f, spring()) }
                    },
                  )
                }
                .clickable(enabled = exitVal < 0.1f) { isNotifExpanded = !isNotifExpanded }
                .testTag("dynamic_notification_capsule"),
          ) {
            NotificationGlassBackdrop(
              timerFraction = if (!isNotifExpanded && exitVal < 0.05f) timerRemaining.value else 0f,
              modifier = Modifier.matchParentSize(),
            )

            Column(
              modifier =
                Modifier.fillMaxWidth()
                  .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                NotificationAppIcon(
                  item = currentItem,
                  size = 38.dp,
                  showLiveDot = true,
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(
                      text = currentItem.appName,
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
                      text = formatRelativeNotificationTime(context, currentItem.postTime),
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                      color = SLauncherColors.TextMuted,
                      maxLines = 1,
                    )
                  }

                  Spacer(modifier = Modifier.height(1.dp))

                  Text(
                    text = currentItem.title,
                    style =
                      MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                      ),
                    color = SLauncherColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )

                  if (currentItem.text.isNotBlank()) {
                    Text(
                      text = currentItem.text,
                      style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                      color = SLauncherColors.TextSecondary,
                      maxLines = if (isNotifExpanded) 4 else 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }

                if (notifications.size > 1) {
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "+${notifications.size - 1}",
                    style =
                      MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                      ),
                    color = SLauncherColors.AccentPrimary,
                    modifier =
                      Modifier.clip(CircleShape)
                        .background(SLauncherColors.AccentContainer)
                        .border(0.8.dp, SLauncherColors.AccentPrimary.copy(alpha = 0.4f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                  )
                }
              }

              if (isNotifExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = stringResource(R.string.lockscreen_swipe_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = SLauncherColors.TextMuted,
                  )
                  if (currentItem.contentIntent != null) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier =
                        Modifier.clip(RoundedCornerShape(50))
                          .background(SLauncherColors.AccentContainer)
                          .border(
                            0.8.dp,
                            SLauncherColors.AccentPrimary.copy(alpha = 0.5f),
                            RoundedCornerShape(50),
                          )
                          .clickable { onOpenNotification(currentItem) }
                          .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = SLauncherColors.AccentPrimary,
                        modifier = Modifier.size(14.dp),
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = stringResource(R.string.action_open),
                        style = MaterialTheme.typography.labelLarge,
                        color = SLauncherColors.AccentPrimary,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      } else if (!isNotificationAccessGranted) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.clip(RoundedCornerShape(50))
              .background(SLauncherColors.GlassSurfaceGradient)
              .border(1.dp, SLauncherColors.GlassBorderGradient, RoundedCornerShape(50))
              .clickable { onRequestNotificationAccess() }
              .padding(horizontal = 14.dp, vertical = 8.dp)
              .testTag("grant_dynamic_notification_pill"),
        ) {
          Icon(
            imageVector = Icons.Rounded.NotificationsActive,
            contentDescription = null,
            tint = SLauncherColors.AccentPrimary,
            modifier = Modifier.size(15.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.dynamic_notif_grant_prompt),
            style = MaterialTheme.typography.labelSmall,
            color = SLauncherColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/**
 * Renders the notification's actual source application icon safely cached in memory,
 * falling back to the SLauncher 6 emblem for preview notifications or a soft bell badge.
 */
@Composable
fun NotificationAppIcon(
  item: DynamicNotificationItem,
  size: Dp = 38.dp,
  showLiveDot: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val iconBitmap by
    produceState<ImageBitmap?>(initialValue = null, item.packageName, item.isPreview) {
      value =
        if (!item.isPreview) {
          DynamicNotificationStore.loadPackageIcon(context, item.packageName, 96)
        } else {
          null
        }
    }

  val containerShape = RoundedCornerShape(12.dp)
  Box(modifier = modifier.size(size)) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.fillMaxSize()
          .clip(containerShape)
          .background(SLauncherColors.AccentContainer)
          .border(0.8.dp, SLauncherColors.GlassBorderStrong, containerShape),
    ) {
      when {
        iconBitmap != null -> {
          Image(
            bitmap = iconBitmap!!,
            contentDescription = item.appName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(containerShape),
          )
        }
        item.isPreview -> {
          Image(
            painter = painterResource(id = R.drawable.slauncher_icon_fg_1795168930289),
            contentDescription = item.appName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(containerShape),
          )
        }
        else -> {
          Icon(
            imageVector = Icons.Rounded.NotificationsActive,
            contentDescription = item.appName,
            tint = SLauncherColors.AccentPrimary,
            modifier = Modifier.size(size * 0.52f),
          )
        }
      }
    }

    if (showLiveDot) {
      Box(
        modifier =
          Modifier.align(Alignment.TopEnd)
            .offset(x = 2.dp, y = (-2).dp)
            .size(9.dp)
            .clip(CircleShape)
            .background(SLauncherColors.AccentPrimary)
            .border(1.5.dp, Color(0xFF101622), CircleShape)
      )
    }
  }
}

fun formatRelativeNotificationTime(context: Context, postTimeMillis: Long): String {
  val diffMinutes = ((System.currentTimeMillis() - postTimeMillis) / 60_000L).coerceAtLeast(0L)
  return when {
    diffMinutes < 1L -> context.getString(R.string.dynamic_notif_now)
    diffMinutes < 60L -> context.getString(R.string.notif_time_minutes, diffMinutes.toInt())
    else -> context.getString(R.string.notif_time_hours, (diffMinutes / 60L).toInt())
  }
}

/**
 * GPU-accelerated Canvas renderer for the liquid water-droplet exit animation.
 * Includes ambient cyan glow behind the icon, top specular highlight, optional 3s liquid
 * countdown hairline, and smooth gooey neck suction into the top edge.
 */
@Composable
private fun LiquidDropletCanvasBackdrop(
  exitProgress: Float,
  accentBorder: Boolean,
  timerFraction: Float,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    if (exitProgress >= 0.99f) return@Canvas

    val fullW = size.width
    val fullH = size.height
    val beadDiameter = minOf(fullH * 0.88f, 38.dp.toPx()).coerceAtLeast(24.dp.toPx())

    val morphT = (exitProgress / 0.56f).coerceIn(0f, 1f)
    val pullT = ((exitProgress - 0.48f) / 0.52f).coerceIn(0f, 1f)

    val currentWidth = lerp(fullW, beadDiameter, morphT) * (1f - pullT * 0.72f)
    val currentHeight = lerp(fullH, beadDiameter, morphT) * (1f - pullT * 0.55f)
    val centerX = fullW / 2f
    val restCenterY = fullH / 2f
    val currentCenterY = lerp(restCenterY, -beadDiameter * 0.65f, pullT)

    val left = centerX - currentWidth / 2f
    val top = currentCenterY - currentHeight / 2f
    val corner = minOf(currentWidth, currentHeight) / 2f
    val alphaFactor = (1f - pullT * 0.92f).coerceIn(0f, 1f)

    val glassBrush =
      Brush.verticalGradient(
        colors =
          listOf(
            Color(0xEE232D42).copy(alpha = 0.94f * alphaFactor),
            Color(0xEE101622).copy(alpha = 0.97f * alphaFactor),
          ),
        startY = top,
        endY = top + currentHeight,
      )

    // Liquid meniscus neck connecting droplet to top bezel during suction phase
    if (morphT > 0.65f && pullT < 0.95f) {
      val neckStrength = ((morphT - 0.65f) / 0.35f).coerceIn(0f, 1f) * (1f - pullT)
      val neckTopHalfW = (currentWidth * 0.62f) * neckStrength
      val beadRadius = currentWidth / 2f
      val anchorY = -4.dp.toPx()

      if (currentCenterY > anchorY) {
        val neckPath =
          Path().apply {
            moveTo(centerX - neckTopHalfW, anchorY)
            cubicTo(
              centerX - neckTopHalfW * 0.45f,
              (anchorY + currentCenterY) * 0.45f,
              centerX - beadRadius * 0.92f,
              currentCenterY - currentHeight * 0.25f,
              centerX - beadRadius * 0.85f,
              currentCenterY,
            )
            lineTo(centerX + beadRadius * 0.85f, currentCenterY)
            cubicTo(
              centerX + beadRadius * 0.92f,
              currentCenterY - currentHeight * 0.25f,
              centerX + neckTopHalfW * 0.45f,
              (anchorY + currentCenterY) * 0.45f,
              centerX + neckTopHalfW,
              anchorY,
            )
            close()
          }
        drawPath(path = neckPath, brush = glassBrush)
      }
    }

    // Main frosted dark glass capsule
    drawRoundRect(
      brush = glassBrush,
      topLeft = Offset(left, top),
      size = Size(currentWidth, currentHeight),
      cornerRadius = CornerRadius(corner, corner),
    )

    // Subtle ambient cyan glow on the left icon side when expanded as full capsule
    if (morphT < 0.3f) {
      val glowAlpha = (1f - morphT / 0.3f) * 0.22f * alphaFactor
      val glowCenter = Offset(left + 32.dp.toPx(), restCenterY)
      val glowRadius = 56.dp.toPx()
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(SLauncherColors.AccentPrimary.copy(alpha = glowAlpha), Color.Transparent),
            center = glowCenter,
            radius = glowRadius,
          ),
        radius = glowRadius,
        center = glowCenter,
      )
    }

    // Top-lit frosted border highlight
    val borderBrush =
      Brush.verticalGradient(
        colors =
          listOf(
            if (accentBorder) SLauncherColors.AccentPrimary.copy(alpha = 0.72f * alphaFactor)
            else Color.White.copy(alpha = 0.44f * alphaFactor),
            Color.White.copy(alpha = 0.10f * alphaFactor),
          ),
        startY = top,
        endY = top + currentHeight,
      )
    drawRoundRect(
      brush = borderBrush,
      topLeft = Offset(left, top),
      size = Size(currentWidth, currentHeight),
      cornerRadius = CornerRadius(corner, corner),
      style = Stroke(width = 1.dp.toPx()),
    )

    // Subtle liquid countdown hairline along bottom center
    if (timerFraction > 0.02f && morphT < 0.05f) {
      val usableW = (currentWidth - 44.dp.toPx()).coerceAtLeast(0f) * timerFraction
      val lineY = top + currentHeight - 2.dp.toPx()
      drawLine(
        color = SLauncherColors.AccentPrimary.copy(alpha = 0.65f * alphaFactor),
        start = Offset(centerX - usableW / 2f, lineY),
        end = Offset(centerX + usableW / 2f, lineY),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
      )
    }

    // Specular water-drop gleam as it forms a droplet
    if (morphT > 0.45f && alphaFactor > 0.1f) {
      val gleamAlpha = ((morphT - 0.45f) / 0.55f).coerceIn(0f, 1f) * alphaFactor * 0.45f
      drawCircle(
        color = Color.White.copy(alpha = gleamAlpha),
        radius = (currentWidth * 0.16f).coerceAtLeast(1.dp.toPx()),
        center = Offset(centerX - currentWidth * 0.18f, currentCenterY - currentHeight * 0.18f),
      )
    }
  }
}

/**
 * Full-capsule frosted glass backdrop for live Dynamic Notifications with ambient cyan glow,
 * top-lit hairline border, and 3-second countdown line (stays full capsule shape until fade-out).
 */
@Composable
private fun NotificationGlassBackdrop(
  timerFraction: Float,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    val fullW = size.width
    val fullH = size.height
    val corner = minOf(24.dp.toPx(), fullH / 2f)

    val glassBrush =
      Brush.verticalGradient(
        colors = listOf(Color(0xEE232D42), Color(0xEE101622)),
      )

    drawRoundRect(
      brush = glassBrush,
      topLeft = Offset.Zero,
      size = size,
      cornerRadius = CornerRadius(corner, corner),
    )

    val glowCenter = Offset(32.dp.toPx(), fullH / 2f)
    val glowRadius = 56.dp.toPx()
    drawCircle(
      brush =
        Brush.radialGradient(
          colors = listOf(SLauncherColors.AccentPrimary.copy(alpha = 0.22f), Color.Transparent),
          center = glowCenter,
          radius = glowRadius,
        ),
      radius = glowRadius,
      center = glowCenter,
    )

    drawRoundRect(
      brush =
        Brush.verticalGradient(
          colors = listOf(Color.White.copy(alpha = 0.44f), Color.White.copy(alpha = 0.10f)),
        ),
      topLeft = Offset.Zero,
      size = size,
      cornerRadius = CornerRadius(corner, corner),
      style = Stroke(width = 1.dp.toPx()),
    )

    if (timerFraction > 0.02f) {
      val usableW = (fullW - 44.dp.toPx()).coerceAtLeast(0f) * timerFraction
      val lineY = fullH - 2.dp.toPx()
      val centerX = fullW / 2f
      drawLine(
        color = SLauncherColors.AccentPrimary.copy(alpha = 0.65f),
        start = Offset(centerX - usableW / 2f, lineY),
        end = Offset(centerX + usableW / 2f, lineY),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
      )
    }
  }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
  start + (stop - start) * fraction
