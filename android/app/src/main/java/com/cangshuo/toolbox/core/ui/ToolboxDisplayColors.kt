package com.cangshuo.toolbox.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

data class ToolboxDisplayColors(val background: Color, val foreground: Color)

/** High-contrast palette for full-screen clocks and banners; follows the active accent where safe. */
@Composable
fun toolboxDisplayColors(): ToolboxDisplayColors {
    val scheme = MaterialTheme.colorScheme
    val background = if (isSystemInDarkTheme()) scheme.surfaceContainerLowest else scheme.inverseSurface
    val accent = scheme.primary
    val foreground = if (contrastRatio(accent, background) >= 3f) accent else scheme.tertiary
    return ToolboxDisplayColors(background, foreground)
}

private fun contrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    return (maxOf(firstLuminance, secondLuminance) + 0.05f) /
        (minOf(firstLuminance, secondLuminance) + 0.05f)
}
