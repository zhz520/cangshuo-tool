package com.cangshuo.toolbox.feature.numberchinese.ui

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
import com.cangshuo.toolbox.feature.numberchinese.domain.ChineseNumberStyle
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberChineseError
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberChineseOutcome
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberDirection
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun NumberChineseScreen(
    state: NumberChineseUiState,
    onInputChanged: (String) -> Unit,
    onStyleSelected: (ChineseNumberStyle) -> Unit,
    onDirectionSelected: (NumberDirection) -> Unit,
    onColloquialChanged: (Boolean) -> Unit,
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
            ToolGroupCard(title = stringResource(R.string.number_chinese_input_title)) {
                ToolboxTextField(
                    value = state.input,
                    onValueChange = onInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.number_chinese_input_label),
                    placeholder = stringResource(R.string.number_chinese_input_hint),
                )
                if (state.input.isNotEmpty()) {
                    ToolboxTextButton(onClick = onClear) { Text(stringResource(R.string.number_chinese_btn_clear)) }
                }
            }
        }

        item(key = "options") {
            ToolGroupCard(title = stringResource(R.string.number_chinese_options_title)) {
                Text(
                    text = stringResource(R.string.number_chinese_style_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChineseNumberStyle.entries.forEach { style ->
                        FilterChip(
                            selected = state.style == style,
                            onClick = { onStyleSelected(style) },
                            label = { Text(stringResource(style.labelResource())) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.number_chinese_direction_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberDirection.entries.forEach { direction ->
                        FilterChip(
                            selected = state.direction == direction,
                            onClick = { onDirectionSelected(direction) },
                            label = { Text(stringResource(direction.labelResource())) },
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = state.colloquialTen,
                            role = Role.Switch,
                            onValueChange = onColloquialChanged,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.number_chinese_colloquial),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ToolboxSwitch(checked = state.colloquialTen, onCheckedChange = null)
                }
                Text(
                    text = stringResource(R.string.number_chinese_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (val outcome = state.outcome) {
            null -> item(key = "placeholder") {
                PlaceholderSurface(stringResource(R.string.number_chinese_empty))
            }

            is NumberChineseOutcome.Failure -> item(key = "error") {
                ErrorSurface(outcome.error)
            }

            is NumberChineseOutcome.Success -> item(key = "result") {
                ToolGroupCard(title = stringResource(R.string.number_chinese_result_title)) {
                    SelectionContainer {
                        Text(
                            text = outcome.text,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    ToolboxTextButton(
                        onClick = { clipboard.setText(AnnotatedString(outcome.text)) },
                    ) { Text(stringResource(R.string.number_chinese_btn_copy)) }
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
private fun ErrorSurface(error: NumberChineseError) {
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
private fun NumberChineseError.messageResource(): Int = when (this) {
    NumberChineseError.EMPTY -> R.string.number_chinese_empty
    NumberChineseError.INVALID_NUMBER -> R.string.number_chinese_error_number
    NumberChineseError.INVALID_CHINESE -> R.string.number_chinese_error_chinese
    NumberChineseError.TOO_LONG -> R.string.number_chinese_error_too_long
    NumberChineseError.MONEY_DECIMALS -> R.string.number_chinese_error_money
}

@StringRes
private fun ChineseNumberStyle.labelResource(): Int = when (this) {
    ChineseNumberStyle.LOWER -> R.string.number_chinese_style_lower
    ChineseNumberStyle.UPPER -> R.string.number_chinese_style_upper
    ChineseNumberStyle.MONEY -> R.string.number_chinese_style_money
}

@StringRes
private fun NumberDirection.labelResource(): Int = when (this) {
    NumberDirection.AUTO -> R.string.number_chinese_direction_auto
    NumberDirection.TO_CHINESE -> R.string.number_chinese_direction_to_chinese
    NumberDirection.TO_NUMBER -> R.string.number_chinese_direction_to_number
}

@Preview
@Composable
private fun NumberChineseScreenPreview() {
    ToolboxTheme { NumberChineseScreen(NumberChineseUiState(), {}, {}, {}, {}, {}) }
}
