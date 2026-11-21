package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.annotation.DrawableRes
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.data.WallpaperChoice
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private const val FULLSCREEN_CUSTOM_MAX_PX = 3840
private const val FULLSCREEN_BUNDLED_MAX_PX = 2880

/**
 * Renders the selected bundled wallpaper photo, a user-picked custom photo from app storage,
 * or stays transparent when SYSTEM is chosen so the device's live wallpaper shines through.
 * Uses full-resolution ARGB_8888 decoding (up to 3840px) and FilterQuality.High scaling for crisp HD visuals.
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
        val customBitmap by
          rememberLocalWallpaperBitmap(
            path = customPath,
            maxDimensionPx = FULLSCREEN_CUSTOM_MAX_PX,
          )
        val bmp = customBitmap
        if (bmp != null) {
          Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Box(modifier = Modifier.fillMaxSize())
        }
      }

      choice.drawableRes != null -> {
        val bundledBitmap by
          rememberBundledWallpaperBitmap(
            resId = choice.drawableRes,
            maxDimensionPx = FULLSCREEN_BUNDLED_MAX_PX,
          )
        val bmp = bundledBitmap
        if (bmp != null) {
          Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Box(modifier = Modifier.fillMaxSize())
        }
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

@Composable
fun rememberBundledWallpaperBitmap(
  @DrawableRes resId: Int,
  maxDimensionPx: Int = FULLSCREEN_BUNDLED_MAX_PX,
): State<ImageBitmap?> {
  val context = LocalContext.current.applicationContext
  return produceState<ImageBitmap?>(initialValue = null, key1 = resId, key2 = maxDimensionPx) {
    value =
      withContext(Dispatchers.IO) {
        decodeResourceSampled(context = context, resId = resId, maxDimensionPx = maxDimensionPx)
      }
  }
}

private fun decodeFileSampled(path: String, maxDimensionPx: Int): ImageBitmap? {
  val file = File(path)
  if (!file.exists()) return null
  return try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val source = ImageDecoder.createSource(file)
      val decoded =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
          decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
          val srcW = info.size.width
          val srcH = info.size.height
          val longest = max(srcW, srcH)
          if (longest > maxDimensionPx && longest > 0) {
            val scale = maxDimensionPx.toFloat() / longest.toFloat()
            decoder.setTargetSize(
              (srcW * scale).roundToInt().coerceAtLeast(1),
              (srcH * scale).roundToInt().coerceAtLeast(1),
            )
          }
        }
      return decoded.asImageBitmap()
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val longest = max(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longest / sample > maxDimensionPx * 2) {
      sample *= 2
    }
    val opts =
      BitmapFactory.Options().apply {
        inSampleSize = sample
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
    BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
  } catch (_: Exception) {
    null
  } catch (_: OutOfMemoryError) {
    null
  }
}

private fun decodeResourceSampled(
  context: Context,
  @DrawableRes resId: Int,
  maxDimensionPx: Int,
): ImageBitmap? {
  return try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val source = ImageDecoder.createSource(context.resources, resId)
      val decoded =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
          decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
          val srcW = info.size.width
          val srcH = info.size.height
          val longest = max(srcW, srcH)
          if (longest > maxDimensionPx && longest > 0) {
            val scale = maxDimensionPx.toFloat() / longest.toFloat()
            decoder.setTargetSize(
              (srcW * scale).roundToInt().coerceAtLeast(1),
              (srcH * scale).roundToInt().coerceAtLeast(1),
            )
          }
        }
      return decoded.asImageBitmap()
    }

    val bounds =
      BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        inScaled = false
      }
    BitmapFactory.decodeResource(context.resources, resId, bounds)
    val longest = max(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longest / sample > maxDimensionPx * 2) {
      sample *= 2
    }
    val opts =
      BitmapFactory.Options().apply {
        inSampleSize = sample
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
    BitmapFactory.decodeResource(context.resources, resId, opts)?.asImageBitmap()
  } catch (_: Exception) {
    null
  } catch (_: OutOfMemoryError) {
    null
  }
}
