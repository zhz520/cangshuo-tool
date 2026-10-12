package com.cangshuo.toolbox.feature.similarity.ui

import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
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
import com.cangshuo.toolbox.core.model.formatAmount
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolResultRow
import com.cangshuo.toolbox.core.ui.ToolboxSwitch
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.similarity.domain.SimilarityError
import com.cangshuo.toolbox.feature.similarity.domain.SimilarityOutcome
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun SimilarityScreen(
    state: SimilarityUiState,
    onFirstChanged: (String) -> Unit,
    onSecondChanged: (String) -> Unit,
    onIgnoreCaseChanged: (Boolean) -> Unit,
    onIgnoreWhitespaceChanged: (Boolean) -> Unit,
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
        item(key = "first") {
            ToolGroupCard(title = stringResource(R.string.similarity_first_title)) {
                ToolboxTextField(
                    value = state.first,
                    onValueChange = onFirstChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.similarity_first_label),
                    placeholder = stringResource(R.string.similarity_input_hint),
                    singleLine = false,
                    minLines = 4,
                    maxLines = 8,
                )
            }
        }

        item(key = "second") {
            ToolGroupCard(title = stringResource(R.string.similarity_second_title)) {
                ToolboxTextField(
                    value = state.second,
                    onValueChange = onSecondChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.similarity_second_label),
                    placeholder = stringResource(R.string.similarity_input_hint),
                    singleLine = false,
                    minLines = 4,
                    maxLines = 8,
                )
                if (state.first.isNotEmpty() || state.second.isNotEmpty()) {
                    ToolboxTextButton(onClick = onClear) { Text(stringResource(R.string.similarity_btn_clear)) }
                }
            }
        }

        item(key = "options") {
            ToolGroupCard(title = stringResource(R.string.similarity_options_title)) {
                SwitchRow(stringResource(R.string.similarity_ignore_case), state.ignoreCase, onIgnoreCaseChanged)
                SwitchRow(
                    stringResource(R.string.similarity_ignore_whitespace),
                    state.ignoreWhitespace,
                    onIgnoreWhitespaceChanged,
                )
                Text(
                    text = stringResource(R.string.similarity_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (val outcome = state.outcome) {
            null -> item(key = "placeholder") {
                PlaceholderSurface(stringResource(R.string.similarity_empty))
            }

            is SimilarityOutcome.Failure -> item(key = "error") {
                ErrorSurface(outcome.error)
            }

            is SimilarityOutcome.Success -> item(key = "result") {
                val result = outcome.result
                val summary = stringResource(
                    R.string.similarity_copy_summary,
                    stringResource(if (result.identical) R.string.similarity_identical_yes else R.string.similarity_identical_no),
                    result.distance.toString(),
                    formatAmount(result.similarityPercent, 1, grouping = false),
                    formatAmount(result.jaccardPercent, 1, grouping = false),
                    result.longestCommonSubstring.toString(),
                    result.commonPrefix.toString(),
                )
                ToolGroupCard(title = stringResource(R.string.similarity_result_title)) {
                    ToolResultRow(
                        stringResource(R.string.similarity_identical),
                        stringResource(if (result.identical) R.string.similarity_identical_yes else R.string.similarity_identical_no),
                    )
                    ToolResultRow(stringResource(R.string.similarity_distance), result.distance.toString())
                    ToolResultRow(
                        stringResource(R.string.similarity_similarity),
                        stringResource(R.string.similarity_percent_format, formatAmount(result.similarityPercent, 1, grouping = false)),
                    )
                    ToolResultRow(
                        stringResource(R.string.similarity_jaccard),
                        stringResource(R.string.similarity_percent_format, formatAmount(result.jaccardPercent, 1, grouping = false)),
                    )
                    ToolResultRow(
                        stringResource(R.string.similarity_lcs),
                        result.longestCommonSubstring.toString(),
                    )
                    ToolResultRow(stringResource(R.string.similarity_prefix), result.commonPrefix.toString())
                    ToolboxTextButton(
                        onClick = { clipboard.setText(AnnotatedString(summary)) },
                    ) { Text(stringResource(R.string.similarity_btn_copy)) }
                }
            }
        }

        item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ToolboxSwitch(checked = checked, onCheckedChange = null)
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
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorSurface(error: SimilarityError) {
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
private fun SimilarityError.messageResource(): Int = when (this) {
    SimilarityError.INPUT_TOO_LONG -> R.string.similarity_error_too_long
}

@Preview
@Composable
private fun SimilarityScreenPreview() {
    ToolboxTheme { SimilarityScreen(SimilarityUiState(), {}, {}, {}, {}, {}) }
}
