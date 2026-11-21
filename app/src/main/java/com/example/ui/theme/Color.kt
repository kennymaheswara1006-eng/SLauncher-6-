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
      colors = listOf(Color(0xD41F293D), Color(0xBD101624))
    )
  val GlassFloatingCardGradient =
    Brush.verticalGradient(
      colors = listOf(Color(0xD9232E45), Color(0xBF101625))
    )
  val GlassBorderGradient =
    Brush.verticalGradient(
      colors = listOf(Color(0x66FFFFFF), Color(0x1E64B5F6), Color(0x14FFFFFF))
    )

  // Hairline borders & subtle dividers
  val GlassBorder = Color(0x33FFFFFF)
  val GlassBorderStrong = Color(0x57FFFFFF)
  val GlassDivider = Color(0x1FFFFFFF)

  // Crisp typography over wallpaper & glass
  val TextPrimary = Color(0xFFF6F8FC)
  val TextSecondary = Color(0xFFC2CDDF)
  val TextMuted = Color(0xFF8C9AB0)
  val WallpaperTextShadow = Color(0xCC000000)

  // Luminous sky-cyan accent matching the SLauncher 6 brand emblem
  val AccentPrimary = Color(0xFF64B5F6)
  val AccentGlow = Color(0xFF38BDF8)
  val AccentContainer = Color(0x3B64B5F6)
  val BatteryCharging = Color(0xFF6EE7B7)
  val WarningSoft = Color(0xFFFBBF24)
  val DestructiveSoft = Color(0xFFF87171)
  val PlaceholderIconBg = Color(0x28FFFFFF)

  // Expressive Weather icon palette
  val SunAmber = Color(0xFFFBBF24)
  val CloudBright = Color(0xFFF1F5F9)
  val CloudMuted = Color(0xFFCBD5E1)
  val RainCyan = Color(0xFF38BDF8)
  val StormIndigo = Color(0xFFA78BFA)

  // Soft Modern Capsule with Deep Shadows palette
  val SoftCapsuleSurfaceTop = Color(0xF0253147)
  val SoftCapsuleSurfaceBottom = Color(0xF2121825)
  val SoftCapsulePanelTop = Color(0xF51D2638)
  val SoftCapsulePanelBottom = Color(0xF70D121D)
  val SoftCapsuleActiveTop = Color(0xFF3B82F6)
  val SoftCapsuleActiveBottom = Color(0xFF2563EB)
  val SoftCapsuleEmerald = Color(0xFF34D399)
  val SoftCapsuleRimHighlight = Color(0x6BFFFFFF)
  val SoftCapsuleInnerShadow = Color(0x7302050B)
  val DeepShadowAmbient = Color(0xD902050C)
  val DeepShadowSpot = Color(0xE6010308)
}
