package com.cangshuo.toolbox.feature.urlextract.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolboxSwitch
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.urlextract.domain.UrlExtractError
import com.cangshuo.toolbox.feature.urlextract.domain.UrlExtractLimits
import com.cangshuo.toolbox.feature.urlextract.domain.UrlExtractOutcome
import com.cangshuo.toolbox.feature.urlextract.domain.UrlOutputFormat
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun UrlExtractScreen(
    state: UrlExtractUiState,
    onTextChanged: (String) -> Unit,
    onBareDomainsChanged: (Boolean) -> Unit,
    onDedupeChanged: (Boolean) -> Unit,
    onFormatSelected: (UrlOutputFormat) -> Unit,
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
            ToolGroupCard(title = stringResource(R.string.url_extract_input_title)) {
                ToolboxTextField(
                    value = state.text,
                    onValueChange = onTextChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.url_extract_input_label),
                    placeholder = stringResource(R.string.url_extract_input_hint),
                    singleLine = false,
                    minLines = 5,
                    maxLines = 10,
                )
                if (state.text.isNotBlank()) {
                    ToolboxTextButton(onClick = onClear) { Text(stringResource(R.string.url_extract_btn_clear)) }
                }
            }
        }

        item(key = "options") {
            ToolGroupCard(title = stringResource(R.string.url_extract_mode_title)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.includeBareDomains,
                        onClick = { onBareDomainsChanged(false) },
                        label = { Text(stringResource(R.string.url_extract_mode_strict)) },
                    )
                    FilterChip(
                        selected = state.includeBareDomains,
                        onClick = { onBareDomainsChanged(true) },
                        label = { Text(stringResource(R.string.url_extract_mode_fuzzy)) },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = state.dedupe, role = Role.Switch, onValueChange = onDedupeChanged),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.url_extract_dedupe),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ToolboxSwitch(checked = state.dedupe, onCheckedChange = null)
                }
                Text(
                    text = stringResource(R.string.url_extract_format_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.format == UrlOutputFormat.LINES,
                        onClick = { onFormatSelected(UrlOutputFormat.LINES) },
                        label = { Text(stringResource(R.string.url_extract_format_lines)) },
                    )
                    FilterChip(
                        selected = state.format == UrlOutputFormat.MARKDOWN,
                        onClick = { onFormatSelected(UrlOutputFormat.MARKDOWN) },
                        label = { Text(stringResource(R.string.url_extract_format_markdown)) },
                    )
                }
            }
        }

        when (val outcome = state.outcome) {
            null -> item(key = "placeholder") {
                PlaceholderSurface(stringResource(R.string.url_extract_empty))
            }

            is UrlExtractOutcome.Failure -> item(key = "error") {
                ErrorSurface(outcome.error)
            }

            is UrlExtractOutcome.Success -> item(key = "result") {
                val result = outcome.result
                ToolGroupCard(title = stringResource(R.string.url_extract_result_title)) {
                    Text(
                        text = stringResource(R.string.url_extract_count, result.links.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    when {
                        result.links.isEmpty() -> Text(
                            text = stringResource(R.string.url_extract_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        else -> {
                            val displayed = result.links.take(UrlExtractLimits.MAX_DISPLAYED)
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    displayed.forEach { link ->
                                        Text(
                                            text = link,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                            if (result.links.size > displayed.size) {
                                Text(
                                    text = stringResource(
                                        R.string.url_extract_show_note,
                                        displayed.size,
                                        result.links.size,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (result.truncated) {
                                Text(
                                    text = stringResource(R.string.url_extract_truncated_note, UrlExtractLimits.MAX_RESULTS),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            ToolboxTextButton(
                                onClick = { clipboard.setText(AnnotatedString(state.copyText)) },
                            ) { Text(stringResource(R.string.url_extract_btn_copy)) }
                        }
                    }
                }
            }
        }

        item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

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
private fun ErrorSurface(error: UrlExtractError) {
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
private fun UrlExtractError.messageResource(): Int = when (this) {
    UrlExtractError.INPUT_TOO_LONG -> R.string.url_extract_error_too_long
}

@Preview
@Composable
private fun UrlExtractScreenPreview() {
    ToolboxTheme { UrlExtractScreen(UrlExtractUiState(), {}, {}, {}, {}, {}) }
}
