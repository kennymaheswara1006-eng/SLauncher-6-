package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Dark Glassmorphism ambient color tokens designed for layered depth,
 * top-lit frosted glass borders, and high legibility over any wallpaper.
 */
object SLauncherColors {
  // Translucent Dark Glass surfaces
  val DarkGlassPill = Color(0xA6101520)
  val DarkGlassCard = Color(0xBA121724)
  val DarkGlassDrawer = Color(0xDC0D111A)
  val DarkGlassElevated = Color(0xE8181E2C)

  // Top-lit Glassmorphism gradient fills & borders
  val GlassSurfaceGradient =
    Brush.verticalGradient(
      colors = listOf(Color(0xC91C2334), Color(0xB3101521))
    )
  val GlassFloatingCardGradient =
    Brush.verticalGradient(
      colors = listOf(Color(0xCC1E2638), Color(0xB5111622))
    )
  val GlassBorderGradient =
    Brush.verticalGradient(
      colors = listOf(Color(0x52FFFFFF), Color(0x16FFFFFF))
    )

  // Hairline borders & subtle dividers
  val GlassBorder = Color(0x2EFFFFFF)
  val GlassBorderStrong = Color(0x4DFFFFFF)
  val GlassDivider = Color(0x1CFFFFFF)

  // Crisp typography over wallpaper & glass
  val TextPrimary = Color(0xFFF5F7FA)
  val TextSecondary = Color(0xFFBAC5D6)
  val TextMuted = Color(0xFF8894A8)
  val WallpaperTextShadow = Color(0xBD000000)

  // Luminous sky-cyan accent matching the SLauncher 6 brand emblem
  val AccentPrimary = Color(0xFF64B5F6)
  val AccentContainer = Color(0x3664B5F6)
  val BatteryCharging = Color(0xFF6EE7B7)
  val WarningSoft = Color(0xFFFBBF24)
  val PlaceholderIconBg = Color(0x26FFFFFF)

  // Expressive Weather icon palette
  val SunAmber = Color(0xFFFBBF24)
  val CloudBright = Color(0xFFF1F5F9)
  val CloudMuted = Color(0xFFCBD5E1)
  val RainCyan = Color(0xFF38BDF8)
  val StormIndigo = Color(0xFFA78BFA)
}
