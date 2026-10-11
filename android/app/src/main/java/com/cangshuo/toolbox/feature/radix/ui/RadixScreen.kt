package com.cangshuo.toolbox.feature.radix.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.feature.radix.domain.RadixError
import com.cangshuo.toolbox.feature.radix.domain.RadixResult
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun RadixScreen(
    state: RadixUiState,
    onInputChanged: (String) -> Unit,
    onRadixSelected: (Int?) -> Unit,
    onCustomModeEnabled: () -> Unit,
    onCustomRadixChanged: (Int) -> Unit,
    onUppercaseToggled: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val result = state.result

    Box(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .fillMaxHeight(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "input_card") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.radix_input_label),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Row {
                                ToolboxTextButton(
                                    onClick = {
                                        val clip = clipboardManager.getText()?.text
                                        if (!clip.isNullOrEmpty()) onInputChanged(clip)
                                    },
                                ) { Text(stringResource(R.string.radix_btn_paste)) }
                                if (state.inputText.isNotEmpty()) {
                                    ToolboxTextButton(onClick = onClear) {
                                        Text(stringResource(R.string.radix_btn_clear))
                                    }
                                }
                            }
                        }
                        ToolboxTextField(
                            value = state.inputText,
                            onValueChange = onInputChanged,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            placeholder = stringResource(R.string.radix_input_placeholder),
                        )
                    }
                }
            }

            item(key = "base_card") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.radix_base_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = state.selectedRadix == null && !state.customMode,
                                onClick = { onRadixSelected(null) },
                                label = { Text(stringResource(R.string.radix_base_auto)) },
                            )
                            listOf(2, 8, 10, 16, 36).forEach { preset ->
                                FilterChip(
                                    selected = !state.customMode && state.selectedRadix == preset,
                                    onClick = { onRadixSelected(preset) },
                                    label = { Text(preset.toString()) },
                                )
                            }
                            FilterChip(
                                selected = state.customMode,
                                onClick = onCustomModeEnabled,
                                label = { Text(stringResource(R.string.radix_base_custom)) },
                            )
                        }
                        if (state.customMode) {
                            Text(
                                stringResource(R.string.radix_custom_label, state.customRadix),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Slider(
                                value = state.customRadix.toFloat(),
                                onValueChange = { onCustomRadixChanged(it.toInt()) },
                                valueRange = 2f..36f,
                                steps = 33,
                            )
                        }
                        FilterChip(
                            selected = state.uppercase,
                            onClick = { onUppercaseToggled(!state.uppercase) },
                            label = { Text(stringResource(R.string.radix_opt_uppercase)) },
                        )
                    }
                }
            }

            when {
                state.isProcessing -> {
                    item(key = "processing") { ToolboxLoadingState(stringResource(R.string.radix_processing)) }
                }
                result is RadixResult.Error -> {
                    item(key = "error_card") {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = when (result.reason) {
                                    RadixError.INPUT_TOO_LARGE -> stringResource(R.string.radix_error_limit)
                                    RadixError.INVALID_RADIX -> stringResource(R.string.radix_error_base)
                                    RadixError.EMPTY -> stringResource(R.string.radix_error_empty)
                                    RadixError.INVALID_CHAR -> if (result.position != null) {
                                        stringResource(R.string.radix_error_invalid, result.position)
                                    } else stringResource(R.string.radix_error_generic)
                                },
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                result is RadixResult.Success -> {
                    item(key = "meta_card") {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val baseLabel = if (result.input.autoDetected) {
                                stringResource(R.string.radix_base_auto)
                            } else {
                                result.input.radix.toString()
                            }
                            Text(
                                stringResource(
                                    R.string.radix_meta_format,
                                    baseLabel,
                                    result.digitCount,
                                    result.bitLength,
                                    result.groupCount,
                                ),
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                state.inputText.isNotBlank() -> Unit

                else -> {
                    item(key = "empty_card") {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(R.string.radix_empty_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (result is RadixResult.Success) {
                item(key = "results_header") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.radix_results_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        ToolboxTextButton(
                            onClick = {
                                val all = result.outputs.joinToString("\n") { item ->
                                    item.label + "(" + item.radix + "): " + item.value
                                }
                                clipboardManager.setText(AnnotatedString(all))
                            },
                        ) { Text(stringResource(R.string.radix_btn_copy_all)) }
                    }
                }

                result.outputs.forEach { output ->
                    item(key = "out_" + output.radix) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            output.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        ) {
                                            Text(
                                                stringResource(R.string.radix_radix_badge, output.radix),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                    ToolboxTextButton(
                                        onClick = { clipboardManager.setText(AnnotatedString(output.value)) },
                                    ) { Text(stringResource(R.string.radix_btn_copy)) }
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Box(Modifier.fillMaxWidth().padding(12.dp)) {
                                        SelectionContainer {
                                            Text(
                                                output.value,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Preview(showBackground = true, name = "Radix Screen Preview")
@Composable
private fun RadixScreenPreview() {
    ToolboxTheme {
        RadixScreen(
            state = RadixUiState(inputText = "255"),
            onInputChanged = {},
            onRadixSelected = {},
            onCustomModeEnabled = {},
            onCustomRadixChanged = {},
            onUppercaseToggled = {},
            onClear = {},
        )
    }
}
