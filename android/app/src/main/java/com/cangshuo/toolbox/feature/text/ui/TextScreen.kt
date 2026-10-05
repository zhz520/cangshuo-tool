package com.cangshuo.toolbox.feature.text.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.feature.text.domain.TextCategoryGroup
import com.cangshuo.toolbox.feature.text.domain.TextOperation
import com.cangshuo.toolbox.feature.text.domain.TextProcessingPolicy
import com.cangshuo.toolbox.feature.text.domain.TextStatistics
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextScreen(
    state: TextUiState,
    onGroupChanged: (TextCategoryGroup) -> Unit,
    onOperationChanged: (TextOperation) -> Unit,
    onInputChanged: (String) -> Unit,
    onFindChanged: (String) -> Unit,
    onReplaceChanged: (String) -> Unit,
    onMatchCaseChanged: (Boolean) -> Unit,
    onUseRegexChanged: (Boolean) -> Unit,
    onExpandGroupsChanged: (Boolean) -> Unit,
    onMultilineChanged: (Boolean) -> Unit,
    onDotAllChanged: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onSwap: () -> Unit,
    onLoadSample: () -> Unit,
    onClear: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val preview = remember(state.outputText) {
        var end = minOf(state.outputText.length, PREVIEW_LENGTH)
        if (end < state.outputText.length && end > 0 && state.outputText[end - 1].isHighSurrogate()) end--
        state.outputText.substring(0, end)
    }
    Box(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "input") {
                TextCard {
                    Text(stringResource(R.string.text_input_label), style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = onLoadSample) { Text(stringResource(R.string.text_btn_sample)) }
                        TextButton(onClick = { clipboard.getText()?.text?.let(onInputChanged) }) {
                            Text(stringResource(R.string.text_btn_paste))
                        }
                        TextButton(onClick = onClear) { Text(stringResource(R.string.text_btn_clear)) }
                    }
                    state.inputErrorRes?.let { TextError(it) }
                    OutlinedTextField(state.inputText, onInputChanged, Modifier.fillMaxWidth(), minLines = 4, maxLines = 10,
                        placeholder = { Text(stringResource(R.string.text_placeholder_input)) },
                        supportingText = { Text(stringResource(R.string.text_input_count,
                            state.inputText.length, TextProcessingPolicy.MAX_INPUT_LENGTH)) })
                }
            }
            item(key = "statistics") {
                TextCard {
                    Text(stringResource(R.string.text_stats_title), style = MaterialTheme.typography.titleSmall)
                    val stats = state.stats
                    if (stats != null) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBadge(stringResource(R.string.text_stat_chars), stats.characters)
                            StatBadge(stringResource(R.string.text_stat_chars_no_space), stats.charactersNoSpaces)
                            StatBadge(stringResource(R.string.text_stat_words), stats.words)
                            StatBadge(stringResource(R.string.text_stat_cjk), stats.cjkCharacters)
                            StatBadge(stringResource(R.string.text_stat_lines), stats.lines)
                            StatBadge(stringResource(R.string.text_stat_non_empty_lines), stats.nonEmptyLines)
                            StatBadge(stringResource(R.string.text_stat_bytes), stats.bytes)
                        }
                    } else if (state.isProcessing) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ToolboxLoadingIndicator(compact = true)
                            Text(stringResource(R.string.text_processing))
                        }
                    } else Text(stringResource(R.string.text_stats_pending), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "options") {
                TextCard {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextCategoryGroup.entries.forEach { group ->
                            FilterChip(state.selectedGroup == group, { onGroupChanged(group) },
                                label = { Text(stringResource(group.nameRes())) })
                        }
                    }
                    if (state.selectedGroup == TextCategoryGroup.FIND_REPLACE) {
                        OutlinedTextField(state.findText, onFindChanged, Modifier.fillMaxWidth(), maxLines = 3,
                            label = { Text(stringResource(R.string.text_fr_find_label)) },
                            placeholder = { Text(stringResource(R.string.text_fr_find_placeholder)) })
                        OutlinedTextField(state.replaceText, onReplaceChanged, Modifier.fillMaxWidth(), maxLines = 3,
                            label = { Text(stringResource(R.string.text_fr_replace_label)) },
                            placeholder = { Text(stringResource(R.string.text_fr_replace_placeholder)) })
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(state.matchCase, { onMatchCaseChanged(!state.matchCase) },
                                label = { Text(stringResource(R.string.text_fr_match_case)) })
                            FilterChip(state.useRegex, { onUseRegexChanged(!state.useRegex) },
                                label = { Text(stringResource(R.string.text_fr_use_regex)) })
                            if (state.useRegex) {
                                FilterChip(state.expandGroups, { onExpandGroupsChanged(!state.expandGroups) },
                                    label = { Text(stringResource(R.string.text_fr_expand_groups)) })
                                FilterChip(state.multiline, { onMultilineChanged(!state.multiline) },
                                    label = { Text(stringResource(R.string.text_fr_multiline)) })
                                FilterChip(state.dotAll, { onDotAllChanged(!state.dotAll) },
                                    label = { Text(stringResource(R.string.text_fr_dot_all)) })
                            }
                        }
                        Text(stringResource(if (state.useRegex) R.string.text_regex_help else R.string.text_literal_help),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextOperation.entries.filter { it.group == state.selectedGroup }.forEach { operation ->
                                FilterChip(state.selectedOperation == operation, { onOperationChanged(operation) },
                                    label = { Text(stringResource(operation.nameRes())) })
                            }
                        }
                    }
                }
            }
            if (state.isProcessing) {
                item(key = "processing") { ToolboxLoadingState(stringResource(R.string.text_processing)) }
                item(key = "cancel") { OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.text_btn_cancel)) } }
            } else {
                item(key = "result") {
                    TextCard {
                        Text(stringResource(R.string.text_output_label), style = MaterialTheme.typography.titleMedium)
                        state.processingErrorRes?.let { TextError(it) }
                        state.statusMessageRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (state.processingErrorRes != null || state.statusMessageRes != null) {
                            TextButton(onClick = onRetry) { Text(stringResource(R.string.text_btn_retry)) }
                        }
                        state.matchCount?.let { Text(stringResource(R.string.text_match_count, it),
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (state.hasResult) {
                            if (preview.length < state.outputText.length) Text(stringResource(R.string.text_preview_limited),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                if (state.outputText.isEmpty()) Text(stringResource(R.string.text_output_empty), Modifier.padding(12.dp))
                                else SelectionContainer(Modifier.padding(12.dp)) {
                                    Text(preview, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(state.outputText)) }) {
                                    Text(stringResource(R.string.text_btn_copy))
                                }
                                OutlinedButton(onClick = onSwap) { Text(stringResource(R.string.text_btn_swap)) }
                            }
                        } else if (state.processingErrorRes == null && state.statusMessageRes == null) {
                            Text(stringResource(R.string.text_output_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TextCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun TextError(resource: Int) {
    Text(stringResource(resource), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun StatBadge(label: String, value: Int) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(value.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

private fun TextCategoryGroup.nameRes(): Int = when (this) {
    TextCategoryGroup.CASE -> R.string.text_group_case
    TextCategoryGroup.WHITESPACE -> R.string.text_group_whitespace
    TextCategoryGroup.LINES -> R.string.text_group_lines
    TextCategoryGroup.FIND_REPLACE -> R.string.text_group_find_replace
}

private fun TextOperation.nameRes(): Int = when (this) {
    TextOperation.UPPERCASE -> R.string.text_op_uppercase
    TextOperation.LOWERCASE -> R.string.text_op_lowercase
    TextOperation.TITLE_CASE -> R.string.text_op_title_case
    TextOperation.SENTENCE_CASE -> R.string.text_op_sentence_case
    TextOperation.CAMEL_CASE -> R.string.text_op_camel_case
    TextOperation.PASCAL_CASE -> R.string.text_op_pascal_case
    TextOperation.SNAKE_CASE -> R.string.text_op_snake_case
    TextOperation.KEBAB_CASE -> R.string.text_op_kebab_case
    TextOperation.CONSTANT_CASE -> R.string.text_op_constant_case
    TextOperation.TRIM_LINES -> R.string.text_op_trim_lines
    TextOperation.REMOVE_EMPTY_LINES -> R.string.text_op_remove_empty_lines
    TextOperation.COLLAPSE_SPACES -> R.string.text_op_collapse_spaces
    TextOperation.REMOVE_ALL_SPACES -> R.string.text_op_remove_all_spaces
    TextOperation.SORT_AZ -> R.string.text_op_sort_az
    TextOperation.SORT_ZA -> R.string.text_op_sort_za
    TextOperation.NATURAL_SORT -> R.string.text_op_natural_sort
    TextOperation.DEDUPLICATE -> R.string.text_op_deduplicate
    TextOperation.REVERSE_LINES -> R.string.text_op_reverse_lines
    TextOperation.NUMBER_LINES -> R.string.text_op_number_lines
}

private const val PREVIEW_LENGTH = 8000

@Preview(name = "Text Chinese", locale = "zh", widthDp = 360, showBackground = true)
@Preview(name = "Text English", locale = "en", widthDp = 360, showBackground = true)
@Composable
private fun TextScreenPreview() {
    ToolboxTheme {
        TextScreen(TextUiState(inputText = "Hello World", outputText = "HELLO WORLD", hasResult = true,
            stats = TextStatistics(characters = 11, words = 2, lines = 1, bytes = 11)),
            onGroupChanged = {}, onOperationChanged = {}, onInputChanged = {}, onFindChanged = {}, onReplaceChanged = {},
            onMatchCaseChanged = {}, onUseRegexChanged = {}, onExpandGroupsChanged = {}, onMultilineChanged = {},
            onDotAllChanged = {}, onCancel = {}, onRetry = {}, onSwap = {}, onLoadSample = {}, onClear = {})
    }
}
