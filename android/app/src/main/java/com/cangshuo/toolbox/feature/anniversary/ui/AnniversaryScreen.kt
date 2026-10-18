package com.cangshuo.toolbox.feature.anniversary.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.UnicodeText
import com.cangshuo.toolbox.core.ui.ToolboxBadge
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxSwitch
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryCategory
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryElapsed
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryError
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryEvent
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryFilter
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryLunarInfo
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryOccurrence
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryPolicy
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryRepeat
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversarySort
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun AnniversaryScreen(
    state: AnniversaryUiState,
    onRefreshToday: () -> Unit,
    onFilter: (AnniversaryFilter) -> Unit,
    onSort: (AnniversarySort) -> Unit,
    onQuery: (String) -> Unit,
    onSave: (Long?, String, String, Long, AnniversaryRepeat, AnniversaryCategory, Boolean) -> Boolean,
    onPin: (AnniversaryEvent, Boolean) -> Unit,
    onDelete: (AnniversaryEvent) -> Unit,
    onLunarInfo: (LocalDate) -> AnniversaryLunarInfo?,
    onClearEditError: () -> Unit,
    onMessageConsumed: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val weekdayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE", locale) }
    val categoryLabels = listOf(
        stringResource(R.string.anniversary_category_birthday),
        stringResource(R.string.anniversary_category_relationship),
        stringResource(R.string.anniversary_category_family),
        stringResource(R.string.anniversary_category_work),
        stringResource(R.string.anniversary_category_other),
    ).toTypedArray()
    val repeatLabels = listOf(
        stringResource(R.string.anniversary_repeat_none),
        stringResource(R.string.anniversary_repeat_monthly),
        stringResource(R.string.anniversary_repeat_yearly),
    ).toTypedArray()
    val messages = listOf(
        AnniversaryMessage.ADDED to stringResource(R.string.anniversary_added),
        AnniversaryMessage.UPDATED to stringResource(R.string.anniversary_updated),
        AnniversaryMessage.DELETED to stringResource(R.string.anniversary_deleted),
        AnniversaryMessage.PINNED to stringResource(R.string.anniversary_pinned_message),
        AnniversaryMessage.UNPINNED to stringResource(R.string.anniversary_unpinned),
        AnniversaryMessage.STORAGE_FAILED to stringResource(R.string.anniversary_storage_failed),
    ).toMap()
    var editor by rememberSaveable { mutableStateOf(false) }
    var editorBaseRevision by rememberSaveable { mutableLongStateOf(0L) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editing = editingId?.let { id -> state.items.firstOrNull { it.id == id } }
    val detail = detailId?.let { id -> state.items.firstOrNull { it.id == id } }
    val deleting = deleteId?.let { id -> state.items.firstOrNull { it.id == id } }

    LaunchedEffect(Unit) { onRefreshToday() }
    LaunchedEffect(state.saveSuccessRevision) {
        if (editor && state.saveSuccessRevision > editorBaseRevision) {
            editor = false
            editingId = null
            onClearEditError()
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(messages[message] ?: return@LaunchedEffect)
        onMessageConsumed()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = ToolboxDimens.pageGutter,
                top = ToolboxDimens.pageTop,
                end = ToolboxDimens.pageGutter,
                bottom = ToolboxDimens.pageBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("stats") { AnniversaryStatsCard(state) }
            item("search") {
                ToolboxTextField(
                    value = state.query,
                    onValueChange = { if (it.length <= 80) onQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.anniversary_search),
                    placeholder = stringResource(R.string.anniversary_search_hint),
                    trailingIcon = if (state.query.isNotEmpty()) {
                        { ToolboxTextButton(onClick = { onQuery("") }) { Text(stringResource(R.string.anniversary_clear_search)) } }
                    } else null,
                )
            }
            item("filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnniversaryFilterChip(AnniversaryFilter.ALL, state.filter, onFilter, stringResource(R.string.anniversary_filter_all))
                        AnniversaryFilterChip(AnniversaryFilter.UPCOMING, state.filter, onFilter, stringResource(R.string.anniversary_filter_upcoming))
                        AnniversaryFilterChip(AnniversaryFilter.TODAY, state.filter, onFilter, stringResource(R.string.anniversary_filter_today))
                        AnniversaryFilterChip(AnniversaryFilter.PAST, state.filter, onFilter, stringResource(R.string.anniversary_filter_past))
                    }
                    Text(stringResource(R.string.anniversary_sort_label), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnniversarySortChip(AnniversarySort.PINNED, state.sort, onSort, stringResource(R.string.anniversary_sort_pinned))
                        AnniversarySortChip(AnniversarySort.NEXT_DATE, state.sort, onSort, stringResource(R.string.anniversary_sort_next))
                        AnniversarySortChip(AnniversarySort.NEWEST, state.sort, onSort, stringResource(R.string.anniversary_sort_newest))
                        AnniversarySortChip(AnniversarySort.NAME, state.sort, onSort, stringResource(R.string.anniversary_sort_name))
                    }
                }
            }
            item("actions") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.anniversary_visible_count, state.items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ToolboxButton(onClick = {
                        editingId = null
                        onClearEditError()
                        editorBaseRevision = state.saveSuccessRevision
                        editor = true
                    }) { Text(stringResource(R.string.anniversary_add)) }
                }
            }
            if (state.loading) {
                item("loading") { ToolboxLoadingState(stringResource(R.string.anniversary_loading)) }
            } else if (state.items.isEmpty()) {
                item("empty") {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(stringResource(R.string.anniversary_empty_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(if (state.query.isNotBlank()) R.string.anniversary_no_matches else R.string.anniversary_empty_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(state.items, key = { it.id }) { event ->
                    AnniversaryCard(
                        event = event,
                        today = state.todayEpochDay,
                        busy = event.id in state.busyEventIds,
                        dateFormatter = dateFormatter,
                        categoryLabels = categoryLabels,
                        repeatLabels = repeatLabels,
                        onDetails = { detailId = event.id },
                        onPin = { onPin(event, !event.pinned) },
                        onEdit = {
                            editingId = event.id
                            onClearEditError()
                            editorBaseRevision = state.saveSuccessRevision
                            editor = true
                        },
                        onDelete = { deleteId = event.id },
                    )
                }
            }
            item("note") {
                Text(
                    stringResource(R.string.anniversary_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }

    if (editor) {
        AnniversaryEditorDialog(
            initial = editing,
            error = state.editError,
            saving = state.saving,
            todayEpochDay = state.todayEpochDay,
            categoryLabels = categoryLabels,
            repeatLabels = repeatLabels,
            dateFormatter = dateFormatter,
            onDismiss = {
                if (!state.saving) {
                    editor = false
                    editingId = null
                    onClearEditError()
                }
            },
            onClearError = onClearEditError,
            onSave = { title, notes, date, repeat, category, pinned ->
                onSave(editing?.id, title, notes, date, repeat, category, pinned)
            },
        )
    }
    detail?.let { event ->
        AnniversaryDetailDialog(
            event = event,
            today = state.todayEpochDay,
            busy = event.id in state.busyEventIds,
            dateFormatter = dateFormatter,
            weekdayFormatter = weekdayFormatter,
            categoryLabels = categoryLabels,
            repeatLabels = repeatLabels,
            lunarInfo = onLunarInfo(LocalDate.ofEpochDay(event.dateEpochDay)),
            onDismiss = { detailId = null },
            onPin = { onPin(event, !event.pinned) },
            onEdit = {
                detailId = null
                editingId = event.id
                onClearEditError()
                editorBaseRevision = state.saveSuccessRevision
                editor = true
            },
            onDelete = { deleteId = event.id },
        )
    }
    deleting?.let { event ->
        AnniversaryConfirmDialog(
            title = stringResource(R.string.anniversary_delete_title),
            message = stringResource(R.string.anniversary_delete_message),
            confirm = stringResource(R.string.anniversary_delete_confirm),
            onDismiss = { deleteId = null },
            onConfirm = {
                deleteId = null
                detailId = null
                onDelete(event)
            },
        )
    }
}

@Composable
private fun AnniversaryStatsCard(state: AnniversaryUiState) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnniversaryStat(stringResource(R.string.anniversary_stat_total), state.stats.total, Modifier.weight(1f))
                AnniversaryStat(stringResource(R.string.anniversary_stat_today), state.stats.today, Modifier.weight(1f), state.stats.today > 0)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnniversaryStat(stringResource(R.string.anniversary_stat_next7), state.stats.nextSevenDays, Modifier.weight(1f))
                AnniversaryStat(stringResource(R.string.anniversary_stat_next30), state.stats.nextThirtyDays, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AnniversaryStat(label: String, value: Int, modifier: Modifier = Modifier, emphasis: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AnniversaryFilterChip(filter: AnniversaryFilter, selected: AnniversaryFilter, onSelect: (AnniversaryFilter) -> Unit, label: String) {
    FilterChip(selected = filter == selected, onClick = { onSelect(filter) }, label = { Text(label) })
}

@Composable
private fun AnniversarySortChip(sort: AnniversarySort, selected: AnniversarySort, onSelect: (AnniversarySort) -> Unit, label: String) {
    FilterChip(selected = sort == selected, onClick = { onSelect(sort) }, label = { Text(label) })
}

@Composable
private fun AnniversaryCard(
    event: AnniversaryEvent,
    today: Long,
    busy: Boolean,
    dateFormatter: DateTimeFormatter,
    categoryLabels: Array<String>,
    repeatLabels: Array<String>,
    onDetails: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val occurrence = AnniversaryPolicy.occurrence(event, today)
    val past = AnniversaryPolicy.isPast(event, today)
    val original = LocalDate.ofEpochDay(event.dateEpochDay)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        event.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.anniversary_original_date, dateFormatter.format(original)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    countdownText(occurrence, past),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (occurrence.daysUntil == 0L && !past) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (event.pinned) ToolboxBadge(stringResource(R.string.anniversary_pinned_badge), emphasized = true)
                ToolboxBadge(categoryLabels[event.category.ordinal])
                ToolboxBadge(repeatLabels[event.repeat.ordinal])
            }
            if (event.notes.isNotBlank()) Text(
                event.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ToolboxTextButton(onClick = onDetails, enabled = !busy) { Text(stringResource(R.string.anniversary_details)) }
                ToolboxTextButton(onClick = onPin, enabled = !busy) {
                    Text(stringResource(if (event.pinned) R.string.anniversary_unpin else R.string.anniversary_pin))
                }
                ToolboxTextButton(onClick = onEdit, enabled = !busy) { Text(stringResource(R.string.anniversary_edit)) }
                ToolboxTextButton(onClick = onDelete, enabled = !busy) {
                    Text(stringResource(R.string.anniversary_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun countdownText(occurrence: AnniversaryOccurrence, past: Boolean): String {
    val days = occurrence.daysUntil
    return when {
        past -> pluralStringResource(
            R.plurals.anniversary_days_past,
            (-days).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            (-days).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
        days == 0L -> stringResource(R.string.anniversary_today)
        days == 1L -> stringResource(R.string.anniversary_tomorrow)
        else -> pluralStringResource(
            R.plurals.anniversary_days_away,
            days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
    }
}

@Composable
private fun AnniversaryEditorDialog(
    initial: AnniversaryEvent?,
    error: AnniversaryError?,
    saving: Boolean,
    todayEpochDay: Long,
    categoryLabels: Array<String>,
    repeatLabels: Array<String>,
    dateFormatter: DateTimeFormatter,
    onDismiss: () -> Unit,
    onClearError: () -> Unit,
    onSave: (String, String, Long, AnniversaryRepeat, AnniversaryCategory, Boolean) -> Unit,
) {
    var title by rememberSaveable(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var notes by rememberSaveable(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var dateEpochDay by rememberSaveable(initial?.id) { mutableLongStateOf(initial?.dateEpochDay ?: todayEpochDay) }
    var repeat by rememberSaveable(initial?.id) { mutableStateOf(initial?.repeat ?: AnniversaryRepeat.YEARLY) }
    var category by rememberSaveable(initial?.id) { mutableStateOf(initial?.category ?: AnniversaryCategory.OTHER) }
    var pinned by rememberSaveable(initial?.id) { mutableStateOf(initial?.pinned ?: false) }
    val context = LocalContext.current
    val chooseDateLabel = stringResource(R.string.anniversary_choose_date)
    val selectedDate = LocalDate.ofEpochDay(dateEpochDay)
    val dateAccessibilityLabel = "$chooseDateLabel: ${dateFormatter.format(selectedDate)}"
    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).heightIn(max = 760.dp).fillMaxHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val compactHeight = maxHeight < 480.dp
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            stringResource(if (initial == null) R.string.anniversary_add else R.string.anniversary_edit_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Column(
                            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                        ToolboxTextField(
                            value = title,
                            onValueChange = {
                                title = UnicodeText.takeCodePointsWithoutSplittingCluster(it, AnniversaryPolicy.MAX_TITLE_CODE_POINTS)
                                onClearError()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = stringResource(R.string.anniversary_title_label),
                            isError = error == AnniversaryError.TITLE,
                            supportingText = {
                                if (error == AnniversaryError.TITLE) AnniversaryErrorText(AnniversaryError.TITLE)
                                else Text(
                                    stringResource(
                                        R.string.anniversary_title_count,
                                        title.codePointCount(0, title.length),
                                        AnniversaryPolicy.MAX_TITLE_CODE_POINTS,
                                    ),
                                )
                            },
                        )
                        ToolboxTextField(
                            value = notes,
                            onValueChange = {
                                notes = UnicodeText.takeCodePointsWithoutSplittingCluster(it, AnniversaryPolicy.MAX_NOTES_CODE_POINTS)
                                onClearError()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = stringResource(R.string.anniversary_notes_label),
                            placeholder = stringResource(R.string.anniversary_notes_hint),
                            minLines = 3,
                            maxLines = 6,
                            isError = error == AnniversaryError.NOTES,
                            supportingText = if (error == AnniversaryError.NOTES) {
                                { AnniversaryErrorText(AnniversaryError.NOTES) }
                            } else null,
                        )
                        Text(stringResource(R.string.anniversary_date_label), style = MaterialTheme.typography.labelLarge)
                        ToolboxOutlinedButton(
                            modifier = Modifier.semantics {
                                contentDescription = dateAccessibilityLabel
                            },
                            onClick = {
                            val dialog = DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    dateEpochDay = LocalDate.of(year, month + 1, day).toEpochDay()
                                    onClearError()
                                },
                                selectedDate.year,
                                selectedDate.monthValue - 1,
                                selectedDate.dayOfMonth,
                            )
                            dialog.datePicker.minDate = AnniversaryPolicy.MIN_DATE.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            dialog.datePicker.maxDate = AnniversaryPolicy.MAX_DATE.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            dialog.show()
                        }) { Text(dateFormatter.format(selectedDate)) }
                        Text(stringResource(R.string.anniversary_category_label), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AnniversaryCategory.entries.forEach { option ->
                                FilterChip(
                                    selected = option == category,
                                    onClick = { category = option; onClearError() },
                                    label = { Text(categoryLabels[option.ordinal]) },
                                )
                            }
                        }
                        Text(stringResource(R.string.anniversary_repeat_label), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AnniversaryRepeat.entries.forEach { option ->
                                FilterChip(
                                    selected = option == repeat,
                                    onClick = { repeat = option; onClearError() },
                                    label = { Text(repeatLabels[option.ordinal]) },
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.anniversary_pinned_label), style = MaterialTheme.typography.bodyLarge)
                            ToolboxSwitch(checked = pinned, onCheckedChange = { pinned = it }, enabled = !saving)
                        }
                            error?.takeIf { it == AnniversaryError.DATE || it == AnniversaryError.LIMIT || it == AnniversaryError.STORAGE }
                                ?.let { AnniversaryErrorText(it) }
                            if (compactHeight) {
                                AnniversaryEditorActions(
                                    saving = saving,
                                    onDismiss = onDismiss,
                                    onSave = { onSave(title, notes, dateEpochDay, repeat, category, pinned) },
                                )
                            }
                        }
                        if (!compactHeight) {
                            AnniversaryEditorActions(
                                saving = saving,
                                onDismiss = onDismiss,
                                onSave = { onSave(title, notes, dateEpochDay, repeat, category, pinned) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnniversaryEditorActions(
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolboxTextButton(onClick = onDismiss, enabled = !saving) {
            Text(stringResource(R.string.anniversary_cancel))
        }
        ToolboxButton(onClick = onSave, enabled = !saving) {
            Text(stringResource(if (saving) R.string.anniversary_saving else R.string.anniversary_save))
        }
    }
}

@Composable
private fun AnniversaryDetailDialog(
    event: AnniversaryEvent,
    today: Long,
    busy: Boolean,
    dateFormatter: DateTimeFormatter,
    weekdayFormatter: DateTimeFormatter,
    categoryLabels: Array<String>,
    repeatLabels: Array<String>,
    lunarInfo: AnniversaryLunarInfo?,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val original = LocalDate.ofEpochDay(event.dateEpochDay)
    val occurrence = AnniversaryPolicy.occurrence(event, today)
    val past = AnniversaryPolicy.isPast(event, today)
    val upcoming = AnniversaryPolicy.upcomingDates(event, today)
    val elapsed = AnniversaryPolicy.elapsedYears(event, today)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp).heightIn(max = 760.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.anniversary_detail_title), style = MaterialTheme.typography.labelMedium)
                            Text(event.title, style = MaterialTheme.typography.titleLarge)
                        }
                        ToolboxTextButton(onClick = onDismiss) { Text(stringResource(R.string.anniversary_cancel)) }
                    }
                    Column(
                        Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            "${dateFormatter.format(original)} · ${weekdayFormatter.format(original)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        lunarInfo?.let { info ->
                            Text(
                                stringResource(
                                    if (info.isLeapMonth) R.string.anniversary_lunar_date_leap else R.string.anniversary_lunar_date,
                                    info.monthChinese,
                                    info.dayChinese,
                                    info.zodiac,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (event.pinned) ToolboxBadge(stringResource(R.string.anniversary_pinned_badge), emphasized = true)
                            ToolboxBadge(categoryLabels[event.category.ordinal])
                            ToolboxBadge(repeatLabels[event.repeat.ordinal])
                            ToolboxBadge(countdownText(occurrence, past), emphasized = !past)
                        }
                        if (!past) Text(
                            stringResource(R.string.anniversary_next_date, dateFormatter.format(occurrence.date)),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        elapsed?.let { value ->
                            Text(
                                pluralStringResource(
                                    R.plurals.anniversary_elapsed_years,
                                    value.years,
                                    value.years,
                                    value.daysSinceLatest.toInt(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (upcoming.isNotEmpty()) {
                            Text(stringResource(R.string.anniversary_next_occurrences), style = MaterialTheme.typography.titleSmall)
                            upcoming.forEach { date ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(dateFormatter.format(date), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        countdownText(AnniversaryOccurrence(date, java.time.temporal.ChronoUnit.DAYS.between(LocalDate.ofEpochDay(today), date)), false),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (event.notes.isNotBlank()) Text(event.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ToolboxTextButton(onClick = onPin, enabled = !busy) {
                            Text(stringResource(if (event.pinned) R.string.anniversary_unpin else R.string.anniversary_pin))
                        }
                        ToolboxTextButton(onClick = onEdit, enabled = !busy) { Text(stringResource(R.string.anniversary_edit)) }
                        ToolboxTextButton(onClick = onDelete, enabled = !busy) {
                            Text(stringResource(R.string.anniversary_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnniversaryErrorText(error: AnniversaryError) {
    Text(
        stringResource(
            when (error) {
                AnniversaryError.TITLE -> R.string.anniversary_error_title
                AnniversaryError.NOTES -> R.string.anniversary_error_notes
                AnniversaryError.DATE -> R.string.anniversary_error_date
                AnniversaryError.LIMIT -> R.string.anniversary_error_limit
                AnniversaryError.STORAGE -> R.string.anniversary_storage_failed
            },
        ),
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun AnniversaryConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { ToolboxButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { ToolboxTextButton(onClick = onDismiss) { Text(stringResource(R.string.anniversary_cancel)) } },
    )
}
