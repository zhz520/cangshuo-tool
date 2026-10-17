package com.cangshuo.toolbox.feature.habit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
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
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.habit.domain.Habit
import com.cangshuo.toolbox.feature.habit.domain.HabitError
import com.cangshuo.toolbox.feature.habit.domain.HabitFilter
import com.cangshuo.toolbox.feature.habit.domain.HabitPolicy
import com.cangshuo.toolbox.feature.habit.domain.HabitSort
import com.cangshuo.toolbox.feature.habit.domain.HabitWeekday
import com.cangshuo.toolbox.feature.habit.domain.HabitWeekProgress
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun HabitScreen(
    state: HabitUiState,
    onRefreshToday: () -> Unit,
    onFilter: (HabitFilter) -> Unit,
    onSort: (HabitSort) -> Unit,
    onQuery: (String) -> Unit,
    onSave: (Long?, String, String, Int) -> Boolean,
    onToggleDate: (Habit, Long) -> Unit,
    onArchive: (Habit, Boolean) -> Unit,
    onDelete: (Habit) -> Unit,
    onClearHistory: (Habit) -> Unit,
    onClearEditError: () -> Unit,
    onMessageConsumed: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val weekdayLabels = stringArrayResource(R.array.habit_weekdays)
    val messages = listOf(
        HabitMessage.CREATED to stringResource(R.string.habit_created),
        HabitMessage.UPDATED to stringResource(R.string.habit_updated),
        HabitMessage.CHECKED to stringResource(R.string.habit_checked),
        HabitMessage.UNCHECKED to stringResource(R.string.habit_unchecked),
        HabitMessage.ARCHIVED to stringResource(R.string.habit_archived_message),
        HabitMessage.RESTORED to stringResource(R.string.habit_restored),
        HabitMessage.DELETED to stringResource(R.string.habit_deleted),
        HabitMessage.HISTORY_CLEARED to stringResource(R.string.habit_history_cleared),
        HabitMessage.ENTRY_LIMIT to stringResource(R.string.habit_entry_limit),
        HabitMessage.STORAGE_FAILED to stringResource(R.string.habit_storage_failed),
    ).toMap()
    var editor by rememberSaveable { mutableStateOf(false) }
    var editorBaseRevision by rememberSaveable { mutableLongStateOf(0L) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var clearHistoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editing = editingId?.let { id -> state.items.firstOrNull { it.id == id } }
    val detail = detailId?.let { id -> state.items.firstOrNull { it.id == id } }
    val delete = deleteId?.let { id -> state.items.firstOrNull { it.id == id } }
    val clearHistory = clearHistoryId?.let { id -> state.items.firstOrNull { it.id == id } }
    val locale = LocalConfiguration.current.locales[0]

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
            item("stats") { HabitStatsCard(state) }
            item("search") {
                ToolboxTextField(
                    value = state.query,
                    onValueChange = { if (it.length <= 80) onQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.habit_search),
                    placeholder = stringResource(R.string.habit_search_hint),
                    trailingIcon = if (state.query.isNotEmpty()) {
                        { ToolboxTextButton(onClick = { onQuery("") }) { Text(stringResource(R.string.habit_clear_search)) } }
                    } else null,
                )
            }
            item("filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HabitFilterChip(HabitFilter.ALL, state.filter, onFilter, stringResource(R.string.habit_filter_all))
                        HabitFilterChip(HabitFilter.TODAY, state.filter, onFilter, stringResource(R.string.habit_filter_today))
                        HabitFilterChip(HabitFilter.ARCHIVED, state.filter, onFilter, stringResource(R.string.habit_filter_archived))
                    }
                    Text(stringResource(R.string.habit_sort_label), style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HabitSortChip(HabitSort.TODAY_FIRST, state.sort, onSort, stringResource(R.string.habit_sort_today))
                        HabitSortChip(HabitSort.STREAK, state.sort, onSort, stringResource(R.string.habit_sort_streak))
                        HabitSortChip(HabitSort.NEWEST, state.sort, onSort, stringResource(R.string.habit_sort_newest))
                        HabitSortChip(HabitSort.NAME, state.sort, onSort, stringResource(R.string.habit_sort_name))
                    }
                }
            }
            item("actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.habit_visible_count, state.items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ToolboxButton(onClick = {
                        editingId = null
                        onClearEditError()
                        editorBaseRevision = state.saveSuccessRevision
                        editor = true
                    }) {
                        Text(stringResource(R.string.habit_add))
                    }
                }
            }
            if (state.loading) {
                item("loading") { ToolboxLoadingState(stringResource(R.string.habit_loading)) }
            } else if (state.items.isEmpty()) {
                item("empty") {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(stringResource(R.string.habit_empty_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(if (state.query.isNotBlank()) R.string.habit_no_matches else R.string.habit_empty_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(state.items, key = { it.id }) { habit ->
                    HabitCard(
                        habit = habit,
                        today = state.todayEpochDay,
                        busy = habit.id in state.busyHabitIds,
                        weekdayLabels = weekdayLabels,
                        onToggleToday = { onToggleDate(habit, state.todayEpochDay) },
                        onDetails = { detailId = habit.id },
                        onEdit = {
                            editingId = habit.id
                            onClearEditError()
                            editorBaseRevision = state.saveSuccessRevision
                            editor = true
                        },
                        onDelete = { deleteId = habit.id },
                    )
                }
            }
            item("note") {
                Text(
                    stringResource(R.string.habit_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }

    if (editor) {
        HabitEditorDialog(
            initial = editing,
            error = state.editError,
            saving = state.saving,
            weekdayLabels = weekdayLabels,
            onDismiss = {
                if (!state.saving) {
                    editor = false
                    editingId = null
                    onClearEditError()
                }
            },
            onClearError = onClearEditError,
            onSave = { name, description, mask -> onSave(editing?.id, name, description, mask) },
        )
    }
    detail?.let { habit ->
        HabitDetailDialog(
            habit = habit,
            today = state.todayEpochDay,
            busy = habit.id in state.busyHabitIds,
            weekdayLabels = weekdayLabels,
            locale = locale,
            onDismiss = { detailId = null },
            onToggleDate = { onToggleDate(habit, it) },
            onEdit = {
                detailId = null
                editingId = habit.id
                onClearEditError()
                editorBaseRevision = state.saveSuccessRevision
                editor = true
            },
            onArchive = { onArchive(habit, !habit.archived); detailId = null },
            onClearHistory = { clearHistoryId = habit.id },
            onDelete = { deleteId = habit.id },
        )
    }
    delete?.let { habit ->
        HabitConfirmDialog(
            title = stringResource(R.string.habit_delete_title),
            message = stringResource(R.string.habit_delete_message),
            confirm = stringResource(R.string.habit_delete_confirm),
            onDismiss = { deleteId = null },
            onConfirm = {
                deleteId = null
                detailId = null
                onDelete(habit)
            },
        )
    }
    clearHistory?.let { habit ->
        HabitConfirmDialog(
            title = stringResource(R.string.habit_clear_history_title),
            message = stringResource(R.string.habit_clear_history_message),
            confirm = stringResource(R.string.habit_clear_history_confirm),
            onDismiss = { clearHistoryId = null },
            onConfirm = {
                clearHistoryId = null
                onClearHistory(habit)
            },
        )
    }
}

@Composable
private fun HabitStatsCard(state: HabitUiState) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HabitStat(stringResource(R.string.habit_stat_active), state.stats.active, Modifier.weight(1f))
                HabitStat(stringResource(R.string.habit_stat_due), state.stats.dueToday, Modifier.weight(1f), state.stats.dueToday > 0)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HabitStat(stringResource(R.string.habit_stat_done), state.stats.completedToday, Modifier.weight(1f))
                HabitStat(stringResource(R.string.habit_stat_streak), state.stats.bestCurrentStreak, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HabitStat(label: String, value: Int, modifier: Modifier = Modifier, emphasis: Boolean = false) {
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
private fun HabitFilterChip(filter: HabitFilter, selected: HabitFilter, onSelect: (HabitFilter) -> Unit, label: String) {
    FilterChip(selected = filter == selected, onClick = { onSelect(filter) }, label = { Text(label) })
}

@Composable
private fun HabitSortChip(sort: HabitSort, selected: HabitSort, onSelect: (HabitSort) -> Unit, label: String) {
    FilterChip(selected = sort == selected, onClick = { onSelect(sort) }, label = { Text(label) })
}

@Composable
private fun HabitCard(
    habit: Habit,
    today: Long,
    busy: Boolean,
    weekdayLabels: Array<String>,
    onToggleToday: () -> Unit,
    onDetails: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val checkedToday = HabitPolicy.isChecked(habit, today)
    val scheduled = HabitPolicy.isScheduled(habit.weekdaysMask, today)
    val progress = HabitPolicy.weekProgress(habit, today)
    val streak = HabitPolicy.currentStreak(habit, today)
    val checkboxState = stringResource(
        when {
            busy -> R.string.habit_action_busy
            checkedToday -> R.string.habit_done_today
            else -> R.string.habit_check_today
        },
    )
    val checkboxDescription = stringResource(R.string.habit_checkbox_description, habit.name, checkboxState)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Checkbox(
                    checked = checkedToday,
                    onCheckedChange = { onToggleToday() },
                    enabled = !habit.archived && !busy,
                    modifier = Modifier.semantics {
                        contentDescription = checkboxDescription
                    },
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        habit.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (habit.description.isNotBlank()) Text(
                        habit.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        when {
                            habit.archived -> ToolboxBadge(stringResource(R.string.habit_archived_badge))
                            checkedToday -> ToolboxBadge(stringResource(R.string.habit_done_today), emphasized = true)
                            scheduled -> ToolboxBadge(stringResource(R.string.habit_due_today), emphasized = true)
                            else -> ToolboxBadge(stringResource(R.string.habit_not_scheduled))
                        }
                        Text(
                            stringResource(R.string.habit_week_progress, progress.completed, progress.scheduled),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            pluralStringResource(R.plurals.habit_current_streak, streak, streak),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HabitWeekStrip(habit, today, weekdayLabels)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ToolboxTextButton(onClick = onDetails, enabled = !busy) { Text(stringResource(R.string.habit_details)) }
                if (!habit.archived) ToolboxTextButton(onClick = onEdit, enabled = !busy) {
                    Text(stringResource(R.string.habit_edit))
                }
                ToolboxTextButton(onClick = onDelete, enabled = !busy) {
                    Text(stringResource(R.string.habit_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun HabitWeekStrip(habit: Habit, today: Long, weekdayLabels: Array<String>) {
    val effectiveToday = if (habit.archived) habit.archivedDay ?: today else today
    val monday = LocalDate.ofEpochDay(effectiveToday).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        repeat(7) { index ->
            val date = monday.plusDays(index.toLong())
            val epochDay = date.toEpochDay()
            val eligible = epochDay in habit.createdDay..effectiveToday
            val scheduled = eligible && HabitPolicy.isScheduled(habit.weekdaysMask, epochDay)
            val completed = HabitPolicy.isChecked(habit, epochDay)
            val colors = MaterialTheme.colorScheme
            val background = when {
                completed -> colors.primary
                scheduled -> colors.surfaceContainerHigh
                else -> colors.surface
            }
            val content = when {
                completed -> colors.onPrimary
                scheduled -> colors.onSurfaceVariant
                else -> colors.outline
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(weekdayLabels[index], style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Surface(
                    shape = CircleShape,
                    color = background,
                    border = BorderStroke(if (epochDay == today) 2.dp else 1.dp, if (epochDay == today) colors.primary else colors.outlineVariant),
                ) {
                    Box(
                        Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 36.dp).padding(horizontal = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = content)
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitEditorDialog(
    initial: Habit?,
    error: HabitError?,
    saving: Boolean,
    weekdayLabels: Array<String>,
    onDismiss: () -> Unit,
    onClearError: () -> Unit,
    onSave: (String, String, Int) -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var description by rememberSaveable(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var mask by rememberSaveable(initial?.id) { mutableIntStateOf(initial?.weekdaysMask ?: HabitPolicy.ALL_DAYS_MASK) }
    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).heightIn(max = 720.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(if (initial == null) R.string.habit_add else R.string.habit_edit_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ToolboxTextField(
                            value = name,
                            onValueChange = {
                                name = UnicodeText.takeCodePointsWithoutSplittingCluster(it, HabitPolicy.MAX_NAME_CODE_POINTS)
                                onClearError()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = stringResource(R.string.habit_name_label),
                            isError = error == HabitError.NAME,
                            supportingText = {
                                if (error == HabitError.NAME) HabitErrorText(HabitError.NAME)
                                else Text(stringResource(R.string.habit_name_count, name.codePointCount(0, name.length), HabitPolicy.MAX_NAME_CODE_POINTS))
                            },
                        )
                        ToolboxTextField(
                            value = description,
                            onValueChange = {
                                description = UnicodeText.takeCodePointsWithoutSplittingCluster(it, HabitPolicy.MAX_DESCRIPTION_CODE_POINTS)
                                onClearError()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = stringResource(R.string.habit_description_label),
                            placeholder = stringResource(R.string.habit_description_hint),
                            minLines = 3,
                            maxLines = 6,
                            isError = error == HabitError.DESCRIPTION,
                            supportingText = if (error == HabitError.DESCRIPTION) {
                                { HabitErrorText(HabitError.DESCRIPTION) }
                            } else null,
                        )
                        Text(stringResource(R.string.habit_choose_weekdays), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HabitWeekday.entries.forEachIndexed { index, weekday ->
                                FilterChip(
                                    selected = mask and weekday.bit != 0,
                                    onClick = {
                                        mask = if (mask and weekday.bit != 0) mask and weekday.bit.inv() else mask or weekday.bit
                                        onClearError()
                                    },
                                    label = { Text(weekdayLabels[index]) },
                                )
                            }
                        }
                        error?.takeIf { it == HabitError.WEEKDAYS || it == HabitError.LIMIT || it == HabitError.STORAGE }
                            ?.let { HabitErrorText(it) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToolboxTextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.habit_cancel)) }
                        ToolboxButton(onClick = { onSave(name, description, mask) }, enabled = !saving) {
                            Text(stringResource(if (saving) R.string.habit_saving else R.string.habit_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitDetailDialog(
    habit: Habit,
    today: Long,
    busy: Boolean,
    weekdayLabels: Array<String>,
    locale: Locale,
    onDismiss: () -> Unit,
    onToggleDate: (Long) -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onClearHistory: () -> Unit,
    onDelete: () -> Unit,
) {
    val current = HabitPolicy.currentStreak(habit, today)
    val best = HabitPolicy.bestStreak(habit)
    val rate = HabitPolicy.completionRate(habit, today)
    val history = HabitPolicy.history(habit, today)
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp),
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
                            Text(stringResource(R.string.habit_detail_title), style = MaterialTheme.typography.labelMedium)
                            Text(habit.name, style = MaterialTheme.typography.titleLarge)
                        }
                        ToolboxTextButton(onClick = onDismiss) { Text(stringResource(R.string.habit_cancel)) }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (habit.description.isNotBlank()) Text(
                            habit.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolboxBadge(pluralStringResource(R.plurals.habit_current_streak, current, current), emphasized = true)
                            ToolboxBadge(pluralStringResource(R.plurals.habit_best_streak, best, best))
                            ToolboxBadge(stringResource(R.string.habit_rate_30d, rate))
                            ToolboxBadge(pluralStringResource(R.plurals.habit_checked_total, habit.checkedDates.size, habit.checkedDates.size))
                        }
                        Text(
                            pluralStringResource(
                                R.plurals.habit_schedule_summary,
                                Integer.bitCount(habit.weekdaysMask),
                                Integer.bitCount(habit.weekdaysMask),
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            HabitWeekday.entries.forEachIndexed { index, weekday ->
                                if (habit.weekdaysMask and weekday.bit != 0) {
                                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                                        Text(
                                            weekdayLabels[index],
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                        Text(stringResource(R.string.habit_history_title), style = MaterialTheme.typography.titleSmall)
                        HabitHistoryGrid(
                            habit = habit,
                            today = today,
                            history = history,
                            weekdayLabels = weekdayLabels,
                            formatter = formatter,
                            busy = busy,
                            onToggleDate = onToggleDate,
                        )
                        Text(
                            stringResource(R.string.habit_history_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (!habit.archived) ToolboxTextButton(onClick = onEdit, enabled = !busy) {
                            Text(stringResource(R.string.habit_edit))
                        }
                        ToolboxOutlinedButton(onClick = onArchive, enabled = !busy) {
                            Text(stringResource(if (habit.archived) R.string.habit_restore else R.string.habit_archive))
                        }
                        ToolboxTextButton(onClick = onClearHistory, enabled = !busy) { Text(stringResource(R.string.habit_clear_history)) }
                        ToolboxTextButton(onClick = onDelete, enabled = !busy) {
                            Text(stringResource(R.string.habit_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitHistoryGrid(
    habit: Habit,
    today: Long,
    history: List<com.cangshuo.toolbox.feature.habit.domain.HabitDay>,
    weekdayLabels: Array<String>,
    formatter: DateTimeFormatter,
    busy: Boolean,
    onToggleDate: (Long) -> Unit,
) {
    val effectiveToday = if (habit.archived) habit.archivedDay ?: today else today
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 400.dp) 4 else 7
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            history.chunked(columns).forEach { group ->
                val firstWeekday = LocalDate.ofEpochDay(group.first().epochDay).dayOfWeek.value - 1
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(columns) { index ->
                            Text(
                                weekdayLabels[(firstWeekday + index) % 7],
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        group.forEach { day ->
                    val date = LocalDate.ofEpochDay(day.epochDay)
                    val beforeStart = day.epochDay < habit.createdDay
                    val enabled = HabitPolicy.canToggleDate(habit, day.epochDay, today) && !busy
                    val completedMark = stringResource(R.string.habit_completed_mark)
                    val colors = MaterialTheme.colorScheme
                    val background = when {
                        day.completed -> colors.primary
                        day.scheduled && !beforeStart -> colors.primaryContainer
                        else -> colors.surface
                    }
                    val contentColor = when {
                        day.completed -> colors.onPrimary
                        day.scheduled && !beforeStart -> colors.onPrimaryContainer
                        else -> colors.onSurfaceVariant
                    }
                    val description = stringResource(
                        R.string.habit_history_cell,
                        formatter.format(date),
                        stringResource(
                            when {
                                beforeStart -> R.string.habit_day_before_start
                                day.completed -> R.string.habit_day_completed
                                day.scheduled -> R.string.habit_day_scheduled
                                else -> R.string.habit_day_unscheduled
                            },
                        ),
                    )
                    Surface(
                        modifier = Modifier.weight(1f).semantics { contentDescription = description }
                            .clickable(enabled = enabled) { onToggleDate(day.epochDay) },
                        shape = MaterialTheme.shapes.small,
                        color = background,
                        border = BorderStroke(
                            if (day.epochDay == effectiveToday) 2.dp else 1.dp,
                            if (day.epochDay == effectiveToday) colors.primary else colors.outlineVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = ToolboxDimens.touchTarget),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, color = contentColor)
                            if (day.completed) Text(
                                completedMark,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor,
                            )
                        }
                    }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitErrorText(error: HabitError) {
    Text(
        stringResource(
            when (error) {
                HabitError.NAME -> R.string.habit_error_name
                HabitError.DESCRIPTION -> R.string.habit_error_description
                HabitError.WEEKDAYS -> R.string.habit_error_weekdays
                HabitError.LIMIT -> R.string.habit_error_limit
                HabitError.ENTRY_LIMIT -> R.string.habit_entry_limit
                HabitError.ARCHIVED -> R.string.habit_error_archived
                HabitError.DATE -> R.string.habit_storage_failed
                HabitError.STORAGE -> R.string.habit_storage_failed
            },
        ),
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun HabitConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { ToolboxButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { ToolboxTextButton(onClick = onDismiss) { Text(stringResource(R.string.habit_cancel)) } },
    )
}
