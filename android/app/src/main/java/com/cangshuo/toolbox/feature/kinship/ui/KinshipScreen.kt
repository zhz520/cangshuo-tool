package com.cangshuo.toolbox.feature.kinship.ui

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
import com.cangshuo.toolbox.feature.kinship.domain.KinshipError
import com.cangshuo.toolbox.feature.kinship.domain.KinshipOutcome
import com.cangshuo.toolbox.feature.kinship.domain.KinshipSex
import com.cangshuo.toolbox.feature.kinship.domain.KinshipType
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

private val quickRelations = listOf("爸爸", "妈妈", "哥哥", "姐姐", "弟弟", "妹妹", "老公", "老婆", "儿子", "女儿")

@Composable
fun KinshipScreen(
    state: KinshipUiState,
    onTextChanged: (String) -> Unit,
    onTargetChanged: (String) -> Unit,
    onAppendRelation: (String) -> Unit,
    onRemoveLast: () -> Unit,
    onSexSelected: (KinshipSex) -> Unit,
    onTypeSelected: (KinshipType) -> Unit,
    onReverseChanged: (Boolean) -> Unit,
    onOptimalChanged: (Boolean) -> Unit,
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
            ToolGroupCard(title = stringResource(R.string.kinship_input_title)) {
                ToolboxTextField(
                    value = state.text,
                    onValueChange = onTextChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.kinship_input_label),
                    placeholder = stringResource(R.string.kinship_input_hint),
                )
                Text(
                    text = stringResource(R.string.kinship_quick_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickRelations.forEach { name ->
                        FilterChip(
                            selected = false,
                            onClick = { onAppendRelation(name) },
                            label = { Text(name) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolboxTextButton(onClick = onRemoveLast) {
                        Text(stringResource(R.string.kinship_btn_remove_last))
                    }
                    ToolboxTextButton(onClick = onClear) {
                        Text(stringResource(R.string.kinship_btn_clear))
                    }
                }
            }
        }

        item(key = "options") {
            ToolGroupCard(title = stringResource(R.string.kinship_options_title)) {
                Text(
                    text = stringResource(R.string.kinship_sex_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KinshipSex.entries.forEach { sex ->
                        FilterChip(
                            selected = state.sex == sex,
                            onClick = { onSexSelected(sex) },
                            label = { Text(stringResource(sex.labelResource())) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.kinship_direction_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.reverse,
                        onClick = { onReverseChanged(false) },
                        label = { Text(stringResource(R.string.kinship_direction_call)) },
                    )
                    FilterChip(
                        selected = state.reverse,
                        onClick = { onReverseChanged(true) },
                        label = { Text(stringResource(R.string.kinship_direction_reverse)) },
                    )
                }
                Text(
                    text = stringResource(R.string.kinship_type_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KinshipType.entries.forEach { type ->
                        FilterChip(
                            selected = state.type == type,
                            onClick = { onTypeSelected(type) },
                            label = { Text(stringResource(type.labelResource())) },
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = state.optimal, role = Role.Switch, onValueChange = onOptimalChanged),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.kinship_optimal),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ToolboxSwitch(checked = state.optimal, onCheckedChange = null)
                }
                ToolboxTextField(
                    value = state.target,
                    onValueChange = onTargetChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.kinship_target_label),
                    placeholder = stringResource(R.string.kinship_target_hint),
                )
                Text(
                    text = stringResource(R.string.kinship_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (val outcome = state.outcome) {
            null -> item(key = "placeholder") {
                PlaceholderSurface(stringResource(R.string.kinship_empty))
            }

            is KinshipOutcome.Failure -> item(key = "error") {
                ErrorSurface(outcome.error)
            }

            is KinshipOutcome.Success -> item(key = "result") {
                val titles = outcome.titles
                ToolGroupCard(title = stringResource(R.string.kinship_result_title)) {
                    if (titles.isEmpty()) {
                        Text(
                            text = stringResource(R.string.kinship_result_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                titles.forEachIndexed { index, title ->
                                    Text(
                                        text = title,
                                        style = if (index == 0 && titles.size == 1) {
                                            MaterialTheme.typography.headlineSmall
                                        } else {
                                            MaterialTheme.typography.titleMedium
                                        },
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                        ToolboxTextButton(
                            onClick = { clipboard.setText(AnnotatedString(titles.joinToString("、"))) },
                        ) { Text(stringResource(R.string.kinship_btn_copy)) }
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
private fun ErrorSurface(error: KinshipError) {
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
private fun KinshipError.messageResource(): Int = when (this) {
    KinshipError.EMPTY_INPUT -> R.string.kinship_empty
    KinshipError.INPUT_TOO_LONG -> R.string.kinship_error_too_long
    KinshipError.DATA_UNAVAILABLE -> R.string.kinship_error_data
}

@StringRes
private fun KinshipSex.labelResource(): Int = when (this) {
    KinshipSex.UNSPECIFIED -> R.string.kinship_sex_any
    KinshipSex.MALE -> R.string.kinship_sex_male
    KinshipSex.FEMALE -> R.string.kinship_sex_female
}

@StringRes
private fun KinshipType.labelResource(): Int = when (this) {
    KinshipType.TITLE -> R.string.kinship_type_title_name
    KinshipType.CHAIN -> R.string.kinship_type_chain
    KinshipType.PAIR -> R.string.kinship_type_pair
}

@Preview
@Composable
private fun KinshipScreenPreview() {
    ToolboxTheme { KinshipScreen(KinshipUiState(), {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}
