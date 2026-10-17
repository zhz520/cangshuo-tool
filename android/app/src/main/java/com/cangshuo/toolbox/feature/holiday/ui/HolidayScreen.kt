package com.cangshuo.toolbox.feature.holiday.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.SystemReportActions
import com.cangshuo.toolbox.core.ui.ToolResultRow
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import com.cangshuo.toolbox.core.ui.ToolboxBadge
import com.cangshuo.toolbox.core.ui.ToolboxDivider
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.feature.holiday.domain.HolidayCountdownItem
import com.cangshuo.toolbox.feature.holiday.domain.HolidayFilter
import com.cangshuo.toolbox.feature.holiday.domain.HolidayId
import com.cangshuo.toolbox.feature.holiday.domain.HolidayKind
import com.cangshuo.toolbox.feature.holiday.domain.LunarDateInfo
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HolidayScreen(
    state: HolidayUiState,
    onSelect: (HolidayId) -> Unit,
    onFilter: (HolidayFilter) -> Unit,
    onRefresh: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE", locale) }
    val selected = state.selectedUpcoming.firstOrNull()
    val report = remember(state.items, state.selectedUpcoming, locale) {
        buildReport(context, state.items, locale, dateFormatter)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.error?.let { error ->
            item(key = "error") { HolidayErrorBanner(error, onRefresh) }
        }
        if (selected == null && state.error == null) {
            item(key = "empty") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = stringResource(R.string.holiday_empty),
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (selected != null) {
            item(key = "hero") {
                HolidayHero(selected, dateFormatter)
            }
            item(key = "future_occurrences") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ToolSectionHeader(stringResource(R.string.holiday_future_title))
                    state.selectedUpcoming.forEach { item ->
                        ToolResultRow(
                            label = dateFormatter.format(item.occurrence.date),
                            value = holidayBasisText(context, item),
                        )
                    }
                }
            }
        }
        item(key = "filters") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HolidayFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { onFilter(filter) },
                            label = { Text(stringResource(filter.labelResource())) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.holiday_live_caption),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ToolboxTextButton(onClick = onRefresh) {
                        Text(stringResource(R.string.holiday_refresh))
                    }
                }
            }
        }
        item(key = "list_header") {
            ToolSectionHeader(
                title = stringResource(R.string.holiday_upcoming_title),
                trailing = {
                    Text(
                        text = pluralStringResource(
                            R.plurals.holiday_upcoming_count,
                            state.items.size,
                            state.items.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
        if (state.items.isEmpty() && selected != null) {
            item(key = "filter_empty") {
                Text(
                    text = stringResource(R.string.holiday_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            itemsIndexed(state.items, key = { _, item -> item.occurrence.id.name }) { index, item ->
                Column(Modifier.fillMaxWidth()) {
                    if (index > 0) ToolboxDivider(startIndent = 0.dp)
                    HolidayRow(
                        item = item,
                        selected = state.selected == item.occurrence.id,
                        dateFormatter = dateFormatter,
                        onClick = { onSelect(item.occurrence.id) },
                    )
                }
            }
        }
        item(key = "report") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolSectionHeader(stringResource(R.string.holiday_report))
                SystemReportActions(report, loading = false, onRefresh = {}, showRefresh = false)
            }
        }
        item(key = "note") {
            Text(
                text = stringResource(R.string.holiday_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun HolidayHero(
    item: HolidayCountdownItem,
    dateFormatter: DateTimeFormatter,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolboxBadge(stringResource(R.string.holiday_next_title), emphasized = true)
            ToolboxBadge(stringResource(item.occurrence.id.kind.labelResource()))
        }
        Text(
            text = stringResource(item.occurrence.id.labelResource()),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = dateFormatter.format(item.occurrence.date),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = lunarDateText(context, item.occurrence.lunarDate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CountdownText(item.timing.isToday, item.timing.daysUntil, item.timing.secondsUntil)
    }
}

@Composable
private fun CountdownText(isToday: Boolean, daysUntil: Long, secondsUntil: Long) {
    val days = secondsUntil / 86_400
    val hours = secondsUntil % 86_400 / 3_600
    val minutes = secondsUntil % 3_600 / 60
    val seconds = secondsUntil % 60
    val text = if (isToday) {
        stringResource(R.string.holiday_today_countdown, hours, minutes, seconds)
    } else if (daysUntil == 1L) {
        stringResource(R.string.holiday_tomorrow_countdown, hours, minutes, seconds)
    } else {
        pluralStringResource(
            R.plurals.holiday_countdown,
            days.toInt(),
            days,
            hours,
            minutes,
            seconds,
        )
    }
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.headlineMedium,
        fontFamily = FontFamily.Monospace,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun HolidayRow(
    item: HolidayCountdownItem,
    selected: Boolean,
    dateFormatter: DateTimeFormatter,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.background,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 10.dp else 0.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(item.occurrence.id.labelResource()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ToolboxBadge(relativeDayText(context, item))
        }
        Text(
            text = dateFormatter.format(item.occurrence.date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = holidayBasisText(context, item),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HolidayErrorBanner(error: HolidayError, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(
                    when (error) {
                        HolidayError.CALCULATION_FAILED -> R.string.holiday_error
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            ToolboxTextButton(onClick = onRetry) {
                Text(stringResource(R.string.holiday_retry))
            }
        }
    }
}

private fun holidayBasisText(context: Context, item: HolidayCountdownItem): String {
    val kind = context.getString(item.occurrence.id.kind.labelResource())
    val lunar = lunarDateText(context, item.occurrence.lunarDate)
    return context.getString(R.string.holiday_date_basis, kind, lunar)
}

private fun lunarDateText(context: Context, date: LunarDateInfo): String = context.getString(
    if (date.isLeap) R.string.holiday_lunar_date_leap else R.string.holiday_lunar_date,
    date.month,
    date.day,
)

private fun relativeDayText(context: Context, item: HolidayCountdownItem): String = when {
    item.timing.isToday -> context.getString(R.string.holiday_today)
    item.timing.daysUntil == 1L -> context.getString(R.string.holiday_tomorrow)
    else -> context.resources.getQuantityString(
        R.plurals.holiday_days_after,
        item.timing.daysUntil.toInt(),
        item.timing.daysUntil,
    )
}

private fun buildReport(
    context: Context,
    items: List<HolidayCountdownItem>,
    locale: Locale,
    dateFormatter: DateTimeFormatter,
): String {
    val title = context.getString(R.string.holiday_report_title)
    val line = context.getString(R.string.holiday_report_line)
    return buildString {
        appendLine(title)
        items.forEach { item ->
            appendLine(
                String.format(
                    locale,
                    line,
                    context.getString(item.occurrence.id.labelResource()),
                    dateFormatter.format(item.occurrence.date),
                    holidayBasisText(context, item),
                    relativeDayText(context, item),
                ),
            )
        }
        append(context.getString(R.string.holiday_note))
    }
}

@StringRes
private fun HolidayFilter.labelResource(): Int = when (this) {
    HolidayFilter.ALL -> R.string.holiday_filter_all
    HolidayFilter.TRADITIONAL -> R.string.holiday_filter_traditional
    HolidayFilter.GREGORIAN -> R.string.holiday_filter_gregorian
}

@StringRes
private fun HolidayKind.labelResource(): Int = when (this) {
    HolidayKind.SOLAR -> R.string.holiday_kind_solar
    HolidayKind.LUNAR -> R.string.holiday_kind_lunar
    HolidayKind.SOLAR_TERM -> R.string.holiday_kind_term
}

@StringRes
private fun HolidayId.labelResource(): Int = when (this) {
    HolidayId.SPRING_FESTIVAL -> R.string.holiday_spring_festival
    HolidayId.LANTERN_FESTIVAL -> R.string.holiday_lantern_festival
    HolidayId.DRAGON_HEAD -> R.string.holiday_dragon_head
    HolidayId.QINGMING -> R.string.holiday_qingming
    HolidayId.DRAGON_BOAT_FESTIVAL -> R.string.holiday_dragon_boat_festival
    HolidayId.QIXI -> R.string.holiday_qixi
    HolidayId.GHOST_FESTIVAL -> R.string.holiday_ghost_festival
    HolidayId.MID_AUTUMN_FESTIVAL -> R.string.holiday_mid_autumn_festival
    HolidayId.DOUBLE_NINTH -> R.string.holiday_double_ninth
    HolidayId.LABA_FESTIVAL -> R.string.holiday_laba_festival
    HolidayId.LITTLE_NEW_YEAR -> R.string.holiday_little_new_year
    HolidayId.NEW_YEAR -> R.string.holiday_new_year
    HolidayId.VALENTINES_DAY -> R.string.holiday_valentines_day
    HolidayId.WOMENS_DAY -> R.string.holiday_womens_day
    HolidayId.LABOR_DAY -> R.string.holiday_labor_day
    HolidayId.CHILDRENS_DAY -> R.string.holiday_childrens_day
    HolidayId.TEACHERS_DAY -> R.string.holiday_teachers_day
    HolidayId.NATIONAL_DAY -> R.string.holiday_national_day
    HolidayId.HALLOWEEN -> R.string.holiday_halloween
    HolidayId.SINGLES_DAY -> R.string.holiday_singles_day
    HolidayId.CHRISTMAS_EVE -> R.string.holiday_christmas_eve
    HolidayId.CHRISTMAS -> R.string.holiday_christmas
}
