package com.cangshuo.toolbox.feature.color.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolResultRow
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.color.domain.ColorError
import com.cangshuo.toolbox.feature.color.domain.RgbColor
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun ColorScreen(
    state: ColorUiState,
    onInputChanged: (String) -> Unit,
    onClear: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "input") {
            ToolGroupCard(title = stringResource(R.string.color_input_title)) {
                ToolboxTextField(
                    value = state.inputText,
                    onValueChange = onInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.color_input_label),
                    placeholder = stringResource(R.string.color_input_hint),
                )
                if (state.inputText.isNotEmpty()) {
                    ToolboxTextButton(onClick = onClear) { Text(stringResource(R.string.color_btn_clear)) }
                }
            }
        }

        when (val outcome = state.outcome) {
            null -> item(key = "placeholder") {
                PlaceholderSurface(stringResource(R.string.color_empty_placeholder))
            }

            is ColorOutcome.Failure -> item(key = "error") {
                ErrorSurface(outcome.error)
            }

            is ColorOutcome.Success -> item(key = "result") {
                val description = outcome.description
                val summary = stringResource(
                    R.string.color_copy_summary,
                    description.hex,
                    description.rgb,
                    description.hsl,
                    description.hsv,
                )
                ToolGroupCard(title = stringResource(R.string.color_result_title)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(description.color.toComposeColor())
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                        )
                        Text(
                            text = description.hex,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    ToolResultRow(stringResource(R.string.color_result_hex), description.hex)
                    ToolResultRow(stringResource(R.string.color_result_rgb), description.rgb)
                    ToolResultRow(stringResource(R.string.color_result_hsl), description.hsl)
                    ToolResultRow(stringResource(R.string.color_result_hsv), description.hsv)
                    Text(
                        text = stringResource(R.string.color_result_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ToolboxTextButton(
                        onClick = { clipboard.setText(AnnotatedString(summary)) },
                    ) { Text(stringResource(R.string.color_btn_copy)) }
                }
            }
        }

        item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

private fun RgbColor.toComposeColor(): Color = Color(
    red = red / 255f,
    green = green / 255f,
    blue = blue / 255f,
)

@Composable
private fun PlaceholderSurface(message: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorSurface(error: ColorError) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(error.messageResource()),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@StringRes
private fun ColorError.messageResource(): Int = when (this) {
    ColorError.INVALID_FORMAT -> R.string.color_error_invalid
    ColorError.OUT_OF_RANGE -> R.string.color_error_range
    ColorError.UNSUPPORTED_ALPHA -> R.string.color_error_alpha
    ColorError.INPUT_TOO_LONG -> R.string.numeric_error_input_too_long
}

@Preview
@Composable
private fun ColorScreenPreview() {
    ToolboxTheme { ColorScreen(ColorUiState(), {}, {}) }
}
