package com.cangshuo.toolbox.feature.regextester.ui

import android.content.ClipData
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.*
import com.cangshuo.toolbox.feature.regextester.domain.*
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun RegexTesterScreen(state: RegexTesterUiState, model: RegexTesterViewModel) {
    val request = state.request
    val clipboard = LocalClipboard.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.regex_tester_copied)
    val copyFailed = stringResource(R.string.regex_tester_copy_failed)
    fun copy(value: String) {
        scope.launch {
            try {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copiedText, value)))
                snackbar.showSnackbar(copiedText)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { snackbar.showSnackbar(copyFailed) }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(horizontal = ToolboxDimens.pageGutter,
                vertical = ToolboxDimens.pageTop),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "preset") {
                ToolSectionHeader(stringResource(R.string.regex_tester_examples))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RegexPreset.entries.forEach { preset ->
                        val sample = stringResource(preset.sampleResource())
                        FilterChip(selected = false, onClick = { model.applyPreset(preset, sample) },
                            label = { Text(stringResource(preset.labelResource())) })
                    }
                }
            }
            item(key = "pattern") {
                ToolboxTextField(request.pattern, model::setPattern, Modifier.fillMaxWidth(),
                    label = stringResource(R.string.regex_tester_pattern),
                    placeholder = stringResource(R.string.regex_tester_pattern_hint),
                    singleLine = false, minLines = 2, maxLines = 4,
                    isError = (state.outcome as? RegexOutcome.Failure)?.reason in
                        setOf(RegexFailure.EMPTY_PATTERN, RegexFailure.PATTERN_TOO_LONG, RegexFailure.INVALID_PATTERN, RegexFailure.TOO_COMPLEX))
                Text(stringResource(R.string.regex_tester_engine_note),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item(key = "flags") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(request.global, { model.setGlobal(!request.global) },
                        label = { Text(stringResource(R.string.regex_tester_global)) })
                    FilterChip(request.ignoreCase, { model.setIgnoreCase(!request.ignoreCase) },
                        label = { Text(stringResource(R.string.regex_tester_ignore_case)) })
                    FilterChip(request.multiline, { model.setMultiline(!request.multiline) },
                        label = { Text(stringResource(R.string.regex_tester_multiline)) })
                    FilterChip(request.dotAll, { model.setDotAll(!request.dotAll) },
                        label = { Text(stringResource(R.string.regex_tester_dot_all)) })
                }
                Text(stringResource(R.string.regex_tester_flags_help),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item(key = "input") {
                ToolboxTextField(request.input, model::setInput, Modifier.fillMaxWidth(),
                    label = stringResource(R.string.regex_tester_input), singleLine = false, minLines = 4, maxLines = 7,
                    isError = (state.outcome as? RegexOutcome.Failure)?.reason == RegexFailure.INPUT_TOO_LONG,
                    supportingText = { Text(stringResource(R.string.regex_tester_input_size,
                        request.input.length, RegexLimits.MAX_INPUT)) })
            }
            item(key = "mode") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(request.mode == RegexMode.MATCH, { model.setMode(RegexMode.MATCH) },
                        label = { Text(stringResource(R.string.regex_tester_mode_match)) })
                    FilterChip(request.mode == RegexMode.REPLACE, { model.setMode(RegexMode.REPLACE) },
                        label = { Text(stringResource(R.string.regex_tester_mode_replace)) })
                }
                if (request.mode == RegexMode.REPLACE) {
                    ToolboxTextField(request.replacement, model::setReplacement, Modifier.fillMaxWidth(),
                        label = stringResource(R.string.regex_tester_replacement), singleLine = false, maxLines = 4,
                        isError = (state.outcome as? RegexOutcome.Failure)?.reason in
                            setOf(RegexFailure.INVALID_REPLACEMENT, RegexFailure.REPLACEMENT_TOO_LONG))
                    Row(Modifier.fillMaxWidth().toggleable(request.expandGroups, role = Role.Switch,
                        onValueChange = model::setExpandGroups), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.regex_tester_expand_groups), Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        ToolboxSwitch(request.expandGroups, null)
                    }
                    Text(stringResource(if (request.expandGroups) R.string.regex_tester_group_help else R.string.regex_tester_literal_help),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "actions") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolboxButton(model::run, enabled = !state.processing) {
                        Text(stringResource(if (request.mode == RegexMode.MATCH) R.string.regex_tester_run else R.string.regex_tester_preview_replace))
                    }
                    if (state.processing) ToolboxOutlinedButton(model::cancel) { Text(stringResource(R.string.regex_tester_cancel)) }
                    else ToolboxTextButton(model::clear, enabled = request.input.isNotEmpty() || request.replacement.isNotEmpty()) {
                        Text(stringResource(R.string.regex_tester_clear))
                    }
                }
            }
            if (state.processing) item(key = "loading") { ToolboxLoadingState(stringResource(R.string.regex_tester_loading)) }
            when (val outcome = state.outcome) {
                null -> if (!state.processing) item(key = "ready") {
                    Text(stringResource(R.string.regex_tester_ready), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is RegexOutcome.Failure -> item(key = "error") {
                    Surface(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                        Text(stringResource(outcome.reason.messageResource()), Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                is RegexOutcome.Success -> {
                    val report = outcome.report
                    item(key = "summary") {
                        ToolSectionHeader(pluralStringResource(R.plurals.regex_tester_match_count, report.matches.size, report.matches.size))
                        ToolResultRow(stringResource(R.string.regex_tester_full_match),
                            stringResource(if (report.fullMatch) R.string.regex_tester_yes else R.string.regex_tester_no))
                        Text(stringResource(R.string.regex_tester_offsets), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (report.matches.isEmpty()) Text(stringResource(R.string.regex_tester_no_matches))
                        else ToolboxTextButton({ copy(report.matches.joinToString("\n") { it.value }) }) {
                            Text(stringResource(R.string.regex_tester_copy_matches))
                        }
                    }
                    if (request.input.isNotEmpty() && report.matches.isNotEmpty()) item(key = "highlight") {
                        HighlightPreview(request.input, report.matches)
                    }
                    report.replacement?.let { replaced -> item(key = "replaced") {
                        ToolGroupCard(title = stringResource(R.string.regex_tester_replaced)) {
                            ToolboxTextField(replaced, {}, Modifier.fillMaxWidth(), readOnly = true,
                                singleLine = false, minLines = 2, maxLines = 7)
                            ToolboxTextButton({ copy(replaced) }) { Text(stringResource(R.string.regex_tester_copy_result)) }
                        }
                    } }
                    itemsIndexed(report.matches, key = { index, _ -> "match-$index" }) { index, match ->
                        MatchRow(index, match, ::copy)
                    }
                }
            }
            item(key = "help") {
                ToolboxDivider(startIndent = 0.dp)
                Text(stringResource(R.string.regex_tester_help), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(ToolboxDimens.pageBottom))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }
}

@Composable
private fun HighlightPreview(input: String, matches: List<RegexMatch>) {
    var end = minOf(input.length, 4000)
    if (end < input.length && end > 0 && Character.isHighSurrogate(input[end - 1])) end--
    val color = MaterialTheme.colorScheme.primaryContainer
    val foreground = MaterialTheme.colorScheme.onPrimaryContainer
    val preview = remember(input, matches, color, foreground) {
        buildAnnotatedString {
            append(input.substring(0, end))
            matches.filter { it.start < end && it.start < it.end }.forEach {
                addStyle(SpanStyle(background = color, color = foreground), it.start, minOf(it.end, end))
            }
        }
    }
    ToolGroupCard(title = stringResource(R.string.regex_tester_highlight)) {
        SelectionContainer { Text(preview, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)) }
        if (end < input.length) Text(stringResource(R.string.regex_tester_preview_limited),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MatchRow(index: Int, match: RegexMatch, copy: (String) -> Unit) {
    var expanded by rememberSaveable(match) { mutableStateOf(false) }
    ToolGroupCard(title = stringResource(R.string.regex_tester_match_range, index + 1, match.start, match.end)) {
        if (match.value.isEmpty()) Text(stringResource(R.string.regex_tester_zero_width))
        else ToolboxTextField(match.value, {}, Modifier.fillMaxWidth(), readOnly = true,
            singleLine = false, maxLines = 4)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolboxTextButton({ copy(match.value) }) { Text(stringResource(R.string.regex_tester_copy_match)) }
            if (match.captures.isNotEmpty()) ToolboxTextButton({ expanded = !expanded }) {
                Text(pluralStringResource(if (expanded) R.plurals.regex_tester_hide_groups else R.plurals.regex_tester_show_groups,
                    match.captures.size, match.captures.size))
            }
        }
        if (expanded) match.captures.forEach { capture ->
            ToolboxDivider(startIndent = 0.dp)
            Text(if (capture.name != null) stringResource(R.string.regex_tester_named_group, capture.index, capture.name)
                else stringResource(R.string.regex_tester_group, capture.index), style = MaterialTheme.typography.titleSmall)
            if (capture.value == null) Text(stringResource(R.string.regex_tester_unmatched_group),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                Text(stringResource(R.string.regex_tester_range, capture.start, capture.end),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (capture.value.isEmpty()) Text(stringResource(R.string.regex_tester_zero_width))
                else ToolboxTextField(capture.value, {}, Modifier.fillMaxWidth(), readOnly = true,
                    singleLine = false, maxLines = 4)
                ToolboxTextButton({ copy(capture.value) }) { Text(stringResource(R.string.regex_tester_copy_group)) }
            }
        }
    }
}

@StringRes private fun RegexFailure.messageResource() = when (this) {
    RegexFailure.EMPTY_PATTERN -> R.string.regex_tester_error_empty
    RegexFailure.INPUT_TOO_LONG -> R.string.regex_tester_error_input
    RegexFailure.PATTERN_TOO_LONG -> R.string.regex_tester_error_pattern_size
    RegexFailure.REPLACEMENT_TOO_LONG -> R.string.regex_tester_error_replacement_size
    RegexFailure.INVALID_UNICODE -> R.string.regex_tester_error_unicode
    RegexFailure.INVALID_PATTERN -> R.string.regex_tester_error_pattern
    RegexFailure.TOO_COMPLEX -> R.string.regex_tester_error_complex
    RegexFailure.INVALID_REPLACEMENT -> R.string.regex_tester_error_replacement
    RegexFailure.TOO_MANY_MATCHES -> R.string.regex_tester_error_matches
    RegexFailure.RESULT_TOO_LARGE -> R.string.regex_tester_error_result
    RegexFailure.TIME_BUDGET -> R.string.regex_tester_error_time
    RegexFailure.MEMORY_LIMIT -> R.string.regex_tester_error_memory
    RegexFailure.PROCESSING_FAILED -> R.string.regex_tester_error_failed
}
@StringRes private fun RegexPreset.labelResource() = when (this) {
    RegexPreset.EMAIL -> R.string.regex_tester_preset_email
    RegexPreset.NUMBER -> R.string.regex_tester_preset_number
    RegexPreset.URL -> R.string.regex_tester_preset_url
    RegexPreset.DATE -> R.string.regex_tester_preset_date
    RegexPreset.KEY_VALUE -> R.string.regex_tester_preset_keyvalue
    RegexPreset.BLANK_LINE -> R.string.regex_tester_preset_blank
}
@StringRes private fun RegexPreset.sampleResource() = when (this) {
    RegexPreset.EMAIL -> R.string.regex_tester_sample_email
    RegexPreset.NUMBER -> R.string.regex_tester_sample_number
    RegexPreset.URL -> R.string.regex_tester_sample_url
    RegexPreset.DATE -> R.string.regex_tester_sample_date
    RegexPreset.KEY_VALUE -> R.string.regex_tester_sample_keyvalue
    RegexPreset.BLANK_LINE -> R.string.regex_tester_sample_blank
}
