package com.cangshuo.toolbox.feature.datecalc.ui

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
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolResultRow
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.datecalc.domain.DateCalcError
import com.cangshuo.toolbox.feature.datecalc.domain.DateDiffOutcome
import com.cangshuo.toolbox.feature.datecalc.domain.DateDirection
import com.cangshuo.toolbox.feature.datecalc.domain.DateShiftOutcome
import com.cangshuo.toolbox.feature.datecalc.domain.DateUnit
import com.cangshuo.toolbox.ui.theme.ToolboxTheme
import java.time.DayOfWeek

@Composable
fun DateCalcScreen(
    state: DateCalcUiState,
    onFirstChanged: (String) -> Unit,
    onSecondChanged: (String) -> Unit,
    onBaseChanged: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onUnitSelected: (DateUnit) -> Unit,
    onDirectionSelected: (DateDirection) -> Unit,
    onFillFirstToday: () -> Unit,
    onFillSecondToday: () -> Unit,
    onFillBaseToday: () -> Unit,
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
        item(key = "difference_input") {
            ToolGroupCard(title = stringResource(R.string.date_diff_title)) {
                ToolboxTextField(
                    value = state.firstText,
                    onValueChange = onFirstChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.date_diff_first_label),
                    placeholder = stringResource(R.string.date_diff_date_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                ToolboxTextButton(onClick = onFillFirstToday) {
                    Text(stringResource(R.string.date_btn_today))
                }
                ToolboxTextField(
                    value = state.secondText,
                    onValueChange = onSecondChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.date_diff_second_label),
                    placeholder = stringResource(R.string.date_diff_date_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                ToolboxTextButton(onClick = onFillSecondToday) {
                    Text(stringResource(R.string.date_btn_today))
                }
                if (state.firstText.isNotEmpty() || state.secondText.isNotEmpty() ||
                    state.baseText.isNotEmpty() || state.amountText.isNotEmpty()
                ) {
                    ToolboxTextButton(onClick = onClear) { Text(stringResource(R.string.date_btn_clear)) }
                }
            }
        }

        when (val outcome = state.diffOutcome) {
            null -> item(key = "diff_placeholder") {
                PlaceholderSurface(stringResource(R.string.date_diff_empty))
            }

            is DateDiffOutcome.Failure -> item(key = "diff_error") {
                ErrorSurface(outcome.error)
            }

            is DateDiffOutcome.Success -> item(key = "diff_result") {
                val result = outcome.result
                val summary = stringResource(
                    R.string.date_copy_diff,
                    state.firstText.trim(),
                    state.secondText.trim(),
                    result.totalDays,
                    result.weeks,
                    result.remainderDays,
                    result.businessDays,
                )
                ToolGroupCard(title = stringResource(R.string.date_diff_result_title)) {
                    Text(
                        text = result.totalDays.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.date_diff_total_days),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ToolResultRow(
                        stringResource(R.string.date_diff_weeks),
                        stringResource(R.string.date_diff_weeks_format, result.weeks, result.remainderDays),
                    )
                    ToolResultRow(
                        stringResource(R.string.date_diff_period),
                        periodText(result.years, result.months, result.days),
                    )
                    ToolResultRow(stringResource(R.string.date_diff_business), result.businessDays.toString())
                    ToolResultRow(stringResource(R.string.date_diff_weekend), result.weekendDays.toString())
                    Text(
                        text = stringResource(R.string.date_diff_business_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ToolboxTextButton(
                        onClick = { clipboard.setText(AnnotatedString(summary)) },
                    ) { Text(stringResource(R.string.date_btn_copy)) }
                }
            }
        }

        item(key = "shift_input") {
            ToolGroupCard(title = stringResource(R.string.date_shift_title)) {
                ToolboxTextField(
                    value = state.baseText,
                    onValueChange = onBaseChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.date_shift_base_label),
                    placeholder = stringResource(R.string.date_diff_date_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                ToolboxTextButton(onClick = onFillBaseToday) {
                    Text(stringResource(R.string.date_btn_today))
                }
                ToolboxTextField(
                    value = state.amountText,
                    onValueChange = onAmountChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.date_shift_amount_label),
                    placeholder = stringResource(R.string.date_shift_amount_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    text = stringResource(R.string.date_shift_unit_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = state.unit == unit,
                            onClick = { onUnitSelected(unit) },
                            label = { Text(stringResource(unit.labelResource())) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.date_shift_direction_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateDirection.entries.forEach { direction ->
                        FilterChip(
                            selected = state.direction == direction,
                            onClick = { onDirectionSelected(direction) },
                            label = { Text(stringResource(direction.labelResource())) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.date_shift_month_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (val outcome = state.shiftOutcome) {
            null -> item(key = "shift_placeholder") {
                PlaceholderSurface(stringResource(R.string.date_shift_empty))
            }

            is DateShiftOutcome.Failure -> item(key = "shift_error") {
                ErrorSurface(outcome.error)
            }

            is DateShiftOutcome.Success -> item(key = "shift_result") {
                val result = outcome.result
                val summary = stringResource(
                    R.string.date_copy_shift,
                    state.baseText.trim(),
                    stringResource(state.direction.labelResource()),
                    state.amountText.trim().toLongOrNull() ?: 0L,
                    stringResource(state.unit.labelResource()),
                    result.result.toString(),
                )
                ToolGroupCard(title = stringResource(R.string.date_shift_result_title)) {
                    Text(
                        text = stringResource(
                            R.string.date_shift_result_format,
                            result.result.toString(),
                            stringResource(result.result.dayOfWeek.labelResource()),
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ToolResultRow(
                        stringResource(R.string.date_shift_days_label),
                        (if (result.signedDays >= 0) "+" else "") + result.signedDays,
                    )
                    ToolboxTextButton(
                        onClick = { clipboard.setText(AnnotatedString(summary)) },
                    ) { Text(stringResource(R.string.date_btn_copy)) }
                }
            }
        }

        item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun periodText(years: Long, months: Long, days: Long): String = when {
    years > 0 && months == 0L && days == 0L -> stringResource(R.string.date_diff_period_y, years)
    years > 0 -> stringResource(R.string.date_diff_period_ymd, years, months, days)
    months > 0 && days == 0L -> stringResource(R.string.date_diff_period_m, months)
    months > 0 -> stringResource(R.string.date_diff_period_md, months, days)
    else -> stringResource(R.string.date_diff_period_d, days)
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
private fun ErrorSurface(error: DateCalcError) {
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
private fun DateCalcError.messageResource(): Int = when (this) {
    DateCalcError.INVALID_DATE -> R.string.date_error_invalid_date
    DateCalcError.OUT_OF_RANGE -> R.string.date_error_range
    DateCalcError.INVALID_AMOUNT -> R.string.date_error_invalid_amount
    DateCalcError.AMOUNT_OUT_OF_RANGE -> R.string.date_error_amount_range
    DateCalcError.RESULT_OUT_OF_RANGE -> R.string.date_error_result_range
    DateCalcError.INPUT_TOO_LONG -> R.string.numeric_error_input_too_long
}

@StringRes
private fun DateUnit.labelResource(): Int = when (this) {
    DateUnit.DAYS -> R.string.date_shift_unit_days
    DateUnit.WEEKS -> R.string.date_shift_unit_weeks
    DateUnit.MONTHS -> R.string.date_shift_unit_months
    DateUnit.YEARS -> R.string.date_shift_unit_years
}

@StringRes
private fun DateDirection.labelResource(): Int = when (this) {
    DateDirection.AFTER -> R.string.date_shift_direction_after
    DateDirection.BEFORE -> R.string.date_shift_direction_before
}

@StringRes
private fun DayOfWeek.labelResource(): Int = when (this) {
    DayOfWeek.MONDAY -> R.string.date_weekday_mon
    DayOfWeek.TUESDAY -> R.string.date_weekday_tue
    DayOfWeek.WEDNESDAY -> R.string.date_weekday_wed
    DayOfWeek.THURSDAY -> R.string.date_weekday_thu
    DayOfWeek.FRIDAY -> R.string.date_weekday_fri
    DayOfWeek.SATURDAY -> R.string.date_weekday_sat
    DayOfWeek.SUNDAY -> R.string.date_weekday_sun
}

@Preview
@Composable
private fun DateCalcScreenPreview() {
    ToolboxTheme {
        DateCalcScreen(DateCalcUiState(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}
