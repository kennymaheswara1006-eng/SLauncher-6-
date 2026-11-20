package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppModel
import com.example.ui.theme.SLauncherColors
import kotlin.math.roundToInt

/**
 * Loads and renders a downscaled app icon asynchronously from the repository LruCache.
 * Shows a subtle squircle placeholder while decoding on Dispatchers.IO so scrolling never janks.
 */
@Composable
fun AppIconImage(
  app: AppModel,
  loadIcon: suspend (AppModel, Int) -> ImageBitmap?,
  modifier: Modifier = Modifier,
  size: Dp = 40.dp,
) {
  val density = LocalDensity.current
  val targetPx = with(density) { size.toPx().roundToInt().coerceIn(64, 128) }

  val iconBitmap by
    produceState<ImageBitmap?>(initialValue = null, key1 = app.componentKey, key2 = targetPx) {
      value = loadIcon(app, targetPx)
    }

  val shape = RoundedCornerShape(12.dp)
  val currentBitmap = iconBitmap
  if (currentBitmap != null) {
    Image(
      bitmap = currentBitmap,
      contentDescription = stringResource(R.string.cd_app_icon, app.label),
      contentScale = ContentScale.Fit,
      modifier = modifier.size(size).clip(shape),
    )
  } else {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        modifier
          .size(size)
          .clip(shape)
          .background(SLauncherColors.PlaceholderIconBg),
    ) {
      Text(
        text = app.sectionLetter.toString(),
        color = SLauncherColors.TextSecondary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}
