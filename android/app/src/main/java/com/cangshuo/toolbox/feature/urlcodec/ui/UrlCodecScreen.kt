package com.cangshuo.toolbox.feature.urlcodec.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.feature.urlcodec.domain.UrlCodecFailure
import com.cangshuo.toolbox.feature.urlcodec.domain.UrlCodecMode
import com.cangshuo.toolbox.feature.urlcodec.domain.UrlCodecResult
import com.cangshuo.toolbox.feature.urlcodec.domain.UrlEncodeType
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun UrlCodecScreen(
    state: UrlCodecUiState,
    onModeChanged: (UrlCodecMode) -> Unit,
    onEncodeTypeChanged: (UrlEncodeType) -> Unit,
    onInputChanged: (String) -> Unit,
    onSwap: () -> Unit,
    onClear: () -> Unit,
    onSample: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.url_codec_name), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.url_codec_description), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UrlCodecMode.entries.forEach { mode ->
                                FilterChip(selected = state.mode == mode, onClick = { onModeChanged(mode) }, label = {
                                    Text(stringResource(if (mode == UrlCodecMode.ENCODE) R.string.url_codec_mode_encode else R.string.url_codec_mode_decode))
                                })
                            }
                        }
                        Text(stringResource(R.string.url_codec_type_title), style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UrlEncodeType.entries.forEach { type ->
                                FilterChip(selected = state.encodeType == type, onClick = { onEncodeTypeChanged(type) }, label = {
                                    Text(stringResource(when (type) {
                                        UrlEncodeType.COMPONENT -> R.string.url_codec_type_component
                                        UrlEncodeType.FULL_URL -> R.string.url_codec_type_full
                                        UrlEncodeType.FORM_VALUE -> R.string.url_codec_type_form
                                    }))
                                })
                            }
                        }
                        Text(profileHelp(state), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.url_codec_single_pass), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.inputText, onValueChange = onInputChanged,
                            modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 8,
                            label = { Text(stringResource(if (state.mode == UrlCodecMode.ENCODE)
                                R.string.url_codec_input_label_plain else R.string.url_codec_input_label_encoded)) },
                            placeholder = { Text(stringResource(if (state.mode == UrlCodecMode.ENCODE)
                                R.string.url_codec_placeholder_plain else R.string.url_codec_placeholder_encoded)) },
                            isError = state.error != null,
                            supportingText = { Text(stringResource(R.string.url_codec_char_count, state.inputText.length)) },
                        )
                        if (state.inputRejected) Text(stringResource(R.string.url_codec_input_rejected),
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { clipboard.getText()?.text?.let(onInputChanged) }) {
                                Text(stringResource(R.string.url_codec_btn_paste))
                            }
                            TextButton(onClick = onSample) { Text(stringResource(R.string.url_codec_btn_sample)) }
                            TextButton(onClick = onClear, enabled = state.inputText.isNotEmpty() || state.inputRejected) {
                                Text(stringResource(R.string.url_codec_btn_clear))
                            }
                        }
                    }
                }
            }
            item {
                when {
                    state.isProcessing -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolboxLoadingState(stringResource(R.string.url_codec_processing))
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.url_codec_btn_cancel)) }
                    }
                    state.error != null -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(errorMessage(state.error), color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = onRetry) { Text(stringResource(R.string.url_codec_btn_retry)) }
                        }
                    }
                    state.cancelled -> Column {
                        Text(stringResource(R.string.url_codec_cancelled), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.url_codec_btn_retry)) }
                    }
                    else -> Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(if (state.mode == UrlCodecMode.ENCODE)
                                R.string.url_codec_output_label_encoded else R.string.url_codec_output_label_plain),
                                style = MaterialTheme.typography.titleMedium)
                            if (state.hasResult) {
                                val preview = remember(state.outputText) {
                                    val end = minOf(state.outputText.length, PREVIEW_LENGTH)
                                    state.outputText.substring(0, if (end > 0 && state.outputText[end - 1].isHighSurrogate()) end - 1 else end)
                                }
                                Text(stringResource(R.string.url_codec_char_count, state.outputText.length),
                                    style = MaterialTheme.typography.labelMedium)
                                if (preview.length < state.outputText.length) Text(
                                    stringResource(R.string.url_codec_preview_partial, preview.length),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                SelectionContainer {
                                    Text(preview, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { clipboard.setText(AnnotatedString(state.outputText)) }) {
                                        Text(stringResource(R.string.url_codec_btn_copy))
                                    }
                                    TextButton(onClick = onSwap) { Text(stringResource(R.string.url_codec_btn_swap)) }
                                }
                            } else Text(stringResource(R.string.url_codec_output_placeholder),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun profileHelp(state: UrlCodecUiState): String = stringResource(when (state.encodeType) {
    UrlEncodeType.COMPONENT -> if (state.mode == UrlCodecMode.ENCODE) R.string.url_codec_desc_component else R.string.url_codec_desc_component_decode
    UrlEncodeType.FULL_URL -> if (state.mode == UrlCodecMode.ENCODE) R.string.url_codec_desc_full else R.string.url_codec_desc_full_decode
    UrlEncodeType.FORM_VALUE -> if (state.mode == UrlCodecMode.ENCODE) R.string.url_codec_desc_form else R.string.url_codec_desc_form_decode
})

@Composable
private fun errorMessage(error: UrlCodecResult.Error): String {
    val message = stringResource(when (error.reason) {
        UrlCodecFailure.INPUT_LIMIT -> R.string.url_codec_error_input_limit
        UrlCodecFailure.OUTPUT_LIMIT -> R.string.url_codec_error_output_limit
        UrlCodecFailure.INVALID_UNICODE -> R.string.url_codec_error_unicode
        UrlCodecFailure.INVALID_PERCENT -> R.string.url_codec_error_invalid
        UrlCodecFailure.INVALID_UTF8 -> R.string.url_codec_error_utf8
        UrlCodecFailure.TIME_BUDGET -> R.string.url_codec_error_time
        UrlCodecFailure.MEMORY_LIMIT -> R.string.url_codec_error_memory
        UrlCodecFailure.PROCESSING_FAILED -> R.string.url_codec_error_processing
    })
    return if (error.offset == null) message else "$message\n${stringResource(R.string.url_codec_error_position, error.offset + 1)}"
}

private const val PREVIEW_LENGTH = 8_000

@Preview(name = "URL Chinese", locale = "zh", widthDp = 360, showBackground = true)
@Preview(name = "URL English", locale = "en", widthDp = 360, showBackground = true)
@Composable
private fun UrlCodecPreview() {
    ToolboxTheme {
        UrlCodecScreen(UrlCodecUiState(), {}, {}, {}, {}, {}, {}, {}, {})
    }
}
