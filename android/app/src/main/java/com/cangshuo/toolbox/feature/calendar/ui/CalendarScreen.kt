package com.cangshuo.toolbox.feature.calendar.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.SystemReportActions
import com.cangshuo.toolbox.core.ui.ToolResultRow
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import com.cangshuo.toolbox.core.ui.ToolboxBadge
import com.cangshuo.toolbox.core.ui.ToolboxDivider
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.feature.calendar.domain.CalendarDayDetails
import com.cangshuo.toolbox.feature.calendar.domain.CalendarDaySummary
import com.cangshuo.toolbox.feature.calendar.domain.CalendarMonth
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onRefresh: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE", locale) }
    val monthFormatter = remember(locale) {
        DateTimeFormatter.ofPattern(if (locale.language == "zh") "yyyy年M月" else "MMMM yyyy", locale)
    }
    val weekdays = stringArrayResource(R.array.calendar_weekdays)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.error?.let { error ->
            item(key = "error") { CalendarErrorBanner(error, onRefresh) }
        }
        if (state.month != null && state.visibleMonth != null && state.details != null) {
            item(key = "month_header") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToolboxTextButton(onClick = onPreviousMonth) {
                            Text(stringResource(R.string.calendar_previous_month))
                        }
                        Text(
                            text = monthFormatter.format(state.visibleMonth),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ToolboxTextButton(onClick = onNextMonth) {
                            Text(stringResource(R.string.calendar_next_month))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.calendar_month_hint),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ToolboxTextButton(onClick = onToday) {
                            Text(stringResource(R.string.calendar_today))
                        }
                    }
                }
            }
            item(key = "weekdays") {
                Row(Modifier.fillMaxWidth()) {
                    weekdays.forEach { label ->
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            item(key = "month_grid") {
                CalendarGrid(
                    month = state.month,
                    selected = state.selectedDate,
                    today = state.today,
                    onSelect = onSelectDate,
                )
            }
            item(key = "selected_detail") {
                DayDetail(state.details, state.today, dateFormatter)
            }
            item(key = "report") {
                val context = LocalContext.current
                val report = remember(state.details, locale) {
                    state.details?.let { detail -> buildCalendarReport(context, detail, dateFormatter) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolSectionHeader(stringResource(R.string.calendar_report))
                    SystemReportActions(report, loading = false, onRefresh = {}, showRefresh = false)
                }
            }
            item(key = "note") {
                Text(
                    text = stringResource(R.string.calendar_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun CalendarGrid(
    month: CalendarMonth,
    selected: LocalDate?,
    today: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        month.days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { day ->
                    CalendarDayCell(
                        day = day,
                        selected = day.date == selected,
                        today = day.date == today,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(day.date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDaySummary,
    selected: Boolean,
    today: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        today -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    Column(
        modifier = modifier
            .height(54.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (day.inCurrentMonth) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = day.lunarLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (day.inCurrentMonth) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (day.festivals.isNotEmpty()) {
            Box(
                Modifier
                    .padding(top = 1.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayDetail(
    details: CalendarDayDetails,
    today: LocalDate?,
    dateFormatter: DateTimeFormatter,
) {
    val context = LocalContext.current
    val none = stringResource(R.string.calendar_none)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolboxBadge(stringResource(R.string.calendar_selected_title), emphasized = true)
            if (details.date == today) ToolboxBadge(stringResource(R.string.calendar_today_badge))
        }
        Text(
            text = dateFormatter.format(details.date),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(
                R.string.calendar_lunar_value,
                details.lunarMonthChinese,
                details.lunarDayChinese,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ToolResultRow(
            stringResource(R.string.calendar_ganzhi_label),
            stringResource(
                R.string.calendar_ganzhi_value,
                details.ganZhiYear,
                details.ganZhiMonth,
                details.ganZhiDay,
            ),
        )
        ToolResultRow(
            stringResource(R.string.calendar_zodiac_label),
            zodiacLabel(context, details.zodiac),
        )
        val highlights = buildList {
            details.solarTerm?.let { add(it) }
            addAll(details.festivals)
        }.distinct()
        if (highlights.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                highlights.forEach { ToolboxBadge(it) }
            }
        }
        ToolboxDivider(startIndent = 0.dp)
        AlmanacRow(stringResource(R.string.calendar_yi_label), details.suitable, none)
        AlmanacRow(stringResource(R.string.calendar_ji_label), details.avoid, none)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlmanacRow(label: String, values: List<String>, empty: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (values.isEmpty()) {
            Text(
                text = empty,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                values.forEach { ToolboxBadge(it) }
            }
        }
    }
}

@Composable
private fun CalendarErrorBanner(error: CalendarError, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(
                    when (error) {
                        CalendarError.CALCULATION_FAILED -> R.string.calendar_error
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            ToolboxTextButton(onClick = onRetry) {
                Text(stringResource(R.string.calendar_retry))
            }
        }
    }
}

private fun zodiacLabel(context: Context, value: String): String {
    val index = ZODIAC.indexOf(value)
    if (index < 0) return value
    return context.resources.getStringArray(R.array.calendar_zodiac_names)[index]
}

private fun buildCalendarReport(
    context: Context,
    details: CalendarDayDetails,
    dateFormatter: DateTimeFormatter,
): String {
    fun line(value: String): String = context.getString(R.string.calendar_report_line, value)
    return buildString {
        appendLine(context.getString(R.string.calendar_report_title))
        appendLine(line(dateFormatter.format(details.date)))
        appendLine(line(context.getString(
            R.string.calendar_lunar_value,
            details.lunarMonthChinese,
            details.lunarDayChinese,
        )))
        appendLine(line(context.getString(
            R.string.calendar_ganzhi_value,
            details.ganZhiYear,
            details.ganZhiMonth,
            details.ganZhiDay,
        )))
        appendLine(line(zodiacLabel(context, details.zodiac)))
        details.solarTerm?.let { appendLine(line(it)) }
        if (details.festivals.isNotEmpty()) appendLine(line(details.festivals.joinToString(" · ")))
        appendLine(context.getString(
            R.string.calendar_almanac_line,
            context.getString(R.string.calendar_yi_label),
            details.suitable.joinToString(" · "),
        ))
        appendLine(context.getString(
            R.string.calendar_almanac_line,
            context.getString(R.string.calendar_ji_label),
            details.avoid.joinToString(" · "),
        ))
        append(context.getString(R.string.calendar_note))
    }
}

private val ZODIAC = listOf("鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪")
