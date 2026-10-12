package com.cangshuo.toolbox.feature.textcount.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolResultRow
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.textcount.domain.TextCountError
import com.cangshuo.toolbox.feature.textcount.domain.TextCountOutcome
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun TextCountScreen(
    state: TextCountUiState,
    onTextChanged: (String) -> Unit,
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
            ToolGroupCard(title = stringResource(R.string.text_count_input_title)) {
                ToolboxTextField(
                    value = state.text,
                    onValueChange = onTextChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.text_count_input_label),
                    placeholder = stringResource(R.string.text_count_input_hint),
                    singleLine = false,
                    minLines = 6,
                    maxLines = 12,
                )
                if (state.text.isNotBlank()) {
                    ToolboxTextButton(onClick = onClear) { Text(stringResource(R.string.text_count_btn_clear)) }
                }
            }
        }

        when (val outcome = state.outcome) {
            null -> item(key = "placeholder") {
                PlaceholderSurface(stringResource(R.string.text_count_empty))
            }

            is TextCountOutcome.Failure -> item(key = "error") {
                ErrorSurface(outcome.error)
            }

            is TextCountOutcome.Success -> item(key = "result") {
                val stats = outcome.stats
                val summary = stringResource(
                    R.string.text_count_copy_summary,
                    stats.charactersWithSpaces,
                    stats.charactersWithoutSpaces,
                    stats.cjkCharacters,
                    stats.latinWords,
                    stats.lines,
                    stats.paragraphs,
                    stats.utf8Bytes,
                )
                ToolGroupCard(title = stringResource(R.string.text_count_result_title)) {
                    Text(
                        text = stats.wordCount.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.text_count_words_total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ToolResultRow(
                        stringResource(R.string.text_count_chars_with_spaces),
                        stats.charactersWithSpaces.toString(),
                    )
                    ToolResultRow(
                        stringResource(R.string.text_count_chars_without_spaces),
                        stats.charactersWithoutSpaces.toString(),
                    )
                    ToolResultRow(stringResource(R.string.text_count_cjk), stats.cjkCharacters.toString())
                    ToolResultRow(stringResource(R.string.text_count_words_latin), stats.latinWords.toString())
                    ToolResultRow(stringResource(R.string.text_count_lines), stats.lines.toString())
                    ToolResultRow(stringResource(R.string.text_count_non_empty_lines), stats.nonEmptyLines.toString())
                    ToolResultRow(stringResource(R.string.text_count_paragraphs), stats.paragraphs.toString())
                    ToolResultRow(stringResource(R.string.text_count_bytes), stats.utf8Bytes.toString())
                    ToolResultRow(stringResource(R.string.text_count_reading), readingTime(stats.readingSeconds))
                    Text(
                        text = stringResource(R.string.text_count_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ToolboxTextButton(
                        onClick = { clipboard.setText(AnnotatedString(summary)) },
                    ) { Text(stringResource(R.string.text_count_btn_copy)) }
                }
            }
        }

        item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun readingTime(seconds: Int): String = if (seconds < 60) {
    stringResource(R.string.text_count_seconds, seconds)
} else {
    stringResource(R.string.text_count_minutes, seconds / 60, seconds % 60)
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
private fun ErrorSurface(error: TextCountError) {
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
private fun TextCountError.messageResource(): Int = when (this) {
    TextCountError.INPUT_TOO_LONG -> R.string.text_count_error_too_long
}

@Preview
@Composable
private fun TextCountScreenPreview() {
    ToolboxTheme { TextCountScreen(TextCountUiState(), {}, {}) }
}
