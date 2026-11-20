package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.data.WallpaperChoice
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Renders the selected bundled wallpaper photo, a user-picked custom photo from app storage,
 * or stays transparent when SYSTEM is chosen so the device's live wallpaper shines through.
 */
@Composable
fun WallpaperBackdrop(
  wallpaperChoice: WallpaperChoice,
  customWallpaperPath: String? = null,
  modifier: Modifier = Modifier,
) {
  val containerModifier =
    if (wallpaperChoice == WallpaperChoice.SYSTEM) {
      modifier.fillMaxSize()
    } else {
      modifier.fillMaxSize().background(Color(0xFF090C14))
    }
  Crossfade(
    targetState = wallpaperChoice to customWallpaperPath,
    animationSpec = tween(durationMillis = 360),
    label = "wallpaper_crossfade",
    modifier = containerModifier,
  ) { (choice, customPath) ->
    when {
      choice == WallpaperChoice.CUSTOM && !customPath.isNullOrBlank() -> {
        val customBitmap by rememberLocalWallpaperBitmap(path = customPath, maxDimensionPx = 1600)
        val bmp = customBitmap
        if (bmp != null) {
          Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Box(modifier = Modifier.fillMaxSize())
        }
      }

      choice.drawableRes != null -> {
        Image(
          painter = painterResource(id = choice.drawableRes),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      }

      else -> {
        Box(modifier = Modifier.fillMaxSize())
      }
    }
  }
}

@Composable
fun rememberLocalWallpaperBitmap(
  path: String,
  maxDimensionPx: Int,
): State<ImageBitmap?> {
  return produceState<ImageBitmap?>(initialValue = null, key1 = path, key2 = maxDimensionPx) {
    value =
      withContext(Dispatchers.IO) {
        decodeFileSampled(path = path, maxDimensionPx = maxDimensionPx)
      }
  }
}

private fun decodeFileSampled(path: String, maxDimensionPx: Int): ImageBitmap? {
  val file = File(path)
  if (!file.exists()) return null
  return try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val longest = max(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longest / sample > maxDimensionPx * 2) {
      sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
  } catch (_: Exception) {
    null
  } catch (_: OutOfMemoryError) {
    null
  }
}
