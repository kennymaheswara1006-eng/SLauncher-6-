package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkGlassColorScheme =
  darkColorScheme(
    primary = SLauncherColors.AccentPrimary,
    onPrimary = Color(0xFF0A1929),
    primaryContainer = SLauncherColors.AccentContainer,
    onPrimaryContainer = SLauncherColors.TextPrimary,
    secondary = SLauncherColors.TextSecondary,
    onSecondary = Color(0xFF0E121B),
    background = Color.Transparent,
    onBackground = SLauncherColors.TextPrimary,
    surface = SLauncherColors.DarkGlassDrawer,
    onSurface = SLauncherColors.TextPrimary,
    surfaceVariant = SLauncherColors.DarkGlassCard,
    onSurfaceVariant = SLauncherColors.TextSecondary,
    outline = SLauncherColors.GlassBorder,
  )

@Composable
fun SLauncher6Theme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = DarkGlassColorScheme,
    typography = SLauncherTypography,
    content = content,
  )
}
