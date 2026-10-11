package com.cangshuo.toolbox.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Design system tokens: white-dominant + light-gray surfaces + black text with a
// user-selectable accent (default blue #1677FF). Accent-derived roles are computed so
// every palette keeps the same hierarchy; accent choices live in ToolboxAccent.

private val LightSurface = Color(0xFFFFFFFF)
private val DarkSurface = Color(0xFF101011)

internal fun toolboxLightColors(accent: ToolboxAccent): ColorScheme = lightColorScheme(
    primary = accent.light,
    onPrimary = Color.White,
    primaryContainer = lerp(accent.light, LightSurface, 0.90f),
    onPrimaryContainer = accent.light,
    inversePrimary = lerp(accent.light, LightSurface, 0.65f),
    secondary = Color(0xFF777777),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3F3F4),
    onSecondaryContainer = Color(0xFF171717),
    // Tertiary doubles as the "ready / success" semantic.
    tertiary = Color(0xFF16A34A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE9F7EE),
    onTertiaryContainer = Color(0xFF15803D),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFF7F7F8),
    onSurfaceVariant = Color(0xFF777777),
    surfaceTint = accent.light,
    inverseSurface = Color(0xFF171717),
    inverseOnSurface = Color(0xFFF7F7F8),
    outline = Color(0xFFE5E5E5),
    outlineVariant = Color(0xFFEEEEEE),
    error = Color(0xFFF04438),
    onError = Color.White,
    errorContainer = Color(0xFFFEECEB),
    onErrorContainer = Color(0xFFB42318),
    surfaceDim = Color(0xFFE5E5E5),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F8),
    // Tool icon container background.
    surfaceContainer = Color(0xFFF3F3F4),
    surfaceContainerHigh = Color(0xFFEDEDEE),
    surfaceContainerHighest = Color(0xFFE5E5E5),
)

// Dark palette: layered near-blacks, off-white text, and a lighter accent.
internal fun toolboxDarkColors(accent: ToolboxAccent): ColorScheme = darkColorScheme(
    primary = accent.dark,
    onPrimary = lerp(accent.dark, Color.Black, 0.82f),
    primaryContainer = lerp(accent.dark, DarkSurface, 0.80f),
    onPrimaryContainer = lerp(accent.dark, Color.White, 0.55f),
    inversePrimary = accent.light,
    secondary = Color(0xFF9E9EA4),
    onSecondary = Color(0xFF171718),
    secondaryContainer = Color(0xFF232326),
    onSecondaryContainer = Color(0xFFF5F5F5),
    tertiary = Color(0xFF4ADE80),
    onTertiary = Color(0xFF052E16),
    tertiaryContainer = Color(0xFF16301F),
    onTertiaryContainer = Color(0xFF86EFAC),
    background = Color(0xFF101011),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF171718),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF232326),
    onSurfaceVariant = Color(0xFF9E9EA4),
    surfaceTint = accent.dark,
    inverseSurface = Color(0xFFF5F5F5),
    inverseOnSurface = Color(0xFF171718),
    outline = Color(0xFF34343A),
    outlineVariant = Color(0xFF262629),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF3A1A1A),
    onErrorContainer = Color(0xFFF87171),
    surfaceDim = Color(0xFF0C0C0D),
    surfaceBright = Color(0xFF2A2A2C),
    surfaceContainerLowest = Color(0xFF0C0C0D),
    surfaceContainerLow = Color(0xFF1A1A1C),
    surfaceContainer = Color(0xFF1F1F22),
    surfaceContainerHigh = Color(0xFF26262A),
    surfaceContainerHighest = Color(0xFF2E2E33),
)
