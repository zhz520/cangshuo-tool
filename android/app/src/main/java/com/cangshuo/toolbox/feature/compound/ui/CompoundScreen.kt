package com.cangshuo.toolbox.feature.compound.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.formatAmount
import com.cangshuo.toolbox.core.ui.ToolResultRow
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolListItem
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.feature.compound.domain.CompoundError
import com.cangshuo.toolbox.feature.compound.domain.CompoundOutcome
import com.cangshuo.toolbox.feature.compound.domain.CompoundPeriod
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun CompoundScreen(
    state: CompoundUiState,
    onPrincipalChanged: (String) -> Unit,
    onContributionChanged: (String) -> Unit,
    onRateChanged: (String) -> Unit,
    onYearsChanged: (String) -> Unit,
    onPeriodSelected: (CompoundPeriod) -> Unit,
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
        item(key = "amounts") {
            ToolGroupCard(title = stringResource(R.string.compound_params_title)) {
                ToolboxTextField(
                    value = state.principalText,
                    onValueChange = onPrincipalChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.compound_principal_label),
                    placeholder = stringResource(R.string.compound_principal_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                ToolboxTextField(
                    value = state.contributionText,
                    onValueChange = onContributionChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.compound_contribution_label),
                    placeholder = stringResource(R.string.compound_contribution_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (state.principalText.isNotEmpty() || state.contributionText.isNotEmpty() ||
                    state.rateText.isNotEmpty() || state.yearsText.isNotEmpty()
                ) {
                    ToolboxTextButton(onClick = onClear) { Text(stringResource(R.string.compound_btn_clear)) }
                }
            }
        }

        item(key = "options") {
            ToolGroupCard(title = stringResource(R.string.compound_options_title)) {
                ToolboxTextField(
                    value = state.rateText,
                    onValueChange = onRateChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.compound_rate_label),
                    placeholder = stringResource(R.string.compound_rate_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                ToolboxTextField(
                    value = state.yearsText,
                    onValueChange = onYearsChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.compound_years_label),
                    placeholder = stringResource(R.string.compound_years_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.period == CompoundPeriod.MONTHLY,
                        onClick = { onPeriodSelected(CompoundPeriod.MONTHLY) },
                        label = { Text(stringResource(R.string.compound_period_monthly)) },
                    )
                    FilterChip(
                        selected = state.period == CompoundPeriod.ANNUAL,
                        onClick = { onPeriodSelected(CompoundPeriod.ANNUAL) },
                        label = { Text(stringResource(R.string.compound_period_annual)) },
                    )
                }
                Text(
                    text = stringResource(R.string.compound_period_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (val outcome = state.outcome) {
            null -> item(key = "placeholder") {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.compound_empty_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            is CompoundOutcome.Failure -> item(key = "error") {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(errorMessage(outcome.error)),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            is CompoundOutcome.Success -> {
                val result = outcome.result
                item(key = "result") {
                    val summary = stringResource(
                        R.string.compound_copy_summary,
                        result.rows.size.toString(),
                        formatAmount(result.futureValue, 2),
                        formatAmount(result.totalContributed, 2),
                        formatAmount(result.totalInterest, 2),
                    )
                    ToolGroupCard(title = stringResource(R.string.compound_result_title)) {
                        Text(
                            text = formatAmount(result.futureValue, 2),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.compound_result_fv),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ToolResultRow(stringResource(R.string.compound_result_contributed), formatAmount(result.totalContributed, 2))
                        ToolResultRow(stringResource(R.string.compound_result_interest), formatAmount(result.totalInterest, 2))
                        ToolResultRow(
                            stringResource(R.string.compound_result_multiplier),
                            stringResource(R.string.compound_multiplier_format, formatAmount(result.multiplier, 2, grouping = false)),
                        )
                        ToolboxTextButton(
                            onClick = { clipboard.setText(AnnotatedString(summary)) },
                        ) { Text(stringResource(R.string.compound_btn_copy)) }
                    }
                }
                item(key = "rows_header") {
                    ToolSectionHeader(title = stringResource(R.string.compound_rows_title))
                }
                result.rows.forEach { row ->
                    item(key = "row_" + row.year) {
                        ToolListItem(
                            title = stringResource(R.string.compound_row_year, row.year),
                            subtitleMaxLines = Int.MAX_VALUE,
                            dividerIndent = 0.dp,
                            subtitle = stringResource(
                                R.string.compound_row_detail,
                                formatAmount(row.balance, 2),
                                formatAmount(row.contributed, 2),
                                formatAmount(row.interest, 2),
                            ),
                        )
                    }
                }
            }
        }

        item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

@StringRes
private fun errorMessage(error: CompoundError): Int = when (error) {
    CompoundError.INVALID -> R.string.compound_error_invalid
    CompoundError.RANGE -> R.string.compound_error_range
    CompoundError.AMOUNT_LIMIT -> R.string.compound_error_limit
    CompoundError.RESULT_LIMIT -> R.string.compound_error_result_limit
    CompoundError.INPUT_TOO_LONG -> R.string.numeric_error_input_too_long
}

@Preview(showBackground = true, name = "Compound Screen Preview")
@Composable
private fun CompoundScreenPreview() {
    ToolboxTheme {
        CompoundScreen(
            state = CompoundUiState(principalText = "10000", rateText = "8", yearsText = "10"),
            onPrincipalChanged = {},
            onContributionChanged = {},
            onRateChanged = {},
            onYearsChanged = {},
            onPeriodSelected = {},
            onClear = {},
        )
    }
}
