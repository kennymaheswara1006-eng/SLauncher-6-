package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable Soft Modern Capsule styling modifier with deep layered shadows,
 * soft cushion-like top specular rim, and subtle inner depth vignette.
 */
fun Modifier.softDeepCapsule(
  cornerRadius: Dp = 28.dp,
  elevation: Dp = 20.dp,
  isActive: Boolean = false,
  accentColor: Color = SLauncherColors.AccentPrimary,
  surfaceBrush: Brush =
    if (isActive) {
      Brush.verticalGradient(
        colors = listOf(accentColor.copy(alpha = 0.34f), Color(0xEB152136))
      )
    } else {
      Brush.verticalGradient(
        colors = listOf(SLauncherColors.SoftCapsuleSurfaceTop, SLauncherColors.SoftCapsuleSurfaceBottom)
      )
    },
): Modifier {
  val shape: Shape = RoundedCornerShape(cornerRadius)
  val borderBrush =
    if (isActive) {
      Brush.verticalGradient(
        colors = listOf(accentColor.copy(alpha = 0.85f), accentColor.copy(alpha = 0.25f))
      )
    } else {
      Brush.verticalGradient(
        colors = listOf(SLauncherColors.SoftCapsuleRimHighlight, Color(0x1CFFFFFF), Color(0x0D000000))
      )
    }

  return this
    .shadow(
      elevation = elevation,
      shape = shape,
      clip = false,
      ambientColor = if (isActive) accentColor.copy(alpha = 0.45f) else SLauncherColors.DeepShadowAmbient,
      spotColor = SLauncherColors.DeepShadowSpot,
    )
    .clip(shape)
    .background(surfaceBrush)
    .drawWithContent {
      drawContent()
      val cornerPx = cornerRadius.toPx().coerceAtMost(size.minDimension / 2f)
      // Subtle soft inner bottom shadow for cushion-like capsule depth
      drawRoundRect(
        brush =
          Brush.verticalGradient(
            0.0f to Color(0x1FFFFFFF),
            0.22f to Color.Transparent,
            0.78f to Color.Transparent,
            1.0f to SLauncherColors.SoftCapsuleInnerShadow,
          ),
        topLeft = Offset.Zero,
        size = size,
        cornerRadius = CornerRadius(cornerPx, cornerPx),
        style = Stroke(width = 1.5.dp.toPx()),
      )
    }
    .border(width = 1.dp, brush = borderBrush, shape = shape)
}
