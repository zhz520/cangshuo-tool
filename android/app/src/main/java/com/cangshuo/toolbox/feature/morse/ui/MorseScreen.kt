package com.cangshuo.toolbox.feature.morse.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.cangshuo.toolbox.feature.morse.domain.MorseDirection
import com.cangshuo.toolbox.feature.morse.domain.MorseError
import com.cangshuo.toolbox.feature.morse.domain.MorseResult
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun MorseScreen(
    state: MorseUiState,
    onInputChanged: (String) -> Unit,
    onDirectionSelected: (MorseDirection) -> Unit,
    onWpmChanged: (Int) -> Unit,
    onPlayToggle: () -> Unit,
    onSwap: () -> Unit,
    onClear: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val result = state.result
    val canSwap = result is MorseResult.Success && result.output.isNotEmpty()

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
            item(key = "direction_card") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = state.direction == MorseDirection.ENCODE,
                            onClick = { onDirectionSelected(MorseDirection.ENCODE) },
                            label = { Text(stringResource(R.string.morse_dir_encode)) },
                        )
                        FilterChip(
                            selected = state.direction == MorseDirection.DECODE,
                            onClick = { onDirectionSelected(MorseDirection.DECODE) },
                            label = { Text(stringResource(R.string.morse_dir_decode)) },
                        )
                    }
                }
            }

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
                                stringResource(R.string.morse_input_label),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Row {
                                ToolboxTextButton(
                                    onClick = {
                                        val clip = clipboardManager.getText()?.text
                                        if (!clip.isNullOrEmpty()) onInputChanged(clip)
                                    },
                                ) { Text(stringResource(R.string.morse_btn_paste)) }
                                if (state.inputText.isNotEmpty()) {
                                    ToolboxTextButton(onClick = onClear) {
                                        Text(stringResource(R.string.morse_btn_clear))
                                    }
                                }
                            }
                        }
                        ToolboxTextField(
                            value = state.inputText,
                            onValueChange = onInputChanged,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6,
                            placeholder = if (state.direction == MorseDirection.ENCODE) {
                                stringResource(R.string.morse_input_placeholder_encode)
                            } else {
                                stringResource(R.string.morse_input_placeholder_decode)
                            },
                        )
                    }
                }
            }

            item(key = "play_card") {
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
                            stringResource(R.string.morse_options_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.morse_speed_format, state.wpm),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Slider(
                            value = state.wpm.toFloat(),
                            onValueChange = { onWpmChanged(it.toInt()) },
                            valueRange = 5f..30f,
                            steps = 25,
                        )
                        ToolboxTextButton(onClick = onPlayToggle) {
                            Text(
                                if (state.isPlaying) {
                                    stringResource(R.string.morse_btn_stop)
                                } else {
                                    stringResource(R.string.morse_btn_play)
                                },
                            )
                        }
                    }
                }
            }

            when (result) {
                is MorseResult.Error -> {
                    item(key = "error_card") {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (result.position != null) {
                                    stringResource(R.string.morse_error_symbol, result.position)
                                } else {
                                    stringResource(R.string.morse_error_empty)
                                },
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                is MorseResult.Success -> {
                    item(key = "result_card") {
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
                                    Text(
                                        stringResource(R.string.morse_results_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Row {
                                        if (canSwap) {
                                            ToolboxTextButton(onClick = onSwap) {
                                                Text(stringResource(R.string.morse_btn_swap))
                                            }
                                        }
                                        ToolboxTextButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(result.output))
                                            },
                                        ) { Text(stringResource(R.string.morse_btn_copy)) }
                                    }
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Box(Modifier.fillMaxWidth().padding(12.dp)) {
                                        SelectionContainer {
                                            Text(
                                                result.output,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                                if (result.unsupported.isNotEmpty()) {
                                    Text(
                                        stringResource(
                                            R.string.morse_unsupported_format,
                                            result.unsupported.size,
                                            result.unsupported.take(20).joinToString(" "),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                null -> {
                    if (state.inputText.isBlank()) {
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
                                        stringResource(R.string.morse_empty_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
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

@Preview(showBackground = true, name = "Morse Screen Preview")
@Composable
private fun MorseScreenPreview() {
    ToolboxTheme {
        MorseScreen(
            state = MorseUiState(inputText = "SOS"),
            onInputChanged = {},
            onDirectionSelected = {},
            onWpmChanged = {},
            onPlayToggle = {},
            onSwap = {},
            onClear = {},
        )
    }
}
