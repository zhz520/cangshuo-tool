package com.cangshuo.toolbox.feature.charconvert.ui

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
import com.cangshuo.toolbox.feature.charconvert.domain.CaseMode
import com.cangshuo.toolbox.feature.charconvert.domain.CharConvertError
import com.cangshuo.toolbox.feature.charconvert.domain.CharConvertOutcome
import com.cangshuo.toolbox.feature.charconvert.domain.WidthMode
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun CharConvertScreen(
    state: CharConvertUiState,
    onInputChanged: (String) -> Unit,
    onWidthSelected: (WidthMode) -> Unit,
    onCaseSelected: (CaseMode) -> Unit,
    onCollapseChanged: (Boolean) -> Unit,
    onZeroWidthChanged: (Boolean) -> Unit,
    onNewlinesChanged: (Boolean) -> Unit,
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
            ToolGroupCard(title = stringResource(R.string.char_convert_input_title)) {
                ToolboxTextField(
                    value = state.input,
                    onValueChange = onInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.char_convert_input_label),
                    placeholder = stringResource(R.string.char_convert_input_hint),
                    singleLine = false,
                    minLines = 5,
                    maxLines = 10,
                )
                if (state.input.isNotEmpty()) {
                    ToolboxTextButton(onClick = onClear) { Text(stringResource(R.string.char_convert_btn_clear)) }
                }
            }
        }

        item(key = "options") {
            ToolGroupCard(title = stringResource(R.string.char_convert_options_title)) {
                Text(
                    text = stringResource(R.string.char_convert_width_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidthMode.entries.forEach { width ->
                        FilterChip(
                            selected = state.width == width,
                            onClick = { onWidthSelected(width) },
                            label = { Text(stringResource(width.labelResource())) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.char_convert_case_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaseMode.entries.forEach { case ->
                        FilterChip(
                            selected = state.case == case,
                            onClick = { onCaseSelected(case) },
                            label = { Text(stringResource(case.labelResource())) },
                        )
                    }
                }
                SwitchRow(stringResource(R.string.char_convert_collapse), state.collapseWhitespace, onCollapseChanged)
                SwitchRow(stringResource(R.string.char_convert_zero_width), state.removeZeroWidth, onZeroWidthChanged)
                SwitchRow(stringResource(R.string.char_convert_newlines), state.unifyNewlines, onNewlinesChanged)
                Text(
                    text = stringResource(R.string.char_convert_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (val outcome = state.outcome) {
            null -> item(key = "placeholder") {
                PlaceholderSurface(stringResource(R.string.char_convert_empty))
            }

            is CharConvertOutcome.Failure -> item(key = "error") {
                ErrorSurface(outcome.error)
            }

            is CharConvertOutcome.Success -> item(key = "result") {
                ToolGroupCard(title = stringResource(R.string.char_convert_result_title)) {
                    SelectionContainer {
                        Text(
                            text = outcome.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    ToolboxTextButton(
                        onClick = { clipboard.setText(AnnotatedString(outcome.text)) },
                    ) { Text(stringResource(R.string.char_convert_btn_copy)) }
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
private fun ErrorSurface(error: CharConvertError) {
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
private fun CharConvertError.messageResource(): Int = when (this) {
    CharConvertError.INPUT_TOO_LONG -> R.string.char_convert_error_too_long
}

@StringRes
private fun WidthMode.labelResource(): Int = when (this) {
    WidthMode.NONE -> R.string.char_convert_width_none
    WidthMode.TO_HALF -> R.string.char_convert_width_half
    WidthMode.TO_FULL -> R.string.char_convert_width_full
}

@StringRes
private fun CaseMode.labelResource(): Int = when (this) {
    CaseMode.NONE -> R.string.char_convert_case_none
    CaseMode.UPPER -> R.string.char_convert_case_upper
    CaseMode.LOWER -> R.string.char_convert_case_lower
}

@Preview
@Composable
private fun CharConvertScreenPreview() {
    ToolboxTheme { CharConvertScreen(CharConvertUiState(), {}, {}, {}, {}, {}, {}, {}) }
}
