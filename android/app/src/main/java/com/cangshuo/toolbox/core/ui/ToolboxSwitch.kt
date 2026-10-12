package com.cangshuo.toolbox.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Keeps the handle visible with our low-contrast border palette in both themes. */
@Composable
fun ToolboxSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onPrimary,
            checkedTrackColor = colors.primary,
            checkedBorderColor = colors.primary,
            uncheckedThumbColor = colors.onSurfaceVariant,
            uncheckedTrackColor = colors.surfaceContainerHigh,
            uncheckedBorderColor = colors.outline,
            disabledCheckedThumbColor = colors.surface,
            disabledCheckedTrackColor = colors.onSurface.copy(alpha = 0.12f),
            disabledCheckedBorderColor = colors.onSurface.copy(alpha = 0.12f),
            disabledUncheckedThumbColor = colors.onSurface.copy(alpha = 0.38f),
            disabledUncheckedTrackColor = colors.surfaceContainerHigh,
            disabledUncheckedBorderColor = colors.onSurface.copy(alpha = 0.12f),
        ),
    )
}
