package com.cangshuo.toolbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

// Shape scale: cards/containers use large(16) per the design (12-16dp);
// tool icon containers use medium(12); chips stay pill.
private val ToolboxShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun ToolboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: ToolboxAccent = ToolboxAccent.BLUE,
    content: @Composable () -> Unit,
) {
    val colors = remember(darkTheme, accent) {
        if (darkTheme) toolboxDarkColors(accent) else toolboxLightColors(accent)
    }
    MaterialTheme(
        colorScheme = colors,
        typography = ToolboxTypography,
        shapes = ToolboxShapes,
        content = content,
    )
}
