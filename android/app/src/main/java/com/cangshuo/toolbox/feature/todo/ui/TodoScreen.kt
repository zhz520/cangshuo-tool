package com.cangshuo.toolbox.feature.todo.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.UnicodeText
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.todo.domain.TodoError
import com.cangshuo.toolbox.feature.todo.domain.TodoFilter
import com.cangshuo.toolbox.feature.todo.domain.TodoItem
import com.cangshuo.toolbox.feature.todo.domain.TodoPolicy
import com.cangshuo.toolbox.feature.todo.domain.TodoPriority
import com.cangshuo.toolbox.feature.todo.domain.TodoSort
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun TodoScreen(
    state: TodoUiState,
    onRefreshToday: () -> Unit,
    onFilter: (TodoFilter) -> Unit,
    onSort: (TodoSort) -> Unit,
    onQuery: (String) -> Unit,
    onSave: (Long?, String, String, Long?, TodoPriority) -> Boolean,
    onToggle: (TodoItem) -> Unit,
    onDelete: (Long) -> Unit,
    onDeleteCompleted: () -> Unit,
    onClearEditError: () -> Unit,
    onMessageConsumed: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val messages = listOf(
        TodoMessage.ADDED to stringResource(R.string.todo_added),
        TodoMessage.UPDATED to stringResource(R.string.todo_updated),
        TodoMessage.DELETED to stringResource(R.string.todo_deleted),
        TodoMessage.CLEARED to stringResource(R.string.todo_cleared),
        TodoMessage.STORAGE_FAILED to stringResource(R.string.todo_storage_failed),
    ).toMap()
    var editor by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var clearCompleted by rememberSaveable { mutableStateOf(false) }
    val editing = editingId?.let { id -> state.items.firstOrNull { it.id == id } }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
    }
    LaunchedEffect(Unit) { onRefreshToday() }
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
            item("stats") {
                TodoStatsCard(state)
            }
            item("search") {
                ToolboxTextField(
                    value = state.query,
                    onValueChange = { if (it.length <= 80) onQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.todo_search),
                    placeholder = stringResource(R.string.todo_search_hint),
                    trailingIcon = if (state.query.isNotEmpty()) {
                        { ToolboxTextButton(onClick = { onQuery("") }) { Text(stringResource(R.string.todo_clear_search)) } }
                    } else null,
                )
            }
            item("filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TodoFilterChip(TodoFilter.ALL, state.filter, { onFilter(it) }, stringResource(R.string.todo_filter_all))
                        TodoFilterChip(TodoFilter.ACTIVE, state.filter, { onFilter(it) }, stringResource(R.string.todo_filter_active))
                        TodoFilterChip(TodoFilter.COMPLETED, state.filter, { onFilter(it) }, stringResource(R.string.todo_filter_completed))
                    }
                    Text(stringResource(R.string.todo_sort_label), style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SortChip(TodoSort.ACTIVE_FIRST, state.sort, onSort, stringResource(R.string.todo_sort_active))
                        SortChip(TodoSort.DUE_DATE, state.sort, onSort, stringResource(R.string.todo_sort_due))
                        SortChip(TodoSort.PRIORITY, state.sort, onSort, stringResource(R.string.todo_sort_priority))
                        SortChip(TodoSort.CREATED_DESC, state.sort, onSort, stringResource(R.string.todo_sort_created))
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
                        stringResource(R.string.todo_visible_count, state.items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ToolboxButton(onClick = { editingId = null; onClearEditError(); editor = true }) {
                        Text(stringResource(R.string.todo_add))
                    }
                }
            }
            if (state.loading) {
                item("loading") { ToolboxLoadingState(stringResource(R.string.todo_loading)) }
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
                            Text(stringResource(R.string.todo_empty_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(if (state.query.isNotBlank()) R.string.todo_no_matches else R.string.todo_empty_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(state.items, key = { it.id }) { item ->
                    TodoRow(
                        item = item,
                        today = state.todayEpochDay,
                        dateFormatter = dateFormatter,
                        onToggle = { onToggle(item) },
                        onEdit = { editingId = item.id; onClearEditError(); editor = true },
                        onDelete = { deleteId = item.id },
                    )
                }
            }
            if (state.stats.completed > 0) {
                item("clear-completed") {
                    ToolboxTextButton(
                        onClick = { clearCompleted = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.todo_clear_completed, state.stats.completed),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            item("note") {
                Text(
                    stringResource(R.string.todo_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }

    if (editor) {
        TodoEditorDialog(
            initial = editing,
            error = state.editError,
            onDismiss = { editor = false; onClearEditError() },
            onClearError = onClearEditError,
            onSave = { title, notes, due, priority ->
                if (onSave(editing?.id, title, notes, due, priority)) {
                    editor = false
                    editingId = null
                }
            },
        )
    }
    deleteId?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.todo_delete_title),
            message = stringResource(R.string.todo_delete_message),
            confirm = stringResource(R.string.todo_delete_confirm),
            onDismiss = { deleteId = null },
            onConfirm = { deleteId = null; onDelete(id) },
        )
    }
    if (clearCompleted) {
        ConfirmDialog(
            title = stringResource(R.string.todo_clear_title),
            message = stringResource(R.string.todo_clear_message),
            confirm = stringResource(R.string.todo_clear_confirm),
            onDismiss = { clearCompleted = false },
            onConfirm = { clearCompleted = false; onDeleteCompleted() },
        )
    }
}

@Composable
private fun TodoStatsCard(state: TodoUiState) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(stringResource(R.string.todo_stat_active), state.stats.active, Modifier.weight(1f))
                Stat(
                    stringResource(R.string.todo_stat_overdue),
                    state.stats.overdue,
                    Modifier.weight(1f),
                    state.stats.overdue > 0,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(stringResource(R.string.todo_stat_today), state.stats.dueToday, Modifier.weight(1f))
                Stat(stringResource(R.string.todo_stat_completed), state.stats.completed, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier = Modifier, emphasis: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasis) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
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
private fun TodoFilterChip(filter: TodoFilter, selected: TodoFilter, onSelect: (TodoFilter) -> Unit, label: String) {
    FilterChip(selected = filter == selected, onClick = { onSelect(filter) }, label = { Text(label) })
}

@Composable
private fun SortChip(sort: TodoSort, selected: TodoSort, onSelect: (TodoSort) -> Unit, label: String) {
    FilterChip(selected = sort == selected, onClick = { onSelect(sort) }, label = { Text(label) })
}

@Composable
private fun TodoRow(
    item: TodoItem,
    today: Long,
    dateFormatter: DateTimeFormatter,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(
                    checked = item.completed,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.padding(top = 2.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.notes.isNotBlank()) Text(
                        item.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        PriorityBadge(item.priority)
                        item.dueDateEpochDay?.let { due ->
                            Text(
                                todoDueText(due, today, dateFormatter),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (!item.completed && due < today) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ToolboxTextButton(onClick = onEdit) { Text(stringResource(R.string.todo_edit)) }
                ToolboxTextButton(onClick = onDelete) {
                    Text(stringResource(R.string.todo_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PriorityBadge(priority: TodoPriority) {
    val colors = MaterialTheme.colorScheme
    val (background, content) = when (priority) {
        TodoPriority.HIGH -> colors.errorContainer to colors.onErrorContainer
        TodoPriority.NORMAL -> colors.secondaryContainer to colors.onSecondaryContainer
        TodoPriority.LOW -> colors.surfaceContainerHigh to colors.onSurfaceVariant
    }
    Surface(shape = MaterialTheme.shapes.small, color = background) {
        Text(
            stringResource(priority.labelRes()),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

private fun TodoPriority.labelRes(): Int = when (this) {
    TodoPriority.HIGH -> R.string.todo_priority_high
    TodoPriority.NORMAL -> R.string.todo_priority_normal
    TodoPriority.LOW -> R.string.todo_priority_low
}

@Composable
private fun todoDueText(epochDay: Long, today: Long, formatter: DateTimeFormatter): String {
    val date = formatter.format(LocalDate.ofEpochDay(epochDay))
    return when (val days = epochDay - today) {
        -1L -> stringResource(R.string.todo_due_overdue_one, date)
        in Long.MIN_VALUE..-2L -> stringResource(
            R.string.todo_due_overdue,
            (-days).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            date,
        )
        0L -> stringResource(R.string.todo_due_today, date)
        1L -> stringResource(R.string.todo_due_tomorrow, date)
        else -> stringResource(R.string.todo_due_date, date)
    }
}

@Composable
private fun TodoEditorDialog(
    initial: TodoItem?,
    error: TodoError?,
    onDismiss: () -> Unit,
    onClearError: () -> Unit,
    onSave: (String, String, Long?, TodoPriority) -> Unit,
) {
    var title by rememberSaveable(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var notes by rememberSaveable(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var due by rememberSaveable(initial?.id) { mutableStateOf(initial?.dueDateEpochDay) }
    var priority by rememberSaveable(initial?.id) { mutableStateOf(initial?.priority ?: TodoPriority.NORMAL) }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
    }
    val dueText = due?.let { formatter.format(LocalDate.ofEpochDay(it)) }
    Dialog(
        onDismissRequest = onDismiss,
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
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        stringResource(if (initial == null) R.string.todo_add else R.string.todo_edit_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                ToolboxTextField(
                    value = title,
                    onValueChange = { title = UnicodeText.takeCodePointsWithoutSplittingCluster(it, TodoPolicy.MAX_TITLE_CODE_POINTS); onClearError() },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.todo_title_label),
                    isError = error == TodoError.TITLE,
                    supportingText = {
                        if (error == TodoError.TITLE) ErrorText(TodoError.TITLE)
                        else Text(stringResource(R.string.todo_title_count, title.codePointCount(0, title.length), TodoPolicy.MAX_TITLE_CODE_POINTS))
                    },
                )
                ToolboxTextField(
                    value = notes,
                    onValueChange = { notes = UnicodeText.takeCodePointsWithoutSplittingCluster(it, TodoPolicy.MAX_NOTES_CODE_POINTS); onClearError() },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.todo_notes_label),
                    placeholder = stringResource(R.string.todo_notes_hint),
                    minLines = 3,
                    maxLines = 6,
                    isError = error == TodoError.NOTES,
                    supportingText = if (error == TodoError.NOTES) { { ErrorText(TodoError.NOTES) } } else null,
                )
                Text(stringResource(R.string.todo_priority), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PriorityChip(TodoPriority.HIGH, priority, { priority = it })
                    PriorityChip(TodoPriority.NORMAL, priority, { priority = it })
                    PriorityChip(TodoPriority.LOW, priority, { priority = it })
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolboxOutlinedButton(onClick = {
                        val value = due?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                due = LocalDate.of(year, month + 1, day).toEpochDay()
                                onClearError()
                            },
                            value.year,
                            value.monthValue - 1,
                            value.dayOfMonth,
                        ).show()
                    }) { Text(dueText ?: stringResource(R.string.todo_pick_date)) }
                    if (due != null) ToolboxTextButton(onClick = { due = null }) {
                        Text(stringResource(R.string.todo_clear_date))
                    }
                }
                error?.takeIf { it == TodoError.LIMIT || it == TodoError.STORAGE }?.let { ErrorText(it) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToolboxTextButton(onClick = onDismiss) { Text(stringResource(R.string.todo_cancel)) }
                        ToolboxButton(onClick = { onSave(title, notes, due, priority) }) {
                            Text(stringResource(R.string.todo_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorText(error: TodoError) {
    Text(
        stringResource(
            when (error) {
                TodoError.TITLE -> R.string.todo_error_title
                TodoError.NOTES -> R.string.todo_error_notes
                TodoError.LIMIT -> R.string.todo_error_limit
                TodoError.STORAGE -> R.string.todo_storage_failed
            },
        ),
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun PriorityChip(priority: TodoPriority, selected: TodoPriority, onSelect: (TodoPriority) -> Unit) {
    FilterChip(
        selected = priority == selected,
        onClick = { onSelect(priority) },
        label = { Text(stringResource(priority.labelRes())) },
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { ToolboxButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { ToolboxTextButton(onClick = onDismiss) { Text(stringResource(R.string.todo_cancel)) } },
    )
}
