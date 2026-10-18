package com.cangshuo.toolbox.feature.inventory.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.cangshuo.toolbox.feature.inventory.domain.ExpiryState
import com.cangshuo.toolbox.feature.inventory.domain.InventoryCategory
import com.cangshuo.toolbox.feature.inventory.domain.InventoryError
import com.cangshuo.toolbox.feature.inventory.domain.InventoryFilter
import com.cangshuo.toolbox.feature.inventory.domain.InventoryInput
import com.cangshuo.toolbox.feature.inventory.domain.InventoryItem
import com.cangshuo.toolbox.feature.inventory.domain.InventoryPolicy
import com.cangshuo.toolbox.feature.inventory.domain.InventorySort
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun InventoryScreen(
    state: InventoryUiState,
    onFilter: (InventoryFilter) -> Unit,
    onSort: (InventorySort) -> Unit,
    onQuery: (String) -> Unit,
    onSave: (Long?, InventoryInput) -> Boolean,
    onAdjust: (InventoryItem, Int) -> Unit,
    onDelete: (InventoryItem) -> Unit,
    onClearEditError: () -> Unit,
    onMessageConsumed: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val categoryLabels = listOf(
        stringResource(R.string.inventory_category_electronics),
        stringResource(R.string.inventory_category_home),
        stringResource(R.string.inventory_category_clothing),
        stringResource(R.string.inventory_category_food),
        stringResource(R.string.inventory_category_medicine),
        stringResource(R.string.inventory_category_document),
        stringResource(R.string.inventory_category_other),
    )
    val messages = listOf(
        InventoryMessage.ADDED to stringResource(R.string.inventory_added),
        InventoryMessage.UPDATED to stringResource(R.string.inventory_updated),
        InventoryMessage.DELETED to stringResource(R.string.inventory_deleted),
        InventoryMessage.STORAGE_FAILED to stringResource(R.string.inventory_storage_failed),
    ).toMap()
    var editor by rememberSaveable { mutableStateOf(false) }
    var editorBaseRevision by rememberSaveable { mutableLongStateOf(0L) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editing = editingId?.let { id -> state.items.firstOrNull { it.id == id } }
    val detail = detailId?.let { id -> state.items.firstOrNull { it.id == id } }
    val deleting = deleteId?.let { id -> state.items.firstOrNull { it.id == id } }

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
            state = listState,
            modifier = Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = ToolboxDimens.pageGutter,
                top = ToolboxDimens.pageTop,
                end = ToolboxDimens.pageGutter,
                bottom = ToolboxDimens.pageBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("stats") { InventoryStatsCard(state) }
            item("search") {
                ToolboxTextField(
                    value = state.query,
                    onValueChange = { if (it.length <= 80) onQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.inventory_search),
                    placeholder = stringResource(R.string.inventory_search_hint),
                    trailingIcon = if (state.query.isNotEmpty()) {
                        {
                            ToolboxTextButton(onClick = { onQuery("") }) {
                                Text(stringResource(R.string.inventory_clear_search))
                            }
                        }
                    } else null,
                )
            }
            item("filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InventoryFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = state.filter == filter,
                                onClick = { onFilter(filter) },
                                label = { Text(inventoryFilterLabel(filter)) },
                            )
                        }
                    }
                    Text(stringResource(R.string.inventory_sort_label), style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InventorySort.entries.forEach { sort ->
                            FilterChip(
                                selected = state.sort == sort,
                                onClick = { onSort(sort) },
                                label = { Text(inventorySortLabel(sort)) },
                            )
                        }
                    }
                }
            }
            item("actions") {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.inventory_visible_count, state.items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        ToolboxButton(onClick = {
                            editingId = null
                            onClearEditError()
                            editorBaseRevision = state.saveSuccessRevision
                            editor = true
                        }) { Text(stringResource(R.string.inventory_add)) }
                    }
                }
            }
            if (state.loading) {
                item("loading") { ToolboxLoadingState(stringResource(R.string.inventory_loading)) }
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
                            Text(
                                stringResource(
                                    if (state.query.isNotBlank() || state.filter != InventoryFilter.ALL) {
                                        R.string.inventory_no_matches_title
                                    } else {
                                        R.string.inventory_empty_title
                                    },
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(
                                    if (state.query.isNotBlank() || state.filter != InventoryFilter.ALL) {
                                        R.string.inventory_no_matches
                                    } else {
                                        R.string.inventory_empty_hint
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(state.items, key = { it.id }) { item ->
                    InventoryCard(
                        item = item,
                        today = state.todayEpochDay,
                        busy = item.id in state.busyItemIds,
                        categoryLabel = categoryLabels[item.category.ordinal],
                        dateFormatter = dateFormatter,
                        onAdjust = { delta -> onAdjust(item, delta) },
                        onDetails = { detailId = item.id },
                        onEdit = {
                            editingId = item.id
                            onClearEditError()
                            editorBaseRevision = state.saveSuccessRevision
                            editor = true
                        },
                        onDelete = { deleteId = item.id },
                    )
                }
            }
            item("note") {
                Text(
                    stringResource(R.string.inventory_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }

    if (editor) {
        InventoryEditorDialog(
            initial = editing,
            error = state.editError,
            saving = state.saving,
            categoryLabels = categoryLabels,
            dateFormatter = dateFormatter,
            onDismiss = {
                if (!state.saving) {
                    editor = false
                    editingId = null
                    onClearEditError()
                }
            },
            onClearError = onClearEditError,
            onSave = { input -> onSave(editing?.id, input) },
        )
    }
    detail?.let { item ->
        InventoryDetailDialog(
            item = item,
            today = state.todayEpochDay,
            categoryLabel = categoryLabels[item.category.ordinal],
            dateFormatter = dateFormatter,
            onDismiss = { detailId = null },
            onEdit = {
                detailId = null
                editingId = item.id
                onClearEditError()
                editorBaseRevision = state.saveSuccessRevision
                editor = true
            },
            onDelete = { deleteId = item.id },
        )
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.inventory_delete_title)) },
            text = { Text(stringResource(R.string.inventory_delete_message)) },
            confirmButton = {
                ToolboxTextButton(onClick = {
                    deleteId = null
                    detailId = null
                    onDelete(item)
                }) { Text(stringResource(R.string.inventory_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                ToolboxTextButton(onClick = { deleteId = null }) {
                    Text(stringResource(R.string.inventory_cancel))
                }
            },
        )
    }
}

@Composable
private fun InventoryStatsCard(state: InventoryUiState) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InventoryStat(
                    label = stringResource(R.string.inventory_stat_items),
                    value = state.stats.itemCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                InventoryStat(
                    label = stringResource(R.string.inventory_stat_units),
                    value = state.stats.totalUnits.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InventoryStat(
                    label = stringResource(R.string.inventory_stat_value),
                    value = InventoryPolicy.formatPrice(state.stats.knownValueCents),
                    modifier = Modifier.weight(1f),
                    emphasized = true,
                )
                InventoryStat(
                    label = stringResource(R.string.inventory_stat_low),
                    value = state.stats.lowStockCount.toString(),
                    modifier = Modifier.weight(1f),
                    emphasized = state.stats.lowStockCount > 0,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InventoryStat(
                    label = stringResource(R.string.inventory_stat_expiring),
                    value = state.stats.expiringCount.toString(),
                    modifier = Modifier.weight(1f),
                    emphasized = state.stats.expiringCount > 0,
                )
                InventoryStat(
                    label = stringResource(R.string.inventory_stat_expired),
                    value = state.stats.expiredCount.toString(),
                    modifier = Modifier.weight(1f),
                    emphasized = state.stats.expiredCount > 0,
                    error = state.stats.expiredCount > 0,
                )
            }
        }
    }
}

@Composable
private fun InventoryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    error: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = when {
                error -> MaterialTheme.colorScheme.error
                emphasized -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InventoryCard(
    item: InventoryItem,
    today: Long,
    busy: Boolean,
    categoryLabel: String,
    dateFormatter: DateTimeFormatter,
    onAdjust: (Int) -> Unit,
    onDetails: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val expiry = item.expiryState(today)
    val decreaseDescription = stringResource(R.string.inventory_decrease)
    val increaseDescription = stringResource(R.string.inventory_increase)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    item.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    InventoryPolicy.formatQuantity(item),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ToolboxBadge(categoryLabel)
                if (item.isLowStock()) ToolboxBadge(stringResource(R.string.inventory_low_stock_badge), emphasized = true)
                when (expiry) {
                    ExpiryState.EXPIRING -> InventoryBadge(
                        stringResource(R.string.inventory_expiring_badge),
                        InventoryBadgeTone.WARNING,
                    )
                    ExpiryState.EXPIRED -> InventoryBadge(
                        stringResource(R.string.inventory_expired_badge),
                        InventoryBadgeTone.ERROR,
                    )
                    else -> Unit
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(
                        R.string.inventory_value,
                        item.valueCents?.let(InventoryPolicy::formatPrice)
                            ?: stringResource(R.string.inventory_unknown_value),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (item.location.isBlank()) stringResource(R.string.inventory_no_location)
                    else stringResource(R.string.inventory_location, item.location),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.expiryEpochDay?.let { day ->
                    Text(
                        stringResource(
                            R.string.inventory_expiry_value,
                            dateFormatter.format(LocalDate.ofEpochDay(day)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ToolboxOutlinedButton(
                    onClick = { onAdjust(-1) },
                    enabled = !busy && item.quantity > 0,
                    modifier = Modifier.semantics {
                        contentDescription = decreaseDescription
                    },
                ) { Text(stringResource(R.string.inventory_decrement_short)) }
                ToolboxOutlinedButton(
                    onClick = { onAdjust(1) },
                    enabled = !busy && item.quantity < InventoryPolicy.MAX_QUANTITY,
                    modifier = Modifier.semantics {
                        contentDescription = increaseDescription
                    },
                ) { Text(stringResource(R.string.inventory_increment_short)) }
                ToolboxTextButton(onClick = onDetails, enabled = !busy) {
                    Text(stringResource(R.string.inventory_details))
                }
                ToolboxTextButton(onClick = onEdit, enabled = !busy) {
                    Text(stringResource(R.string.inventory_edit))
                }
                ToolboxTextButton(onClick = onDelete, enabled = !busy) {
                    Text(stringResource(R.string.inventory_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private enum class InventoryBadgeTone { WARNING, ERROR }

@Composable
private fun InventoryBadge(text: String, tone: InventoryBadgeTone) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = if (tone == InventoryBadgeTone.ERROR) colors.errorContainer else colors.tertiaryContainer,
        contentColor = if (tone == InventoryBadgeTone.ERROR) colors.onErrorContainer else colors.onTertiaryContainer,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun InventoryEditorDialog(
    initial: InventoryItem?,
    error: InventoryError?,
    saving: Boolean,
    categoryLabels: List<String>,
    dateFormatter: DateTimeFormatter,
    onDismiss: () -> Unit,
    onClearError: () -> Unit,
    onSave: (InventoryInput) -> Unit,
) {
    val initialPrice = remember(initial?.unitPriceCents) {
        initial?.unitPriceCents?.let { cents ->
            BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString()
        }.orEmpty()
    }
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var notes by rememberSaveable(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var category by rememberSaveable(initial?.id) { mutableStateOf(initial?.category ?: InventoryCategory.HOME) }
    var location by rememberSaveable(initial?.id) { mutableStateOf(initial?.location.orEmpty()) }
    var quantity by rememberSaveable(initial?.id) { mutableStateOf(initial?.quantity?.toString() ?: "1") }
    var unit by rememberSaveable(initial?.id) { mutableStateOf(initial?.unit.orEmpty()) }
    var price by rememberSaveable(initial?.id) { mutableStateOf(initialPrice) }
    var threshold by rememberSaveable(initial?.id) {
        mutableStateOf((initial?.lowStockThreshold ?: 0).toString())
    }
    var purchase by rememberSaveable(initial?.id) { mutableStateOf(initial?.purchaseEpochDay) }
    var expiry by rememberSaveable(initial?.id) { mutableStateOf(initial?.expiryEpochDay) }
    val context = LocalContext.current

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = ToolboxDimens.formMaxWidth)
                    .heightIn(max = 820.dp).fillMaxHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val compactHeight = maxHeight < 560.dp
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            stringResource(if (initial == null) R.string.inventory_add else R.string.inventory_edit_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Column(
                            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ToolboxTextField(
                                value = name,
                                onValueChange = {
                                    name = UnicodeText.takeCodePointsWithoutSplittingCluster(
                                        it,
                                        InventoryPolicy.MAX_NAME_CODE_POINTS,
                                    )
                                    onClearError()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = stringResource(R.string.inventory_name_label),
                                isError = error == InventoryError.NAME,
                                supportingText = if (error == InventoryError.NAME) {
                                    { InventoryErrorText(InventoryError.NAME) }
                                } else null,
                            )
                            ToolboxTextField(
                                value = notes,
                                onValueChange = {
                                    notes = UnicodeText.takeCodePointsWithoutSplittingCluster(
                                        it,
                                        InventoryPolicy.MAX_NOTES_CODE_POINTS,
                                    )
                                    onClearError()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = stringResource(R.string.inventory_notes_label),
                                placeholder = stringResource(R.string.inventory_notes_hint),
                                minLines = 3,
                                maxLines = 6,
                                isError = error == InventoryError.NOTES,
                                supportingText = if (error == InventoryError.NOTES) {
                                    { InventoryErrorText(InventoryError.NOTES) }
                                } else null,
                            )
                            Text(stringResource(R.string.inventory_category_label), style = MaterialTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                InventoryCategory.entries.forEach { option ->
                                    FilterChip(
                                        selected = category == option,
                                        onClick = { category = option; onClearError() },
                                        label = { Text(categoryLabels[option.ordinal]) },
                                    )
                                }
                            }
                            ToolboxTextField(
                                value = location,
                                onValueChange = {
                                    location = UnicodeText.takeCodePointsWithoutSplittingCluster(
                                        it,
                                        InventoryPolicy.MAX_LOCATION_CODE_POINTS,
                                    )
                                    onClearError()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = stringResource(R.string.inventory_location_label),
                                isError = error == InventoryError.LOCATION,
                                supportingText = if (error == InventoryError.LOCATION) {
                                    { InventoryErrorText(InventoryError.LOCATION) }
                                } else null,
                            )
                            ToolboxTextField(
                                value = quantity,
                                onValueChange = {
                                    if (it.length <= 6 && it.all(Char::isDigit)) quantity = it
                                    onClearError()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = stringResource(R.string.inventory_quantity_label),
                                isError = error == InventoryError.QUANTITY,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            ToolboxTextField(
                                value = unit,
                                onValueChange = {
                                    unit = UnicodeText.takeCodePointsWithoutSplittingCluster(
                                        it,
                                        InventoryPolicy.MAX_UNIT_CODE_POINTS,
                                    )
                                    onClearError()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = stringResource(R.string.inventory_unit_label),
                                placeholder = stringResource(R.string.inventory_unit_hint),
                                isError = error == InventoryError.UNIT,
                            )
                            error?.takeIf { it == InventoryError.QUANTITY || it == InventoryError.UNIT }
                                ?.let { InventoryErrorText(it) }
                            ToolboxTextField(
                                value = price,
                                onValueChange = {
                                    if (it.length <= 10 && it.all { char -> char.isDigit() || char == '.' }) {
                                        price = it
                                    }
                                    onClearError()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = stringResource(R.string.inventory_price_label),
                                isError = error == InventoryError.PRICE,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                supportingText = if (error == InventoryError.PRICE) {
                                    { InventoryErrorText(InventoryError.PRICE) }
                                } else null,
                            )
                            ToolboxTextField(
                                value = threshold,
                                onValueChange = {
                                    if (it.length <= 6 && it.all(Char::isDigit)) threshold = it
                                    onClearError()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = stringResource(R.string.inventory_threshold_label),
                                isError = error == InventoryError.THRESHOLD,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = if (error == InventoryError.THRESHOLD) {
                                    { InventoryErrorText(InventoryError.THRESHOLD) }
                                } else null,
                            )
                            InventoryDateEditor(
                                label = stringResource(R.string.inventory_purchase_label),
                                epochDay = purchase,
                                formatter = dateFormatter,
                                onChoose = {
                                    val selected = purchase?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
                                    val dialog = DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            val next = LocalDate.of(year, month + 1, day).toEpochDay()
                                            purchase = next
                                            if (expiry != null && expiry!! < next) expiry = null
                                            onClearError()
                                        },
                                        selected.year,
                                        selected.monthValue - 1,
                                        selected.dayOfMonth,
                                    )
                                    dialog.datePicker.minDate = InventoryPolicy.MIN_DATE
                                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    val maxDate = expiry?.let(LocalDate::ofEpochDay) ?: InventoryPolicy.MAX_DATE
                                    dialog.datePicker.maxDate = maxDate
                                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    dialog.show()
                                },
                                onClear = { purchase = null; onClearError() },
                                onClearError = onClearError,
                            )
                            InventoryDateEditor(
                                label = stringResource(R.string.inventory_expiry_label),
                                epochDay = expiry,
                                formatter = dateFormatter,
                                onChoose = {
                                    val selected = expiry?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
                                    val dialog = DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            expiry = LocalDate.of(year, month + 1, day).toEpochDay()
                                            onClearError()
                                        },
                                        selected.year,
                                        selected.monthValue - 1,
                                        selected.dayOfMonth,
                                    )
                                    val minDate = purchase?.let(LocalDate::ofEpochDay) ?: InventoryPolicy.MIN_DATE
                                    dialog.datePicker.minDate = minDate
                                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    dialog.datePicker.maxDate = InventoryPolicy.MAX_DATE
                                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    dialog.show()
                                },
                                onClear = { expiry = null; onClearError() },
                                onClearError = onClearError,
                            )
                            if (error == InventoryError.DATE) InventoryErrorText(InventoryError.DATE)
                            error?.takeIf { it == InventoryError.LIMIT || it == InventoryError.STORAGE }
                                ?.let { InventoryErrorText(it) }
                            if (compactHeight) {
                                InventoryEditorActions(
                                    saving = saving,
                                    onDismiss = onDismiss,
                                    onSave = {
                                        onSave(
                                            InventoryInput(
                                                name = name,
                                                notes = notes,
                                                category = category,
                                                location = location,
                                                quantityText = quantity,
                                                unit = unit,
                                                unitPriceText = price,
                                                lowStockThresholdText = threshold,
                                                purchaseEpochDay = purchase,
                                                expiryEpochDay = expiry,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                        if (!compactHeight) {
                            InventoryEditorActions(
                                saving = saving,
                                onDismiss = onDismiss,
                                onSave = {
                                    onSave(
                                        InventoryInput(
                                            name = name,
                                            notes = notes,
                                            category = category,
                                            location = location,
                                            quantityText = quantity,
                                            unit = unit,
                                            unitPriceText = price,
                                            lowStockThresholdText = threshold,
                                            purchaseEpochDay = purchase,
                                            expiryEpochDay = expiry,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryDateEditor(
    label: String,
    epochDay: Long?,
    formatter: DateTimeFormatter,
    onChoose: () -> Unit,
    onClear: () -> Unit,
    onClearError: () -> Unit,
) {
    val display = epochDay?.let { formatter.format(LocalDate.ofEpochDay(it)) }
        ?: stringResource(R.string.inventory_no_date)
    val chooseDate = stringResource(R.string.inventory_choose_date)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToolboxOutlinedButton(
                onClick = { onClearError(); onChoose() },
                modifier = Modifier.semantics { contentDescription = "$label: $display" },
            ) { Text(if (epochDay == null) chooseDate else display) }
            if (epochDay != null) {
                ToolboxTextButton(onClick = onClear) {
                    Text(stringResource(R.string.inventory_clear_date))
                }
            }
        }
    }
}

@Composable
private fun InventoryEditorActions(saving: Boolean, onDismiss: () -> Unit, onSave: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolboxTextButton(onClick = onDismiss, enabled = !saving) {
            Text(stringResource(R.string.inventory_cancel))
        }
        ToolboxButton(onClick = onSave, enabled = !saving) {
            Text(stringResource(if (saving) R.string.inventory_saving else R.string.inventory_save))
        }
    }
}

@Composable
private fun InventoryDetailDialog(
    item: InventoryItem,
    today: Long,
    categoryLabel: String,
    dateFormatter: DateTimeFormatter,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = ToolboxDimens.formMaxWidth)
                    .heightIn(max = 720.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        stringResource(R.string.inventory_details_title, item.name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Column(
                        Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ToolboxBadge(categoryLabel)
                            if (item.isLowStock()) {
                                ToolboxBadge(stringResource(R.string.inventory_low_stock_badge), emphasized = true)
                            }
                            when (item.expiryState(today)) {
                                ExpiryState.EXPIRING -> InventoryBadge(
                                    stringResource(R.string.inventory_expiring_badge),
                                    InventoryBadgeTone.WARNING,
                                )
                                ExpiryState.EXPIRED -> InventoryBadge(
                                    stringResource(R.string.inventory_expired_badge),
                                    InventoryBadgeTone.ERROR,
                                )
                                else -> Unit
                            }
                        }
                        InventoryDetailRow(
                            stringResource(R.string.inventory_category_label),
                            categoryLabel,
                        )
                        InventoryDetailRow(
                            stringResource(R.string.inventory_quantity_label),
                            InventoryPolicy.formatQuantity(item),
                        )
                        InventoryDetailRow(
                            stringResource(R.string.inventory_price_label),
                            item.unitPriceCents?.let(InventoryPolicy::formatPrice)
                                ?: stringResource(R.string.inventory_unknown_value),
                        )
                        InventoryDetailRow(
                            stringResource(R.string.inventory_stat_value),
                            item.valueCents?.let(InventoryPolicy::formatPrice)
                                ?: stringResource(R.string.inventory_unknown_value),
                        )
                        InventoryDetailRow(
                            stringResource(R.string.inventory_location_label),
                            item.location.ifBlank { stringResource(R.string.inventory_no_location) },
                        )
                        InventoryDetailRow(
                            stringResource(R.string.inventory_threshold_label),
                            item.lowStockThreshold.toString(),
                        )
                        InventoryDetailRow(
                            stringResource(R.string.inventory_purchase_label),
                            item.purchaseEpochDay?.let { day ->
                                dateFormatter.format(LocalDate.ofEpochDay(day))
                            } ?: stringResource(R.string.inventory_no_date),
                        )
                        InventoryDetailRow(
                            stringResource(R.string.inventory_expiry_label),
                            item.expiryEpochDay?.let { day ->
                                dateFormatter.format(LocalDate.ofEpochDay(day))
                            } ?: stringResource(R.string.inventory_no_date),
                        )
                        if (item.notes.isNotBlank()) {
                            InventoryDetailRow(stringResource(R.string.inventory_notes_label), item.notes)
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ToolboxTextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.inventory_cancel))
                        }
                        ToolboxOutlinedButton(onClick = onEdit) {
                            Text(stringResource(R.string.inventory_edit))
                        }
                        ToolboxOutlinedButton(onClick = onDelete) {
                            Text(
                                stringResource(R.string.inventory_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryDetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun InventoryErrorText(error: InventoryError) {
    Text(
        stringResource(
            when (error) {
                InventoryError.NAME -> R.string.inventory_error_name
                InventoryError.NOTES -> R.string.inventory_error_notes
                InventoryError.LOCATION -> R.string.inventory_error_location
                InventoryError.UNIT -> R.string.inventory_error_unit
                InventoryError.QUANTITY -> R.string.inventory_error_quantity
                InventoryError.PRICE -> R.string.inventory_error_price
                InventoryError.THRESHOLD -> R.string.inventory_error_threshold
                InventoryError.DATE -> R.string.inventory_error_date
                InventoryError.LIMIT -> R.string.inventory_error_limit
                InventoryError.STORAGE -> R.string.inventory_storage_failed
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun inventoryFilterLabel(filter: InventoryFilter): String = stringResource(
    when (filter) {
        InventoryFilter.ALL -> R.string.inventory_filter_all
        InventoryFilter.LOW_STOCK -> R.string.inventory_filter_low
        InventoryFilter.EXPIRING -> R.string.inventory_filter_expiring
        InventoryFilter.EXPIRED -> R.string.inventory_filter_expired
    },
)

@Composable
private fun inventorySortLabel(sort: InventorySort): String = stringResource(
    when (sort) {
        InventorySort.UPDATED -> R.string.inventory_sort_updated
        InventorySort.NAME -> R.string.inventory_sort_name
        InventorySort.QUANTITY -> R.string.inventory_sort_quantity
        InventorySort.VALUE -> R.string.inventory_sort_value
        InventorySort.EXPIRY -> R.string.inventory_sort_expiry
    },
)
