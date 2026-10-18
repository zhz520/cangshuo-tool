package com.cangshuo.toolbox.feature.period.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolResultRow
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import com.cangshuo.toolbox.core.ui.ToolboxBadge
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxDivider
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxSwitch
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.period.domain.PeriodCalendarDay
import com.cangshuo.toolbox.feature.period.domain.PeriodCycle
import com.cangshuo.toolbox.feature.period.domain.PeriodError
import com.cangshuo.toolbox.feature.period.domain.PeriodFlow
import com.cangshuo.toolbox.feature.period.domain.PeriodMood
import com.cangshuo.toolbox.feature.period.domain.PeriodPolicy
import com.cangshuo.toolbox.feature.period.domain.PeriodSymptom
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PeriodScreen(
    state: PeriodUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onSelectDay: (Long) -> Unit,
    onOpenEditor: () -> Unit,
    onEditCycle: (Long) -> Unit,
    onDismissEditor: () -> Unit,
    onEditorStart: (Long) -> Unit,
    onEditorEnd: (Long?) -> Unit,
    onFlow: (PeriodFlow) -> Unit,
    onSymptom: (PeriodSymptom) -> Unit,
    onMood: (PeriodMood?) -> Unit,
    onNotes: (String) -> Unit,
    onSaveEditor: () -> Unit,
    onDeleteEditor: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissSettings: () -> Unit,
    onCycleDays: (String) -> Unit,
    onPeriodDays: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onClearEditorError: () -> Unit,
    onClearSettingsError: () -> Unit,
    onMessageConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { periodMessageLabel(it) }
    LaunchedEffect(state.messageRevision) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            onMessageConsumed()
        }
    }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val monthFormatter = remember(locale) {
        DateTimeFormatter.ofPattern(if (locale.language == "zh") "yyyy年M月" else "MMMM yyyy", locale)
    }
    val weekdays = stringArrayResource(R.array.period_weekdays).toList()
    val weekdayDescriptions = stringArrayResource(R.array.period_weekdays_full).toList()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = ToolboxDimens.contentMaxWidth).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = ToolboxDimens.pageGutter,
                top = ToolboxDimens.pageTop,
                end = ToolboxDimens.pageGutter,
                bottom = ToolboxDimens.pageBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("overview") {
                PeriodOverview(
                    state = state,
                    dateFormatter = dateFormatter,
                    onOpenEditor = onOpenEditor,
                    onOpenSettings = onOpenSettings,
                )
            }
            if (state.loading && state.cycles.isEmpty()) {
                item("loading") { ToolboxLoadingState(stringResource(R.string.period_loading)) }
            } else {
                item("month_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onPreviousMonth) {
                                Icon(
                                    painterResource(R.drawable.ic_back),
                                    contentDescription = stringResource(R.string.period_previous_month),
                                )
                            }
                            Text(
                                monthFormatter.format(state.visibleMonth),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(onClick = onNextMonth) {
                                Icon(
                                    painterResource(R.drawable.ic_back),
                                    contentDescription = stringResource(R.string.period_next_month),
                                    modifier = Modifier.rotate(180f),
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.period_calendar_hint),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ToolboxTextButton(onClick = onToday) {
                                Text(stringResource(R.string.period_today))
                            }
                        }
                    }
                }
                item("calendar") {
                    PeriodCalendar(
                        days = state.calendarDays,
                        weekdays = weekdays,
                        weekdayDescriptions = weekdayDescriptions,
                        dateFormatter = dateFormatter,
                        onSelect = onSelectDay,
                    )
                }
                item("legend") { PeriodLegend() }
                item("selected") {
                    SelectedDayDetail(
                        state = state,
                        dateFormatter = dateFormatter,
                        onOpenEditor = onOpenEditor,
                    )
                }
                item("insights") {
                    PeriodInsightRows(state = state, dateFormatter = dateFormatter)
                }
                item("history_header") {
                    ToolSectionHeader(
                        stringResource(R.string.period_history),
                        trailing = {
                            if (state.cycles.isNotEmpty()) {
                                Text(
                                    pluralStringResource(R.plurals.period_record_count, state.cycles.size, state.cycles.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
                if (state.cycles.isEmpty()) {
                    item("empty") {
                        Text(
                            stringResource(R.string.period_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.cycles, key = { cycle -> cycle.id }) { cycle ->
                        CycleRow(
                            cycle = cycle,
                            todayEpochDay = state.todayEpochDay,
                            dateFormatter = dateFormatter,
                            onClick = { onEditCycle(cycle.id) },
                        )
                    }
                }
                item("note") {
                    Text(
                        stringResource(R.string.period_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }

    state.editor?.let { editor ->
        PeriodEditorDialog(
            editor = editor,
            saving = state.saving,
            dateFormatter = dateFormatter,
            onDismiss = onDismissEditor,
            onStart = onEditorStart,
            onEnd = onEditorEnd,
            onFlow = onFlow,
            onSymptom = onSymptom,
            onMood = onMood,
            onNotes = onNotes,
            onSave = onSaveEditor,
            onDelete = onDeleteEditor,
            onClearError = onClearEditorError,
        )
    }
    state.settingsEditor?.let { editor ->
        PeriodSettingsDialog(
            editor = editor,
            onDismiss = onDismissSettings,
            onCycleDays = onCycleDays,
            onPeriodDays = onPeriodDays,
            onSave = onSaveSettings,
            onClearError = onClearSettingsError,
        )
    }
}

@Composable
private fun PeriodOverview(
    state: PeriodUiState,
    dateFormatter: DateTimeFormatter,
    onOpenEditor: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val today = LocalDate.ofEpochDay(state.todayEpochDay)
    val current = state.cycles.firstOrNull { cycle ->
        val end = cycle.endEpochDay ?: state.todayEpochDay.coerceAtLeast(cycle.startEpochDay)
        state.todayEpochDay in cycle.startEpochDay..end
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (current == null) R.string.period_cycle_overview else R.string.period_current_period),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (current != null) ToolboxBadge(stringResource(R.string.period_ongoing), emphasized = true)
            }
            if (current == null && state.cycles.isEmpty()) {
                Text(
                    stringResource(R.string.period_first_record_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val cycleDay = state.insights.currentCycleDay
                if (cycleDay != null) {
                    ToolResultRow(
                        stringResource(R.string.period_cycle_day),
                        stringResource(R.string.period_cycle_day_value, cycleDay),
                    )
                }
                state.insights.nextStartEpochDay?.let { next ->
                    val nextDate = LocalDate.ofEpochDay(next)
                    ToolResultRow(
                        stringResource(R.string.period_next_expected),
                        dateFormatter.format(nextDate),
                    )
                    if (state.insights.lateDays > 0) {
                        Text(
                            pluralStringResource(
                                R.plurals.period_late_days,
                                state.insights.lateDays,
                                state.insights.lateDays,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                ToolResultRow(
                    stringResource(R.string.period_today_label),
                    dateFormatter.format(today),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolboxButton(onClick = onOpenEditor) {
                    Text(stringResource(if (state.selectedCycle == null) R.string.period_add_record else R.string.period_edit_record))
                }
                ToolboxOutlinedButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.period_settings))
                }
            }
        }
    }
}

@Composable
private fun PeriodCalendar(
    days: List<PeriodCalendarDay>,
    weekdays: List<String>,
    weekdayDescriptions: List<String>,
    dateFormatter: DateTimeFormatter,
    onSelect: (Long) -> Unit,
) {
    val gap = 4.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val cellWidth = maxOf(48.dp, (availableWidth - gap * 6) / 7)
        val needsHorizontalScroll = cellWidth * 7 + gap * 6 > availableWidth
        val scrollState = rememberScrollState()
        val density = LocalDensity.current
        val focusDay = days.firstOrNull { it.selected } ?: days.firstOrNull { it.today }
        val focusIndex = focusDay?.let { days.indexOf(it) } ?: -1
        LaunchedEffect(focusDay?.epochDay, cellWidth, availableWidth) {
            if (focusIndex >= 0 && needsHorizontalScroll) {
                withFrameNanos { }
                val cellPx = with(density) { cellWidth.toPx() }
                val gapPx = with(density) { gap.toPx() }
                val viewportPx = with(density) { availableWidth.toPx() }
                val column = focusIndex % 7
                val target = (column * (cellPx + gapPx) + cellPx / 2f - viewportPx / 2f)
                    .roundToInt().coerceAtLeast(0)
                scrollState.scrollTo(target.coerceAtMost(scrollState.maxValue))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (needsHorizontalScroll) {
                Text(
                    stringResource(R.string.period_calendar_swipe),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                Modifier.fillMaxWidth().horizontalScroll(scrollState),
            ) {
                Column(
                    Modifier.width(cellWidth * 7 + gap * 6),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        weekdays.forEachIndexed { index, label ->
                            Text(
                                label,
                                modifier = Modifier.width(cellWidth).semantics {
                                    contentDescription = weekdayDescriptions.getOrElse(index) { label }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    days.chunked(7).forEach { week ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            week.forEach { day ->
                                CalendarDayCell(
                                    day = day,
                                    dateFormatter = dateFormatter,
                                    modifier = Modifier.width(cellWidth),
                                    onClick = { onSelect(day.epochDay) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: PeriodCalendarDay,
    dateFormatter: DateTimeFormatter,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val background = when {
        day.cycleId != null -> MaterialTheme.colorScheme.primaryContainer
        day.predictedPeriod -> MaterialTheme.colorScheme.surfaceContainerHigh
        day.selected -> MaterialTheme.colorScheme.primaryContainer
        day.fertileWindow -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val status = periodDayStatuses(day).joinToString(" · ")
    val date = LocalDate.ofEpochDay(day.epochDay)
    val fullStatus = listOfNotNull(
        status.takeIf { it.isNotBlank() },
        stringResource(R.string.period_today_status).takeIf { day.today },
        stringResource(R.string.period_selected_status).takeIf { day.selected },
        stringResource(R.string.period_outside_month).takeIf { !day.inVisibleMonth },
    ).joinToString(" · ")
    val description = if (fullStatus.isBlank()) {
        stringResource(R.string.period_day_description, dateFormatter.format(date))
    } else {
        stringResource(R.string.period_day_status_description, dateFormatter.format(date), fullStatus)
    }
    Column(
        modifier = modifier
            .heightIn(min = 50.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .then(if (day.selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
            .semantics {
                contentDescription = description
                stateDescription = fullStatus
                this.selected = day.selected
            }
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (day.today || day.periodStart) FontWeight.Bold else FontWeight.Medium,
            color = when {
                day.cycleId != null -> MaterialTheme.colorScheme.onPrimaryContainer
                !day.inVisibleMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (day.predictedPeriod) MarkerDot(MaterialTheme.colorScheme.primary)
            if (day.fertileWindow) MarkerRing(MaterialTheme.colorScheme.tertiary)
            if (day.symptomCount > 0) MarkerBar(MaterialTheme.colorScheme.secondary)
            if (day.hasNotes) MarkerDiamond(MaterialTheme.colorScheme.onSurfaceVariant)
            if (day.today) MarkerSquare(MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun MarkerDot(color: Color) {
    Box(Modifier.size(5.dp).clip(CircleShape).background(color))
}

@Composable
private fun MarkerRing(color: Color) {
    Box(
        Modifier.size(8.dp).clip(CircleShape).border(1.dp, color, CircleShape),
    )
}

@Composable
private fun MarkerBar(color: Color) {
    Box(Modifier.width(9.dp).height(3.dp).clip(MaterialTheme.shapes.small).background(color))
}

@Composable
private fun MarkerSquare(color: Color) {
    Box(Modifier.size(5.dp).clip(MaterialTheme.shapes.extraSmall).background(color))
}

@Composable
private fun MarkerDiamond(color: Color) {
    Box(Modifier.size(7.dp).rotate(45f).clip(MaterialTheme.shapes.extraSmall).background(color))
}

@Composable
private fun periodDayStatuses(day: PeriodCalendarDay): List<String> = buildList {
    if (day.cycleId != null) add(stringResource(if (day.ongoing) R.string.period_legend_recorded_ongoing else R.string.period_legend_recorded))
    if (day.periodStart) add(stringResource(R.string.period_start_day_status))
    if (day.periodEnd) add(stringResource(R.string.period_end_day_status))
    if (day.predictedPeriod) add(stringResource(R.string.period_legend_predicted))
    if (day.fertileWindow) add(stringResource(R.string.period_legend_reference))
    if (day.symptomCount > 0) add(stringResource(R.string.period_legend_symptoms))
    if (day.hasNotes) add(stringResource(R.string.period_legend_notes))
}

@Composable
private fun PeriodLegend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LegendItem(stringResource(R.string.period_legend_recorded)) {
            Box(Modifier.width(10.dp).height(6.dp).clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.primaryContainer))
        }
        LegendItem(stringResource(R.string.period_legend_predicted)) {
            MarkerDot(MaterialTheme.colorScheme.primary)
        }
        LegendItem(stringResource(R.string.period_legend_reference)) {
            MarkerRing(MaterialTheme.colorScheme.tertiary)
        }
        LegendItem(stringResource(R.string.period_legend_symptoms)) {
            MarkerBar(MaterialTheme.colorScheme.secondary)
        }
        LegendItem(stringResource(R.string.period_legend_notes)) {
            MarkerDiamond(MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LegendItem(stringResource(R.string.period_today)) {
            MarkerSquare(MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun LegendItem(label: String, marker: @Composable () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            marker()
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SelectedDayDetail(
    state: PeriodUiState,
    dateFormatter: DateTimeFormatter,
    onOpenEditor: () -> Unit,
) {
    val epochDay = state.selectedEpochDay ?: state.todayEpochDay
    val date = LocalDate.ofEpochDay(epochDay)
    val cycle = state.selectedCycle
    val symptomLabels = cycle?.symptoms?.map { periodSymptomLabel(it) }.orEmpty()
    Surface(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dateFormatter.format(date),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (epochDay == state.todayEpochDay) ToolboxBadge(stringResource(R.string.period_today))
            }
            if (cycle == null) {
                Text(
                    stringResource(R.string.period_no_record),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ToolResultRow(
                    stringResource(R.string.period_flow),
                    periodFlowLabel(cycle.flow),
                )
                if (cycle.symptoms.isNotEmpty()) {
                    ToolResultRow(
                        stringResource(R.string.period_symptoms),
                        symptomLabels.joinToString(" · "),
                    )
                }
                cycle.mood?.let { ToolResultRow(stringResource(R.string.period_mood), periodMoodLabel(it)) }
                if (cycle.notes.isNotBlank()) {
                    Text(stringResource(R.string.period_notes), style = MaterialTheme.typography.labelLarge)
                    Text(cycle.notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
            ToolboxOutlinedButton(onClick = onOpenEditor, enabled = epochDay <= state.todayEpochDay) {
                Text(stringResource(if (cycle == null) R.string.period_add_record else R.string.period_edit_record))
            }
        }
    }
}

@Composable
private fun PeriodInsightRows(state: PeriodUiState, dateFormatter: DateTimeFormatter) {
    val insights = state.insights
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolSectionHeader(stringResource(R.string.period_estimates))
        ToolResultRow(
            stringResource(R.string.period_average_cycle),
            if (insights.cycleSamples > 0) {
                pluralStringResource(R.plurals.period_day_value, insights.averageCycleDays, insights.averageCycleDays)
            } else {
                stringResource(R.string.period_setting_fallback, insights.averageCycleDays)
            },
        )
        ToolResultRow(
            stringResource(R.string.period_average_period),
            if (insights.periodSamples > 0) {
                pluralStringResource(R.plurals.period_day_value, insights.averagePeriodDays, insights.averagePeriodDays)
            } else {
                stringResource(R.string.period_setting_fallback, insights.averagePeriodDays)
            },
        )
        if (insights.fertileStartEpochDay != null && insights.fertileEndEpochDay != null) {
            insights.predictedEndEpochDay?.let { end ->
                ToolResultRow(
                    stringResource(R.string.period_predicted_end),
                    dateFormatter.format(LocalDate.ofEpochDay(end)),
                )
            }
            insights.ovulationEpochDay?.let { ovulation ->
                ToolResultRow(
                    stringResource(R.string.period_ovulation_estimate),
                    dateFormatter.format(LocalDate.ofEpochDay(ovulation)),
                )
            }
            ToolResultRow(
                stringResource(R.string.period_reference_window),
                "${dateFormatter.format(LocalDate.ofEpochDay(insights.fertileStartEpochDay))} – " +
                    dateFormatter.format(LocalDate.ofEpochDay(insights.fertileEndEpochDay)),
            )
        }
        Text(
            stringResource(R.string.period_estimate_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CycleRow(
    cycle: PeriodCycle,
    todayEpochDay: Long,
    dateFormatter: DateTimeFormatter,
    onClick: () -> Unit,
) {
    val start = LocalDate.ofEpochDay(cycle.startEpochDay)
    val end = cycle.endEpochDay?.let(LocalDate::ofEpochDay)
    val symptomLabels = cycle.symptoms.map { periodSymptomLabel(it) }
    val range = if (end == null) {
        stringResource(R.string.period_range_ongoing, dateFormatter.format(start))
    } else {
        stringResource(R.string.period_range, dateFormatter.format(start), dateFormatter.format(end))
    }
    val duration = cycle.durationDays(todayEpochDay)
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(range, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            ToolboxBadge(if (cycle.endEpochDay == null) stringResource(R.string.period_ongoing) else periodFlowLabel(cycle.flow))
        }
        Text(
            pluralStringResource(R.plurals.period_day_value, duration, duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (cycle.symptoms.isNotEmpty()) {
            Text(
                symptomLabels.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun PeriodEditorDialog(
    editor: PeriodEditorState,
    saving: Boolean,
    dateFormatter: DateTimeFormatter,
    onDismiss: () -> Unit,
    onStart: (Long) -> Unit,
    onEnd: (Long?) -> Unit,
    onFlow: (PeriodFlow) -> Unit,
    onSymptom: (PeriodSymptom) -> Unit,
    onMood: (PeriodMood?) -> Unit,
    onNotes: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClearError: () -> Unit,
) {
    val context = LocalContext.current
    var showDelete by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
        Surface(
            modifier = Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxWidth().heightIn(max = 840.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        stringResource(if (editor.cycleId == null) R.string.period_add_title else R.string.period_edit_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    DateSelectorRow(
                        stringResource(R.string.period_start_date), editor.startEpochDay, dateFormatter,
                        enabled = !saving,
                    ) {
                        showDatePicker(context, editor.startEpochDay, LocalDate.now().toEpochDay(), onStart)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.period_ongoing_switch), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.period_ongoing_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ToolboxSwitch(
                            checked = editor.endEpochDay == null,
                            onCheckedChange = { ongoing ->
                                if (ongoing) onEnd(null) else onEnd(editor.startEpochDay)
                            },
                            enabled = !saving,
                        )
                    }
                    if (editor.endEpochDay != null) {
                        DateSelectorRow(
                            stringResource(R.string.period_end_date), editor.endEpochDay, dateFormatter,
                            enabled = !saving,
                        ) {
                            showDatePicker(context, editor.endEpochDay, LocalDate.now().toEpochDay(), onEnd)
                        }
                    }
                    SelectionGroup(
                        title = stringResource(R.string.period_flow),
                        values = PeriodFlow.entries,
                        selected = { it == editor.flow },
                        label = { periodFlowLabel(it) },
                        onClick = onFlow,
                        enabled = !saving,
                    )
                    Text(stringResource(R.string.period_symptoms), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PeriodSymptom.entries.forEach { symptom ->
                            FilterChip(
                                selected = symptom in editor.symptoms,
                                onClick = { onSymptom(symptom) },
                                label = { Text(periodSymptomLabel(symptom)) },
                                enabled = !saving,
                            )
                        }
                    }
                    Text(stringResource(R.string.period_mood), style = MaterialTheme.typography.titleSmall)
                    PeriodMood.entries.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { mood ->
                                FilterChip(
                                    selected = editor.mood == mood,
                                    onClick = { onMood(if (editor.mood == mood) null else mood) },
                                    label = { Text(periodMoodLabel(mood)) },
                                    enabled = !saving,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    ToolboxTextField(
                        value = editor.notes,
                        onValueChange = { onNotes(it); onClearError() },
                        label = stringResource(R.string.period_notes),
                        minLines = 3,
                        maxLines = 6,
                        enabled = !saving,
                        isError = editor.error == PeriodError.NOTES,
                        supportingText = if (editor.error == PeriodError.NOTES) {
                            {
                                Text(
                                    periodErrorLabel(PeriodError.NOTES),
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                                )
                            }
                        } else null,
                    )
                    editor.error?.takeUnless { it == PeriodError.NOTES }?.let { error ->
                        Text(
                            periodErrorLabel(error),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (editor.cycleId != null) {
                        ToolboxTextButton(onClick = { showDelete = true }, enabled = !saving) {
                            Text(stringResource(R.string.period_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    ToolboxTextButton(onClick = onDismiss, enabled = !saving) {
                        Text(stringResource(R.string.period_cancel))
                    }
                    ToolboxButton(onClick = onSave, enabled = !saving) {
                        Text(stringResource(if (saving) R.string.period_saving else R.string.period_save))
                    }
                }
            }
        }
        }
    }
    if (showDelete) {
        DeleteConfirmationDialog(
            onDismiss = { showDelete = false },
            onConfirm = { showDelete = false; onDelete() },
        )
    }
}

@Composable
private fun PeriodSettingsDialog(
    editor: PeriodSettingsEditor,
    onDismiss: () -> Unit,
    onCycleDays: (String) -> Unit,
    onPeriodDays: (String) -> Unit,
    onSave: () -> Unit,
    onClearError: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
        Surface(
            modifier = Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxWidth().heightIn(max = 620.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(stringResource(R.string.period_settings_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.period_settings_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ToolboxTextField(
                        value = editor.cycleDays,
                        onValueChange = { onCycleDays(it); onClearError() },
                        label = stringResource(R.string.period_setting_cycle),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = editor.error == PeriodError.SETTINGS,
                    )
                    ToolboxTextField(
                        value = editor.periodDays,
                        onValueChange = { onPeriodDays(it); onClearError() },
                        label = stringResource(R.string.period_setting_period),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = editor.error == PeriodError.SETTINGS,
                    )
                    Text(
                        stringResource(R.string.period_setting_bounds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    editor.error?.let {
                        Text(
                            periodErrorLabel(it),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    ToolboxTextButton(onClick = onDismiss) { Text(stringResource(R.string.period_cancel)) }
                    ToolboxButton(onClick = onSave) { Text(stringResource(R.string.period_save)) }
                }
            }
        }
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.widthIn(max = ToolboxDimens.formMaxWidth)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.period_delete_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.period_delete_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    ToolboxTextButton(onClick = onDismiss) { Text(stringResource(R.string.period_cancel)) }
                    ToolboxButton(onClick = onConfirm) {
                        Text(stringResource(R.string.period_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSelectorRow(
    label: String,
    epochDay: Long?,
    dateFormatter: DateTimeFormatter,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                epochDay?.let { dateFormatter.format(LocalDate.ofEpochDay(it)) }.orEmpty(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

private fun showDatePicker(
    context: android.content.Context,
    initialEpochDay: Long,
    maxEpochDay: Long,
    onDate: (Long) -> Unit,
) {
    val initial = LocalDate.ofEpochDay(initialEpochDay)
    DatePickerDialog(
        context,
        { _, year, month, day -> onDate(LocalDate.of(year, month + 1, day).toEpochDay()) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).apply {
        datePicker.minDate = PeriodPolicy.MIN_DATE.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        datePicker.maxDate = LocalDate.ofEpochDay(maxEpochDay).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
    }.show()
}

@Composable
private fun <T> SelectionGroup(
    title: String,
    values: List<T>,
    selected: (T) -> Boolean,
    label: @Composable (T) -> String,
    onClick: (T) -> Unit,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                FilterChip(
                    selected = selected(value),
                    onClick = { onClick(value) },
                    label = { Text(label(value)) },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun periodFlowLabel(value: PeriodFlow): String = stringResource(
    when (value) {
        PeriodFlow.LIGHT -> R.string.period_flow_light
        PeriodFlow.MEDIUM -> R.string.period_flow_medium
        PeriodFlow.HEAVY -> R.string.period_flow_heavy
    },
)

@Composable
private fun periodSymptomLabel(value: PeriodSymptom): String = stringResource(
    when (value) {
        PeriodSymptom.CRAMPS -> R.string.period_symptom_cramps
        PeriodSymptom.HEADACHE -> R.string.period_symptom_headache
        PeriodSymptom.BACK_PAIN -> R.string.period_symptom_back_pain
        PeriodSymptom.FATIGUE -> R.string.period_symptom_fatigue
        PeriodSymptom.BLOATING -> R.string.period_symptom_bloating
        PeriodSymptom.TENDER_BREASTS -> R.string.period_symptom_tender_breasts
        PeriodSymptom.ACNE -> R.string.period_symptom_acne
        PeriodSymptom.NAUSEA -> R.string.period_symptom_nausea
    },
)

@Composable
private fun periodMoodLabel(value: PeriodMood): String = stringResource(
    when (value) {
        PeriodMood.CALM -> R.string.period_mood_calm
        PeriodMood.HAPPY -> R.string.period_mood_happy
        PeriodMood.SENSITIVE -> R.string.period_mood_sensitive
        PeriodMood.IRRITABLE -> R.string.period_mood_irritable
        PeriodMood.LOW -> R.string.period_mood_low
        PeriodMood.ANXIOUS -> R.string.period_mood_anxious
    },
)

@Composable
private fun periodErrorLabel(error: PeriodError): String = stringResource(
    when (error) {
        PeriodError.START_DATE -> R.string.period_error_start_date
        PeriodError.END_DATE -> R.string.period_error_end_date
        PeriodError.DATE_ORDER -> R.string.period_error_date_order
        PeriodError.OVERLAP -> R.string.period_error_overlap
        PeriodError.ONGOING -> R.string.period_error_ongoing
        PeriodError.NOTES -> R.string.period_error_notes
        PeriodError.LIMIT -> R.string.period_error_limit
        PeriodError.SETTINGS -> R.string.period_error_settings
        PeriodError.STORAGE -> R.string.period_error_storage
    },
)

@Composable
private fun periodMessageLabel(message: PeriodMessage): String = stringResource(
    when (message) {
        PeriodMessage.SAVED -> R.string.period_saved
        PeriodMessage.DELETED -> R.string.period_deleted
        PeriodMessage.SETTINGS_SAVED -> R.string.period_settings_saved
        PeriodMessage.FUTURE_DATE -> R.string.period_future_date
        PeriodMessage.STORAGE_FAILED -> R.string.period_error_storage
    },
)
