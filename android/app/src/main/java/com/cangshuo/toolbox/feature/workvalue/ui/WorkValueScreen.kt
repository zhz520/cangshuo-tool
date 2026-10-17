package com.cangshuo.toolbox.feature.workvalue.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.formatAmount
import com.cangshuo.toolbox.core.ui.SystemReportActions
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolResultRow
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.workvalue.domain.WorkPreset
import com.cangshuo.toolbox.feature.workvalue.domain.WorkValueError
import com.cangshuo.toolbox.feature.workvalue.domain.WorkValueResult

@Composable
fun WorkValueScreen(
    state: WorkValueUiState,
    onValueChange: (WorkValueField, String) -> Unit,
    onPreset: (WorkPreset) -> Unit,
    onClear: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "result") {
            WorkValueResultPanel(state.result)
        }
        state.error?.let { error ->
            item(key = "error") { WorkValueErrorBanner(error) }
        }
        item(key = "presets") {
            WorkPresetRow(onPreset)
        }
        item(key = "pay") {
            ToolGroupCard(title = stringResource(R.string.work_value_pay_title)) {
                WorkValueField.SALARY.InputField(state, onValueChange)
                WorkValueField.PAID_MONTHS.InputField(state, onValueChange)
                WorkValueField.ANNUAL_BONUS.InputField(state, onValueChange)
                WorkValueField.MONTHLY_BENEFITS.InputField(state, onValueChange)
                WorkValueField.MONTHLY_JOB_COST.InputField(state, onValueChange)
            }
        }
        item(key = "time") {
            ToolGroupCard(title = stringResource(R.string.work_value_time_title)) {
                WorkValueField.WORK_DAYS_PER_WEEK.InputField(state, onValueChange)
                WorkValueField.OFFICE_DAYS_PER_WEEK.InputField(state, onValueChange)
                WorkValueField.DAILY_ATTENDANCE_HOURS.InputField(state, onValueChange)
                WorkValueField.UNPAID_BREAK_HOURS.InputField(state, onValueChange)
                WorkValueField.COMMUTE_HOURS_ONE_WAY.InputField(state, onValueChange)
                WorkValueField.PAID_NON_WORKING_DAYS.InputField(state, onValueChange)
            }
        }
        item(key = "clear") {
            ToolboxTextButton(onClick = onClear) {
                Text(stringResource(R.string.work_value_clear))
            }
        }
        state.result?.let { result ->
            item(key = "result_detail") { WorkValueResultRows(result) }
            item(key = "report") {
                val context = LocalContext.current
                val report = buildWorkValueReport(context, result)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolSectionHeader(stringResource(R.string.work_value_report))
                    SystemReportActions(report, loading = false, onRefresh = {}, showRefresh = false)
                }
            }
        }
        item(key = "note") {
            Text(
                text = stringResource(R.string.work_value_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkPresetRow(onPreset: (WorkPreset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolSectionHeader(stringResource(R.string.work_value_presets))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkPreset.entries.forEach { preset ->
                AssistChip(
                    onClick = { onPreset(preset) },
                    label = { Text(stringResource(preset.labelResource())) },
                )
            }
        }
    }
}

@Composable
private fun WorkValueField.InputField(
    state: WorkValueUiState,
    onValueChange: (WorkValueField, String) -> Unit,
) {
    ToolboxTextField(
        value = state.values[this].orEmpty(),
        onValueChange = { onValueChange(this, it) },
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(labelResource()),
        placeholder = if (this == WorkValueField.SALARY) stringResource(R.string.work_value_salary_hint) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun WorkValueResultPanel(result: WorkValueResult?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ToolSectionHeader(stringResource(R.string.work_value_result_title))
        if (result == null) {
            Text(
                text = stringResource(R.string.work_value_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        Text(
            text = stringResource(
                R.string.work_value_per_hour_value,
                formatAmount(result.netRealHourly, 2),
            ),
            style = MaterialTheme.typography.displaySmall,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = stringResource(
                R.string.work_value_nominal_value,
                formatAmount(result.nominalHourly, 2),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WorkValueResultRows(result: WorkValueResult) {
    val unit = stringResource(R.string.work_value_currency)
    ToolGroupCard(title = stringResource(R.string.work_value_metrics_title)) {
        ToolResultRow(
            stringResource(R.string.work_value_annual_cash),
            formatAmount(result.annualCash, 2) + " " + unit,
        )
        ToolResultRow(
            stringResource(R.string.work_value_annual_cost),
            formatAmount(result.annualJobCost, 2) + " " + unit,
        )
        ToolResultRow(
            stringResource(R.string.work_value_annual_net),
            formatAmount(result.annualNetCash, 2) + " " + unit,
        )
        ToolResultRow(
            stringResource(R.string.work_value_paid_days),
            stringResource(R.string.work_value_days_value, formatAmount(result.paidWorkDays, 1)),
        )
        ToolResultRow(
            stringResource(R.string.work_value_work_hours),
            stringResource(R.string.work_value_hours_value, formatAmount(result.workHoursPerYear, 1)),
        )
        ToolResultRow(
            stringResource(R.string.work_value_commute_hours),
            stringResource(R.string.work_value_hours_value, formatAmount(result.commuteHoursPerYear, 1)),
        )
        ToolResultRow(
            stringResource(R.string.work_value_commute_share),
            stringResource(R.string.work_value_percent_value, formatAmount(result.commuteSharePercent, 1)),
        )
        ToolResultRow(
            stringResource(R.string.work_value_minutes_per_100),
            stringResource(R.string.work_value_minutes_value, formatAmount(result.minutesPerHundredCash, 1)),
        )
    }
}

@Composable
private fun WorkValueErrorBanner(error: WorkValueError) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = stringResource(
                when (error) {
                    WorkValueError.INVALID -> R.string.work_value_error_invalid
                    WorkValueError.OUT_OF_RANGE -> R.string.work_value_error_range
                    WorkValueError.NO_WORK_TIME -> R.string.work_value_error_no_time
                },
            ),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun buildWorkValueReport(context: android.content.Context, result: WorkValueResult): String {
    val locale = context.resources.configuration.locales[0]
    val line = context.getString(R.string.work_value_report_line)
    val unit = context.getString(R.string.work_value_currency)
    return buildString {
        appendLine(context.getString(R.string.work_value_report_title))
        appendLine(String.format(locale, line, context.getString(R.string.work_value_real_hourly),
            formatAmount(result.netRealHourly, 2) + " " + unit))
        appendLine(String.format(locale, line, context.getString(R.string.work_value_nominal_hourly),
            formatAmount(result.nominalHourly, 2) + " " + unit))
        appendLine(String.format(locale, line, context.getString(R.string.work_value_annual_cash),
            formatAmount(result.annualCash, 2) + " " + unit))
        appendLine(String.format(locale, line, context.getString(R.string.work_value_annual_net),
            formatAmount(result.annualNetCash, 2) + " " + unit))
        appendLine(String.format(locale, line, context.getString(R.string.work_value_paid_days),
            formatAmount(result.paidWorkDays, 1)))
        appendLine(String.format(locale, line, context.getString(R.string.work_value_commute_share),
            String.format(locale, "%.1f%%", result.commuteSharePercent)))
        append(context.getString(R.string.work_value_note))
    }
}

@StringRes
private fun WorkValueField.labelResource(): Int = when (this) {
    WorkValueField.SALARY -> R.string.work_value_salary
    WorkValueField.PAID_MONTHS -> R.string.work_value_paid_months
    WorkValueField.ANNUAL_BONUS -> R.string.work_value_annual_bonus
    WorkValueField.MONTHLY_BENEFITS -> R.string.work_value_monthly_benefits
    WorkValueField.MONTHLY_JOB_COST -> R.string.work_value_monthly_cost
    WorkValueField.WORK_DAYS_PER_WEEK -> R.string.work_value_work_days
    WorkValueField.OFFICE_DAYS_PER_WEEK -> R.string.work_value_office_days
    WorkValueField.DAILY_ATTENDANCE_HOURS -> R.string.work_value_daily_hours
    WorkValueField.UNPAID_BREAK_HOURS -> R.string.work_value_break_hours
    WorkValueField.COMMUTE_HOURS_ONE_WAY -> R.string.work_value_commute
    WorkValueField.PAID_NON_WORKING_DAYS -> R.string.work_value_nonworking_days
}

@StringRes
private fun WorkPreset.labelResource(): Int = when (this) {
    WorkPreset.STANDARD -> R.string.work_value_preset_standard
    WorkPreset.SINGLE_DAY_OFF -> R.string.work_value_preset_single
    WorkPreset.BIG_SMALL_WEEK -> R.string.work_value_preset_big_small
    WorkPreset.REMOTE -> R.string.work_value_preset_remote
}
