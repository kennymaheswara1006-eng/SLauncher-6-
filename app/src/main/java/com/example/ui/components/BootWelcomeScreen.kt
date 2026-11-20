package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BootObsidianBlack = Color(0xFF09090B)
private val PulseIceBlue = Color(0xFFDCEFFF)
private val PulseCyanGlow = Color(0xFF8CCBFF)
private val PulseTrackBg = Color(0x28DCEFFF)

/**
 * Lightweight "Minimalist Futuristic Pulse" Boot-Up Welcome overlay (~2.2s total).
 * Built purely with Jetpack Compose Canvas & Animatable (zero bitmaps, video, or Lottie)
 * to remain super light and responsive on 2GB RAM devices.
 *
 * Timeline:
 * - 0.0s - 0.8s: Deep obsidian backdrop (#09090B) + geometric bluish-white pulsing ring spring-in around "S6"
 * - 0.8s - 1.5s: "SLauncher 6" fade-in + slide-up + smooth thin progress bar fill
 * - 1.5s - 2.2s: Smooth cross-fade out into HomeScreen
 */
@Composable
fun BootWelcomeScreen(
  onAnimationFinished: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Phase 1 (0.0s - 0.8s): Logo + Geometric Pulse Ring
  val ringScale = remember { Animatable(0.45f) }
  val logoAlpha = remember { Animatable(0f) }
  val pulseWave = remember { Animatable(0f) }
  val ringSweepAngle = remember { Animatable(-90f) }

  // Phase 2 (0.8s - 1.5s): Brand Title + Thin Progress Line
  val titleAlpha = remember { Animatable(0f) }
  val titleOffsetY = remember { Animatable(24f) }
  val progressFill = remember { Animatable(0f) }

  // Phase 3 (1.5s - 2.2s): Cross-fade Exit
  val containerAlpha = remember { Animatable(1f) }

  LaunchedEffect(Unit) {
    // 0.0s - 0.8s: Spring scale-in + geometric ring pulse
    coroutineScope {
      launch {
        logoAlpha.animateTo(1f, animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing))
      }
      launch {
        ringScale.animateTo(
          targetValue = 1f,
          animationSpec =
            spring(
              dampingRatio = 0.58f,
              stiffness = Spring.StiffnessMediumLow,
            ),
        )
      }
      launch {
        pulseWave.animateTo(1f, animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing))
      }
      launch {
        ringSweepAngle.animateTo(135f, animationSpec = tween(durationMillis = 1500, easing = LinearEasing))
      }
    }

    // 0.8s - 1.5s: Brand title slide-up + fade-in + thin progress fill
    coroutineScope {
      launch {
        titleAlpha.animateTo(1f, animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing))
      }
      launch {
        titleOffsetY.animateTo(0f, animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing))
      }
      launch {
        progressFill.animateTo(1f, animationSpec = tween(durationMillis = 680, easing = FastOutSlowInEasing))
      }
    }

    // Brief settle then 1.5s - 2.2s: Smooth cross-fade out (total <= 2,200 ms)
    delay(40L)
    containerAlpha.animateTo(
      targetValue = 0f,
      animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
    )

    onAnimationFinished()
  }

  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .fillMaxSize()
        .graphicsLayer { alpha = containerAlpha.value }
        .background(BootObsidianBlack)
        .testTag("boot_welcome_screen"),
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(horizontal = 32.dp),
    ) {
      // Geometric Bluish-White Ring + Minimalist "S6" Logo Mark
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.size(136.dp).graphicsLayer {
            val s = ringScale.value
            scaleX = s
            scaleY = s
            alpha = logoAlpha.value
          },
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val strokeThin = 1.6.dp.toPx()
          val strokeAccent = 2.4.dp.toPx()
          val radius = size.minDimension * 0.38f
          val center = Offset(size.width / 2f, size.height / 2f)

          // Soft radial ambient glow behind ring
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(PulseCyanGlow.copy(alpha = 0.22f), Color.Transparent),
                center = center,
                radius = radius * 1.45f,
              ),
            radius = radius * 1.45f,
            center = center,
          )

          // Expanding outer pulse ripple (0.0s - 0.8s)
          val waveProgress = pulseWave.value
          if (waveProgress in 0.01f..0.99f) {
            drawCircle(
              color = PulseIceBlue.copy(alpha = (1f - waveProgress) * 0.45f),
              radius = radius * (1f + waveProgress * 0.36f),
              center = center,
              style = Stroke(width = 1.2.dp.toPx()),
            )
          }

          // Crisp thin primary geometric ring
          drawCircle(
            color = PulseIceBlue.copy(alpha = 0.35f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeThin),
          )

          // Futuristic rotating luminous arc segment
          val arcDiameter = radius * 2f
          val arcTopLeft = Offset(center.x - radius, center.y - radius)
          drawArc(
            brush =
              Brush.sweepGradient(
                colors =
                  listOf(
                    Color.Transparent,
                    PulseCyanGlow.copy(alpha = 0.65f),
                    PulseIceBlue,
                    Color.Transparent,
                  ),
                center = center,
              ),
            startAngle = ringSweepAngle.value,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = Size(arcDiameter, arcDiameter),
            style = Stroke(width = strokeAccent, cap = StrokeCap.Round),
          )
        }

        Text(
          text = stringResource(R.string.boot_logo_mark),
          style =
            MaterialTheme.typography.headlineMedium.copy(
              fontSize = 34.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 2.sp,
            ),
          color = PulseIceBlue,
        )
      }

      Spacer(modifier = Modifier.height(22.dp))

      // Phase 2: "SLauncher 6" Fade-In & Slide-Up
      Text(
        text = stringResource(R.string.boot_brand_name),
        style =
          MaterialTheme.typography.titleLarge.copy(
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 3.2.sp,
          ),
        color = PulseIceBlue,
        modifier =
          Modifier.graphicsLayer {
            alpha = titleAlpha.value
            translationY = titleOffsetY.value
          },
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Thin Futuristic Progress Line
      Canvas(
        modifier =
          Modifier.width(128.dp)
            .height(3.dp)
            .graphicsLayer { alpha = titleAlpha.value },
      ) {
        val corner = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
          color = PulseTrackBg,
          topLeft = Offset.Zero,
          size = size,
          cornerRadius = corner,
        )
        val fillWidth = (size.width * progressFill.value.coerceIn(0f, 1f))
        if (fillWidth > 0f) {
          drawRoundRect(
            brush = Brush.horizontalGradient(listOf(PulseCyanGlow, PulseIceBlue)),
            topLeft = Offset.Zero,
            size = Size(fillWidth, size.height),
            cornerRadius = corner,
          )
        }
      }
    }
  }
}
