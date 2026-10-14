package com.cangshuo.toolbox.feature.pinyin.ui

import android.content.ClipData
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolResultRow
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.core.ui.ToolboxSwitch
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinCaseMode
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinError
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinLimits
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinMode
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinOutcome
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinSeparator
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinToneStyle
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinVCharType
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PinyinScreen(
    state: PinyinUiState,
    onInputChanged: (String) -> Unit,
    onModeSelected: (PinyinMode) -> Unit,
    onToneStyleSelected: (PinyinToneStyle) -> Unit,
    onCaseModeSelected: (PinyinCaseMode) -> Unit,
    onSeparatorSelected: (PinyinSeparator) -> Unit,
    onVCharTypeSelected: (PinyinVCharType) -> Unit,
    onKeepNonChineseChanged: (Boolean) -> Unit,
    onExampleSelected: (String) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.pinyin_copied)
    val copyFailedMessage = stringResource(R.string.pinyin_copy_failed)
    val examples = listOf(
        stringResource(R.string.pinyin_example_polyphone),
        stringResource(R.string.pinyin_example_vchar),
        stringResource(R.string.pinyin_example_separator),
        stringResource(R.string.pinyin_example_unsupported),
    )
    fun copyResult(value: String) {
        scope.launch {
            try {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copiedMessage, value)))
                snackbar.showSnackbar(copiedMessage)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snackbar.showSnackbar(copyFailedMessage)
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = ToolboxDimens.formMaxWidth)
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(
                horizontal = ToolboxDimens.pageGutter,
                vertical = ToolboxDimens.pageTop,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "examples") {
                ToolSectionHeader(stringResource(R.string.pinyin_examples_title))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    examples.forEach { example ->
                        FilterChip(
                            selected = false,
                            onClick = { onExampleSelected(example) },
                            label = { Text(example) },
                        )
                    }
                }
            }

            item(key = "input") {
                ToolGroupCard(title = stringResource(R.string.pinyin_input_title)) {
                    ToolboxTextField(
                        value = state.input,
                        onValueChange = onInputChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.pinyin_input_label),
                        placeholder = stringResource(R.string.pinyin_input_hint),
                        singleLine = false,
                        minLines = 4,
                        maxLines = 8,
                        isError = (state.outcome as? PinyinOutcome.Failure)?.error == PinyinError.INPUT_TOO_LONG,
                    )
                    if (state.input.isNotEmpty()) {
                        ToolboxTextButton(onClick = onClear) {
                            Text(stringResource(R.string.pinyin_btn_clear))
                        }
                    }
                }
            }

            item(key = "options") {
                ToolGroupCard(title = stringResource(R.string.pinyin_options_title)) {
                    ChipGroup(
                        title = stringResource(R.string.pinyin_mode_title),
                        values = PinyinMode.entries.toList(),
                        selected = state.mode,
                        label = { stringResource(it.labelResource()) },
                        onSelected = onModeSelected,
                    )

                    if (state.mode == PinyinMode.FULL) {
                        ChipGroup(
                            title = stringResource(R.string.pinyin_tone_title),
                            values = PinyinToneStyle.entries.toList(),
                            selected = state.toneStyle,
                            label = { stringResource(it.labelResource()) },
                            onSelected = onToneStyleSelected,
                        )
                    }

                    ChipGroup(
                        title = stringResource(R.string.pinyin_case_title),
                        values = PinyinCaseMode.entries.toList(),
                        selected = state.caseMode,
                        label = { stringResource(it.labelResource()) },
                        onSelected = onCaseModeSelected,
                    )

                    if (state.mode == PinyinMode.FULL) {
                        ChipGroup(
                            title = stringResource(R.string.pinyin_separator_title),
                            values = PinyinSeparator.entries.toList(),
                            selected = state.separator,
                            label = { stringResource(it.labelResource()) },
                            onSelected = onSeparatorSelected,
                        )

                        if (state.toneStyle != PinyinToneStyle.TONE_MARK) {
                            ChipGroup(
                                title = stringResource(R.string.pinyin_vchar_title),
                                values = PinyinVCharType.entries.toList(),
                                selected = state.vCharType,
                                label = { stringResource(it.labelResource()) },
                                onSelected = onVCharTypeSelected,
                            )
                        }
                    }

                    if (state.mode != PinyinMode.POLYPHONE) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = state.keepNonChinese,
                                    role = Role.Switch,
                                    onValueChange = onKeepNonChineseChanged,
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.pinyin_keep_non_chinese),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            ToolboxSwitch(
                                checked = state.keepNonChinese,
                                onCheckedChange = null,
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.pinyin_usage_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.pinyin_phrase_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "result") {
                ToolGroupCard(title = stringResource(R.string.pinyin_result_title)) {
                    when {
                        state.processing -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ToolboxLoadingIndicator(compact = true)
                            Text(
                                text = stringResource(R.string.pinyin_processing),
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ToolboxTextButton(onClick = onCancel) {
                                Text(stringResource(R.string.pinyin_btn_cancel))
                            }
                        }

                        state.cancelled -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.pinyin_cancelled),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ToolboxTextButton(onClick = onRetry) {
                                Text(stringResource(R.string.pinyin_btn_retry))
                            }
                        }

                        state.outcome == null -> Text(
                            text = stringResource(R.string.pinyin_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        state.outcome is PinyinOutcome.Failure -> Text(
                            text = stringResource((state.outcome as PinyinOutcome.Failure).error.messageResource()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )

                        else -> {
                            val success = state.outcome as PinyinOutcome.Success
                            val preview = remember(success.text) { previewOf(success.text) }
                            val statistics = success.statistics
                            ToolSectionHeader(stringResource(R.string.pinyin_stats_title))
                            ToolResultRow(
                                stringResource(R.string.pinyin_stats_chinese),
                                statistics.chineseChars.toString(),
                            )
                            ToolResultRow(
                                stringResource(R.string.pinyin_stats_non_chinese),
                                statistics.nonChineseChars.toString(),
                            )
                            ToolResultRow(
                                stringResource(R.string.pinyin_stats_total),
                                statistics.totalChars.toString(),
                            )
                            if (statistics.unsupportedChineseChars > 0) {
                                ToolResultRow(
                                    stringResource(R.string.pinyin_stats_unsupported),
                                    statistics.unsupportedChineseChars.toString(),
                                )
                                Text(
                                    text = stringResource(R.string.pinyin_unsupported_notice),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            if (success.text.isEmpty()) {
                                Text(
                                    text = stringResource(
                                        if (state.mode == PinyinMode.POLYPHONE) {
                                            R.string.pinyin_polyphone_empty
                                        } else {
                                            R.string.pinyin_empty_result
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .semantics { liveRegion = LiveRegionMode.Polite },
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                text = preview.first,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                                if (preview.second) {
                                    Text(
                                        text = stringResource(R.string.pinyin_preview_notice),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                ToolboxTextButton(onClick = { copyResult(success.text) }) {
                                    Text(stringResource(R.string.pinyin_btn_copy))
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }
}

@Composable
private fun <T> ChipGroup(
    title: String,
    values: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(label(value)) },
            )
        }
    }
}

/** Long results are shown truncated but always copied in full; a surrogate pair is never split. */
private fun previewOf(text: String): Pair<String, Boolean> {
    if (text.length <= PinyinLimits.PREVIEW_LENGTH) return text to false
    var end = PinyinLimits.PREVIEW_LENGTH
    if (Character.isHighSurrogate(text[end - 1])) end -= 1
    return text.substring(0, end) to true
}

@StringRes
private fun PinyinError.messageResource(): Int = when (this) {
    PinyinError.INPUT_TOO_LONG -> R.string.pinyin_error_too_long
    PinyinError.INVALID_UNICODE -> R.string.pinyin_error_unicode
    PinyinError.OUTPUT_TOO_LONG -> R.string.pinyin_error_output_too_long
    PinyinError.TIME_LIMIT -> R.string.pinyin_error_time_limit
    PinyinError.PROCESSING_FAILED -> R.string.pinyin_error_failed
}

@StringRes
private fun PinyinMode.labelResource(): Int = when (this) {
    PinyinMode.FULL -> R.string.pinyin_mode_full
    PinyinMode.INITIALS -> R.string.pinyin_mode_initials
    PinyinMode.POLYPHONE -> R.string.pinyin_mode_polyphone
}

@StringRes
private fun PinyinToneStyle.labelResource(): Int = when (this) {
    PinyinToneStyle.TONE_MARK -> R.string.pinyin_tone_mark
    PinyinToneStyle.TONE_NUMBER -> R.string.pinyin_tone_number
    PinyinToneStyle.NO_TONE -> R.string.pinyin_tone_none
}

@StringRes
private fun PinyinCaseMode.labelResource(): Int = when (this) {
    PinyinCaseMode.LOWER -> R.string.pinyin_case_lower
    PinyinCaseMode.UPPER -> R.string.pinyin_case_upper
    PinyinCaseMode.CAPITALIZE -> R.string.pinyin_case_capitalize
}

@StringRes
private fun PinyinSeparator.labelResource(): Int = when (this) {
    PinyinSeparator.SPACE -> R.string.pinyin_separator_space
    PinyinSeparator.HYPHEN -> R.string.pinyin_separator_hyphen
    PinyinSeparator.NONE -> R.string.pinyin_separator_none
}

@StringRes
private fun PinyinVCharType.labelResource(): Int = when (this) {
    PinyinVCharType.UNICODE -> R.string.pinyin_vchar_unicode
    PinyinVCharType.V -> R.string.pinyin_vchar_v
    PinyinVCharType.U_COLON -> R.string.pinyin_vchar_colon
}
