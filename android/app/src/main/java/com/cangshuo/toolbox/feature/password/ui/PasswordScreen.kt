package com.cangshuo.toolbox.feature.password.ui

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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.formatAmount
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolResultRow
import com.cangshuo.toolbox.core.ui.ToolboxSwitch
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.password.domain.PasswordError
import com.cangshuo.toolbox.feature.password.domain.PasswordOutcome
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun PasswordScreen(
    state: PasswordUiState,
    onLengthChanged: (String) -> Unit,
    onCountChanged: (String) -> Unit,
    onExcludeChanged: (String) -> Unit,
    onUppercaseChanged: (Boolean) -> Unit,
    onLowercaseChanged: (Boolean) -> Unit,
    onDigitsChanged: (Boolean) -> Unit,
    onSymbolsChanged: (Boolean) -> Unit,
    onExcludeSimilarChanged: (Boolean) -> Unit,
    onRequireEachClassChanged: (Boolean) -> Unit,
    onUniqueCharactersChanged: (Boolean) -> Unit,
    onRegenerate: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "options") {
            ToolGroupCard(title = stringResource(R.string.password_options_title)) {
                ToolboxTextField(
                    value = state.lengthText,
                    onValueChange = onLengthChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.password_length_label),
                    placeholder = stringResource(R.string.password_length_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                ToolboxTextField(
                    value = state.countText,
                    onValueChange = onCountChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.password_count_label),
                    placeholder = stringResource(R.string.password_count_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    text = stringResource(R.string.password_charset_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.uppercase,
                        onClick = { onUppercaseChanged(!state.uppercase) },
                        label = { Text(stringResource(R.string.password_charset_upper)) },
                    )
                    FilterChip(
                        selected = state.lowercase,
                        onClick = { onLowercaseChanged(!state.lowercase) },
                        label = { Text(stringResource(R.string.password_charset_lower)) },
                    )
                    FilterChip(
                        selected = state.digits,
                        onClick = { onDigitsChanged(!state.digits) },
                        label = { Text(stringResource(R.string.password_charset_digits)) },
                    )
                    FilterChip(
                        selected = state.symbols,
                        onClick = { onSymbolsChanged(!state.symbols) },
                        label = { Text(stringResource(R.string.password_charset_symbols)) },
                    )
                }
                SwitchRow(
                    title = stringResource(R.string.password_exclude_similar),
                    checked = state.excludeSimilar,
                    onCheckedChange = onExcludeSimilarChanged,
                )
                SwitchRow(
                    title = stringResource(R.string.password_require_each),
                    checked = state.requireEachClass,
                    onCheckedChange = onRequireEachClassChanged,
                )
                SwitchRow(
                    title = stringResource(R.string.password_unique),
                    checked = state.uniqueCharacters,
                    onCheckedChange = onUniqueCharactersChanged,
                )
                ToolboxTextField(
                    value = state.excludeText,
                    onValueChange = onExcludeChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.password_exclude_label),
                    placeholder = stringResource(R.string.password_exclude_hint),
                )
            }
        }

        when (val outcome = state.outcome) {
            null -> item(key = "placeholder") {
                PlaceholderSurface(stringResource(R.string.password_placeholder))
            }

            is PasswordOutcome.Failure -> item(key = "error") {
                ErrorSurface(outcome.error)
            }

            is PasswordOutcome.Success -> item(key = "result") {
                val strength = strengthLabel(outcome.passwords.first().entropyBits)
                ToolGroupCard(title = stringResource(R.string.password_result_title)) {
                    outcome.passwords.forEachIndexed { index, password ->
                        SelectionContainer {
                            Text(
                                text = password.value,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (index == 0 && outcome.passwords.size > 1) {
                            Text(
                                text = stringResource(R.string.password_more_hint, outcome.passwords.size - 1),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ToolResultRow(stringResource(R.string.password_pool_label), outcome.poolSize.toString())
                    ToolResultRow(
                        stringResource(R.string.password_entropy_label),
                        stringResource(
                            R.string.password_entropy_format,
                            formatAmount(outcome.passwords.first().entropyBits, 0, grouping = false),
                        ),
                    )
                    ToolResultRow(stringResource(R.string.password_strength_label), stringResource(strength))
                    Text(
                        text = stringResource(R.string.password_entropy_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolboxTextButton(onClick = onRegenerate) {
                            Text(stringResource(R.string.password_btn_regenerate))
                        }
                        ToolboxTextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(outcome.passwords.joinToString("\n") { it.value }))
                            },
                        ) { Text(stringResource(R.string.password_btn_copy)) }
                    }
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

@StringRes
private fun strengthLabel(entropyBits: Double): Int = when {
    entropyBits < 40 -> R.string.password_strength_weak
    entropyBits < 60 -> R.string.password_strength_moderate
    entropyBits < 80 -> R.string.password_strength_strong
    else -> R.string.password_strength_excellent
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
private fun ErrorSurface(error: PasswordError) {
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
private fun PasswordError.messageResource(): Int = when (this) {
    PasswordError.NO_CHARSET -> R.string.password_error_no_charset
    PasswordError.LENGTH_RANGE -> R.string.password_error_length
    PasswordError.COUNT_RANGE -> R.string.password_error_count
    PasswordError.STRICT_LENGTH -> R.string.password_error_strict
    PasswordError.UNIQUE_IMPOSSIBLE -> R.string.password_error_unique
    PasswordError.INPUT_TOO_LONG -> R.string.password_error_exclude_long
}

@Preview
@Composable
private fun PasswordScreenPreview() {
    ToolboxTheme { PasswordScreen(PasswordUiState(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}
