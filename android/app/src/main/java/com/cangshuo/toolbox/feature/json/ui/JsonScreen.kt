package com.cangshuo.toolbox.feature.json.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.cangshuo.toolbox.feature.json.domain.JsonFailure
import com.cangshuo.toolbox.feature.json.domain.JsonIndent
import com.cangshuo.toolbox.feature.json.domain.JsonStringInput
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JsonScreen(
    state: JsonUiState,
    onModeChanged: (JsonMode) -> Unit,
    onIndentChanged: (JsonIndent) -> Unit,
    onStringInputChanged: (JsonStringInput) -> Unit,
    onInputChanged: (String) -> Unit,
    onSwap: () -> Unit,
    onClear: () -> Unit,
    onLoadSample: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val preview = remember(state.outputText) {
        var end = minOf(state.outputText.length, PREVIEW_LENGTH)
        if (end < state.outputText.length && end > 0 && state.outputText[end - 1].isHighSurrogate()) end--
        state.outputText.substring(0, end)
    }
    Box(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "input") {
                JsonCard {
                    Text(stringResource(R.string.json_input_label), style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = onLoadSample) { Text(stringResource(R.string.json_btn_sample)) }
                        TextButton(onClick = { clipboard.getText()?.text?.let(onInputChanged) }) { Text(stringResource(R.string.json_btn_paste)) }
                        TextButton(onClick = onClear) { Text(stringResource(R.string.json_btn_clear)) }
                    }
                    if (state.inputRejected) JsonError(R.string.json_error_input_limit)
                    OutlinedTextField(state.inputText, onInputChanged, Modifier.fillMaxWidth(), minLines = 5, maxLines = 10,
                        isError = state.error != null,
                        placeholder = { Text(stringResource(R.string.json_placeholder_input)) },
                        supportingText = {
                            Text(if (state.inputByteCount != null) stringResource(R.string.json_input_stats,
                                state.inputText.length, state.inputByteCount)
                            else stringResource(R.string.json_input_pending, state.inputText.length))
                        })
                    state.error?.let { error ->
                        JsonError(error.reason.messageRes())
                        if (error.line != null && error.column != null) JsonError(R.string.json_error_location, error.line, error.column)
                    }
                }
            }
            item(key = "options") {
                JsonCard {
                    Text(stringResource(R.string.json_mode_title), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JsonMode.entries.forEach { mode ->
                            FilterChip(state.mode == mode, { onModeChanged(mode) }, label = { Text(stringResource(mode.nameRes())) })
                        }
                    }
                    if (state.mode == JsonMode.FORMAT) {
                        Text(stringResource(R.string.json_indent_title), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            JsonIndent.entries.forEach { indent ->
                                FilterChip(state.indent == indent, { onIndentChanged(indent) }, label = {
                                    Text(stringResource(if (indent == JsonIndent.TWO_SPACES) R.string.json_indent_two else R.string.json_indent_four))
                                })
                            }
                        }
                    }
                    if (state.mode == JsonMode.UNESCAPE) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(state.stringInput == JsonStringInput.QUOTED_LITERAL,
                                { onStringInputChanged(JsonStringInput.QUOTED_LITERAL) },
                                label = { Text(stringResource(R.string.json_string_quoted)) })
                            FilterChip(state.stringInput == JsonStringInput.ESCAPED_CONTENT,
                                { onStringInputChanged(JsonStringInput.ESCAPED_CONTENT) },
                                label = { Text(stringResource(R.string.json_string_content)) })
                        }
                    }
                    Text(stringResource(when (state.mode) {
                        JsonMode.FORMAT, JsonMode.COMPRESS -> R.string.json_help_document
                        JsonMode.ESCAPE -> R.string.json_help_escape
                        JsonMode.UNESCAPE -> if (state.stringInput == JsonStringInput.QUOTED_LITERAL) R.string.json_help_quoted else R.string.json_help_content
                    }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.isProcessing) {
                item(key = "processing") { ToolboxLoadingState(stringResource(R.string.json_processing)) }
                item(key = "cancel") { OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.json_btn_cancel)) } }
            } else {
                item(key = "output") {
                    JsonCard {
                        Text(stringResource(R.string.json_output_label), style = MaterialTheme.typography.titleMedium)
                        if (state.cancelled) Text(stringResource(R.string.json_cancelled), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.cancelled || state.error != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.json_btn_retry)) }
                        if (state.hasResult) {
                            if (state.mode != JsonMode.UNESCAPE) Text(stringResource(R.string.json_valid), color = MaterialTheme.colorScheme.tertiary)
                            Text(stringResource(R.string.json_output_stats, state.outputLineCount, state.outputByteCount),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state.duplicateKeyCount > 0) Text(stringResource(R.string.json_duplicate_keys, state.duplicateKeyCount),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (preview.length < state.outputText.length) Text(stringResource(R.string.json_preview_limited),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                if (state.outputText.isEmpty()) Text(stringResource(R.string.json_output_empty), Modifier.padding(12.dp))
                                else SelectionContainer(Modifier.padding(12.dp)) {
                                    Text(preview, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(state.outputText)) }) { Text(stringResource(R.string.json_btn_copy)) }
                                OutlinedButton(onClick = onSwap) { Text(stringResource(R.string.json_btn_swap)) }
                            }
                        } else if (state.error == null && !state.cancelled) {
                            Text(stringResource(R.string.json_output_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JsonCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun JsonError(resource: Int, vararg args: Any) {
    Text(stringResource(resource, *args), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

private fun JsonMode.nameRes(): Int = when (this) {
    JsonMode.FORMAT -> R.string.json_mode_format
    JsonMode.COMPRESS -> R.string.json_mode_compress
    JsonMode.ESCAPE -> R.string.json_mode_escape
    JsonMode.UNESCAPE -> R.string.json_mode_unescape
}

private fun JsonFailure.messageRes(): Int = when (this) {
    JsonFailure.INPUT_LIMIT -> R.string.json_error_input_limit
    JsonFailure.OUTPUT_LIMIT -> R.string.json_error_output_limit
    JsonFailure.DEPTH_LIMIT -> R.string.json_error_depth
    JsonFailure.TOKEN_LIMIT -> R.string.json_error_tokens
    JsonFailure.INVALID_UNICODE -> R.string.json_error_unicode
    JsonFailure.EXPECTED_VALUE -> R.string.json_error_value
    JsonFailure.EXPECTED_KEY -> R.string.json_error_key
    JsonFailure.EXPECTED_COLON -> R.string.json_error_colon
    JsonFailure.EXPECTED_SEPARATOR -> R.string.json_error_separator
    JsonFailure.INVALID_NUMBER -> R.string.json_error_number
    JsonFailure.INVALID_STRING -> R.string.json_error_string
    JsonFailure.INVALID_ESCAPE -> R.string.json_error_escape
    JsonFailure.TRAILING_CONTENT -> R.string.json_error_trailing
    JsonFailure.TIME_BUDGET -> R.string.json_error_timeout
    JsonFailure.MEMORY_LIMIT -> R.string.json_error_memory
    JsonFailure.PROCESSING_FAILED -> R.string.json_error_processing
}

private const val PREVIEW_LENGTH = 8000

@Preview(name = "JSON Chinese", locale = "zh", widthDp = 360, showBackground = true)
@Preview(name = "JSON English", locale = "en", widthDp = 360, showBackground = true)
@Composable
private fun JsonScreenPreview() {
    ToolboxTheme {
        JsonScreen(JsonUiState(inputText = "{\"name\":\"沧烁\"}", outputText = "{\n  \"name\": \"沧烁\"\n}",
            hasResult = true, outputLineCount = 3, outputByteCount = 24), onModeChanged = {}, onIndentChanged = {},
            onStringInputChanged = {}, onInputChanged = {}, onSwap = {}, onClear = {}, onLoadSample = {}, onCancel = {}, onRetry = {})
    }
}
