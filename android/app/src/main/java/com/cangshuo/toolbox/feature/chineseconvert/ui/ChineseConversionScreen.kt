package com.cangshuo.toolbox.feature.chineseconvert.ui

import android.content.ClipData
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolResultRow
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionError
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionLimits
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionMode
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionOutcome
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChineseConversionScreen(
    state: ChineseConversionUiState,
    onInputChanged: (String) -> Unit,
    onModeSelected: (ChineseConversionMode) -> Unit,
    onReverse: () -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var modeExpanded by remember { mutableStateOf(false) }
    val copied = stringResource(R.string.chinese_convert_copied)
    val actionError = stringResource(R.string.chinese_convert_action_error)
    val examples = listOf(
        stringResource(R.string.chinese_convert_example_phrase),
        stringResource(R.string.chinese_convert_example_taiwan),
        stringResource(R.string.chinese_convert_example_hongkong),
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(horizontal = ToolboxDimens.pageGutter, vertical = ToolboxDimens.pageTop),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("mode") {
                ToolGroupCard(title = stringResource(R.string.chinese_convert_mode)) {
                    ExposedDropdownMenuBox(
                        expanded = modeExpanded,
                        onExpandedChange = { modeExpanded = it },
                    ) {
                        ToolboxTextField(
                            value = stringResource(state.mode.labelResource()),
                            onValueChange = {},
                            readOnly = true,
                            singleLine = false,
                            minLines = 1,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modeExpanded) },
                        )
                        ExposedDropdownMenu(
                            expanded = modeExpanded,
                            onDismissRequest = { modeExpanded = false },
                        ) {
                            ChineseConversionMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(mode.labelResource())) },
                                    leadingIcon = { RadioButton(selected = mode == state.mode, onClick = null) },
                                    onClick = {
                                        onModeSelected(mode)
                                        modeExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.chinese_convert_mode_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item("input") {
                ToolGroupCard(title = stringResource(R.string.chinese_convert_input)) {
                    ToolboxTextField(
                        state.input, onInputChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.chinese_convert_input_label),
                        placeholder = stringResource(R.string.chinese_convert_input_hint),
                        singleLine = false, minLines = 4, maxLines = 8,
                        isError = state.outcome is ChineseConversionOutcome.Failure,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        examples.forEachIndexed { index, example ->
                            ToolboxTextButton(onClick = { onInputChanged(example) }) {
                                Text(stringResource(R.string.chinese_convert_example_button, index + 1))
                            }
                        }
                        if (state.input.isNotEmpty()) ToolboxTextButton(onClick = onClear) {
                            Text(stringResource(R.string.chinese_convert_clear))
                        }
                    }
                }
            }
            item("result") {
                ToolGroupCard(title = stringResource(R.string.chinese_convert_result)) {
                    when {
                        state.processing -> {
                            ToolboxLoadingIndicator(compact = true)
                            Text(stringResource(R.string.chinese_convert_processing))
                            ToolboxTextButton(onClick = onCancel) { Text(stringResource(R.string.chinese_convert_cancel)) }
                        }
                        state.cancelled -> {
                            Text(stringResource(R.string.chinese_convert_cancelled))
                            ToolboxTextButton(onClick = onRetry) { Text(stringResource(R.string.chinese_convert_retry)) }
                        }
                        state.outcome is ChineseConversionOutcome.Failure -> {
                            Text(
                                stringResource(state.outcome.error.messageResource()),
                                color = MaterialTheme.colorScheme.error,
                            )
                            ToolboxTextButton(onClick = onRetry) { Text(stringResource(R.string.chinese_convert_retry)) }
                        }
                        state.outcome is ChineseConversionOutcome.Success -> {
                            val result = state.outcome
                            val preview = remember(result.text) { previewChineseResult(result.text) }
                            ToolResultRow(stringResource(R.string.chinese_convert_input_count), result.statistics.inputCharacters.toString())
                            ToolResultRow(stringResource(R.string.chinese_convert_output_count), result.statistics.outputCharacters.toString())
                            ToolResultRow(stringResource(R.string.chinese_convert_changed), result.statistics.changedSegments.toString())
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                                SelectionContainer {
                                    Text(
                                        preview, Modifier.fillMaxWidth().padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                            if (result.text.length > preview.length) Text(
                                stringResource(R.string.chinese_convert_preview),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ToolboxTextButton(onClick = {
                                    scope.launch {
                                        try {
                                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copied, result.text)))
                                            snackbar.showSnackbar(copied)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) { snackbar.showSnackbar(actionError) }
                                    }
                                }) { Text(stringResource(R.string.chinese_convert_copy)) }
                                ToolboxTextButton(onClick = {
                                    try {
                                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, result.text)
                                        }, null))
                                    } catch (_: Exception) { scope.launch { snackbar.showSnackbar(actionError) } }
                                }) { Text(stringResource(R.string.chinese_convert_share)) }
                                ToolboxTextButton(onClick = onReverse, enabled = result.text.length <= ChineseConversionLimits.MAX_INPUT) {
                                    Text(stringResource(R.string.chinese_convert_reverse))
                                }
                            }
                            Text(
                                stringResource(R.string.chinese_convert_result_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> Text(stringResource(R.string.chinese_convert_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }
}

internal fun previewChineseResult(text: String): String {
    var end = minOf(text.length, ChineseConversionLimits.PREVIEW)
    if (end < text.length && end > 0 && text[end - 1].isHighSurrogate()) end--
    return text.substring(0, end)
}

@StringRes
internal fun ChineseConversionMode.labelResource(): Int = when (this) {
    ChineseConversionMode.S2T -> R.string.chinese_convert_s2t
    ChineseConversionMode.T2S -> R.string.chinese_convert_t2s
    ChineseConversionMode.S2TW -> R.string.chinese_convert_s2tw
    ChineseConversionMode.TW2S -> R.string.chinese_convert_tw2s
    ChineseConversionMode.S2TWP -> R.string.chinese_convert_s2twp
    ChineseConversionMode.TW2SP -> R.string.chinese_convert_tw2sp
    ChineseConversionMode.S2HK -> R.string.chinese_convert_s2hk
    ChineseConversionMode.HK2S -> R.string.chinese_convert_hk2s
}

@StringRes
private fun ChineseConversionError.messageResource(): Int = when (this) {
    ChineseConversionError.INPUT_TOO_LONG -> R.string.chinese_convert_error_input
    ChineseConversionError.INVALID_UNICODE -> R.string.chinese_convert_error_unicode
    ChineseConversionError.OUTPUT_TOO_LONG -> R.string.chinese_convert_error_output
    ChineseConversionError.TIME_LIMIT -> R.string.chinese_convert_error_time
    ChineseConversionError.FAILED -> R.string.chinese_convert_error_failed
}
