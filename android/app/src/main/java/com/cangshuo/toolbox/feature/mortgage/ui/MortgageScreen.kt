package com.cangshuo.toolbox.feature.mortgage.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageError
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageLimits
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageMethod
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageOutcome
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageResult
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun MortgageScreen(
    state: MortgageUiState,
    onAmountChanged: (String) -> Unit,
    onRateChanged: (String) -> Unit,
    onYearsChanged: (String) -> Unit,
    onMethodSelected: (MortgageMethod) -> Unit,
    onShowAllRowsChanged: (Boolean) -> Unit,
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
        item(key = "loan") {
            ToolGroupCard(title = stringResource(R.string.mortgage_loan_title)) {
                ToolboxTextField(
                    value = state.amountText,
                    onValueChange = onAmountChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.mortgage_amount_label),
                    placeholder = stringResource(R.string.mortgage_amount_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                ToolboxTextField(
                    value = state.rateText,
                    onValueChange = onRateChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.mortgage_rate_label),
                    placeholder = stringResource(R.string.mortgage_rate_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                ToolboxTextField(
                    value = state.yearsText,
                    onValueChange = onYearsChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.mortgage_years_label),
                    placeholder = stringResource(R.string.mortgage_years_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    text = stringResource(R.string.mortgage_term_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MortgageLimits.TERM_PRESETS.forEach { term ->
                        FilterChip(
                            selected = state.yearsText == term.toString(),
                            onClick = { onYearsChanged(term.toString()) },
                            label = { Text(stringResource(R.string.mortgage_term_years, term)) },
                        )
                    }
                }
                if (state.amountText.isNotEmpty() || state.rateText.isNotEmpty() || state.yearsText.isNotEmpty()) {
                    ToolboxTextButton(onClick = onClear) { Text(stringResource(R.string.mortgage_btn_clear)) }
                }
            }
        }

        item(key = "method") {
            ToolGroupCard(title = stringResource(R.string.mortgage_method_title)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.method == MortgageMethod.EQUAL_INSTALLMENT,
                        onClick = { onMethodSelected(MortgageMethod.EQUAL_INSTALLMENT) },
                        label = { Text(stringResource(R.string.mortgage_method_equal_installment)) },
                    )
                    FilterChip(
                        selected = state.method == MortgageMethod.EQUAL_PRINCIPAL,
                        onClick = { onMethodSelected(MortgageMethod.EQUAL_PRINCIPAL) },
                        label = { Text(stringResource(R.string.mortgage_method_equal_principal)) },
                    )
                }
                Text(
                    text = stringResource(R.string.mortgage_disclaimer),
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
                            stringResource(R.string.mortgage_empty_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            is MortgageOutcome.Failure -> item(key = "error") {
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

            is MortgageOutcome.Success -> {
                val result = outcome.result
                item(key = "result") {
                    val summary = stringResource(
                        R.string.mortgage_copy_summary,
                        stringResource(result.method.labelResource()),
                        formatAmount(result.totalInterest, 2),
                        formatAmount(result.totalPayment, 2),
                    )
                    ToolGroupCard(title = stringResource(R.string.mortgage_result_title)) {
                        if (result.method == MortgageMethod.EQUAL_INSTALLMENT) {
                            Text(
                                text = formatAmount(result.monthlyPayment ?: result.firstPayment, 2),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.mortgage_result_monthly),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = formatAmount(result.firstPayment, 2),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.mortgage_result_first),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ToolResultRow(stringResource(R.string.mortgage_result_last), formatAmount(result.lastPayment, 2))
                            ToolResultRow(
                                stringResource(R.string.mortgage_result_decrease),
                                formatAmount(result.monthlyDecrease ?: java.math.BigDecimal.ZERO, 2),
                            )
                        }
                        ToolResultRow(stringResource(R.string.mortgage_result_total_interest), formatAmount(result.totalInterest, 2))
                        ToolResultRow(stringResource(R.string.mortgage_result_total_payment), formatAmount(result.totalPayment, 2))
                        ToolResultRow(
                            stringResource(R.string.mortgage_result_months),
                            stringResource(R.string.mortgage_result_months_format, result.months),
                        )
                        ToolboxTextButton(
                            onClick = { clipboard.setText(AnnotatedString(summary)) },
                        ) { Text(stringResource(R.string.mortgage_btn_copy)) }
                    }
                }

                item(key = "schedule_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            ToolSectionHeader(title = stringResource(R.string.mortgage_schedule_title))
                        }
                        Text(
                            text = stringResource(R.string.mortgage_schedule_show_all),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Switch(checked = state.showAllRows, onCheckedChange = onShowAllRowsChanged)
                    }
                }
                if (!state.showAllRows && result.rows.size > MortgageLimits.SCHEDULE_PREVIEW_ROWS) {
                    item(key = "schedule_hint") {
                        Text(
                            text = stringResource(R.string.mortgage_schedule_preview_hint, MortgageLimits.SCHEDULE_PREVIEW_ROWS),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val visibleRows = if (state.showAllRows) result.rows else result.rows.take(MortgageLimits.SCHEDULE_PREVIEW_ROWS)
                visibleRows.forEach { row ->
                    item(key = "schedule_" + row.period) {
                        ToolListItem(
                            title = stringResource(R.string.mortgage_schedule_row, row.period),
                            subtitleMaxLines = Int.MAX_VALUE,
                            dividerIndent = 0.dp,
                            subtitle = stringResource(
                                R.string.mortgage_schedule_detail,
                                formatAmount(row.payment, 2),
                                formatAmount(row.principalPart, 2),
                                formatAmount(row.interestPart, 2),
                                formatAmount(row.remaining, 2),
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
private fun MortgageMethod.labelResource(): Int = when (this) {
    MortgageMethod.EQUAL_INSTALLMENT -> R.string.mortgage_method_equal_installment
    MortgageMethod.EQUAL_PRINCIPAL -> R.string.mortgage_method_equal_principal
}

@StringRes
private fun errorMessage(error: MortgageError): Int = when (error) {
    MortgageError.INVALID -> R.string.mortgage_error_invalid
    MortgageError.RANGE -> R.string.mortgage_error_range
    MortgageError.AMOUNT_LIMIT -> R.string.mortgage_error_limit
    MortgageError.INPUT_TOO_LONG -> R.string.numeric_error_input_too_long
}

@Preview(showBackground = true, name = "Mortgage Screen Preview")
@Composable
private fun MortgageScreenPreview() {
    ToolboxTheme {
        MortgageScreen(
            state = MortgageUiState(amountText = "100", rateText = "3.1", yearsText = "30"),
            onAmountChanged = {},
            onRateChanged = {},
            onYearsChanged = {},
            onMethodSelected = {},
            onShowAllRowsChanged = {},
            onClear = {},
        )
    }
}
