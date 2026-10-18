package com.cangshuo.toolbox.feature.subscription.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.UnicodeText
import com.cangshuo.toolbox.core.ui.ToolboxBadge
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxSwitch
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.subscription.domain.BillingCycle
import com.cangshuo.toolbox.feature.subscription.domain.DueState
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionCategory
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionError
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionFilter
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionInput
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionItem
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionPolicy
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionSort
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SubscriptionScreen(
    state: SubscriptionUiState,
    onFilter: (SubscriptionFilter) -> Unit,
    onSort: (SubscriptionSort) -> Unit,
    onQuery: (String) -> Unit,
    onSave: (Long?, SubscriptionInput) -> Boolean,
    onSetActive: (SubscriptionItem, Boolean) -> Unit,
    onRenew: (SubscriptionItem) -> Unit,
    onDelete: (SubscriptionItem) -> Unit,
    onClearEditError: () -> Unit,
    onMessageConsumed: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val categoryLabels = listOf(
        stringResource(R.string.subscription_category_entertainment),
        stringResource(R.string.subscription_category_software),
        stringResource(R.string.subscription_category_music),
        stringResource(R.string.subscription_category_cloud),
        stringResource(R.string.subscription_category_education),
        stringResource(R.string.subscription_category_health),
        stringResource(R.string.subscription_category_news),
        stringResource(R.string.subscription_category_other),
    )
    val messages = listOf(
        SubscriptionMessage.ADDED to stringResource(R.string.subscription_added),
        SubscriptionMessage.UPDATED to stringResource(R.string.subscription_updated),
        SubscriptionMessage.DELETED to stringResource(R.string.subscription_deleted),
        SubscriptionMessage.RENEWED to stringResource(R.string.subscription_renewed),
        SubscriptionMessage.PAUSED to stringResource(R.string.subscription_paused_message),
        SubscriptionMessage.RESUMED to stringResource(R.string.subscription_resumed),
        SubscriptionMessage.STORAGE_FAILED to stringResource(R.string.subscription_storage_failed),
    ).toMap()
    var notificationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationGranted = granted }
    var editor by rememberSaveable { mutableStateOf(false) }
    var editorBaseRevision by rememberSaveable { mutableLongStateOf(0L) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editing = editingId?.let { id -> state.items.firstOrNull { it.id == id } }
    val detail = detailId?.let { id -> state.items.firstOrNull { it.id == id } }
    val deleting = deleteId?.let { id -> state.items.firstOrNull { it.id == id } }
    val hasReminder = state.hasActiveReminders

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            notificationGranted = Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    LaunchedEffect(state.saveSuccessRevision) {
        if (editor && state.saveSuccessRevision > editorBaseRevision) {
            editor = false
            editingId = null
            onClearEditError()
        }
    }
    LaunchedEffect(state.messageRevision) {
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
            item("stats") { SubscriptionStatsCard(state) }
            if (!notificationGranted && hasReminder && Build.VERSION.SDK_INT >= 33) {
                item("notification-permission") {
                    NotificationPermissionCard { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }
            }
            item("search") {
                ToolboxTextField(
                    value = state.query,
                    onValueChange = { if (it.length <= 80) onQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.subscription_search),
                    placeholder = stringResource(R.string.subscription_search_hint),
                    trailingIcon = if (state.query.isNotEmpty()) {
                        { ToolboxTextButton(onClick = { onQuery("") }) { Text(stringResource(R.string.subscription_clear_search)) } }
                    } else null,
                )
            }
            item("filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SubscriptionFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = state.filter == filter,
                                onClick = { onFilter(filter) },
                                label = { Text(subscriptionFilterLabel(filter)) },
                            )
                        }
                    }
                    Text(stringResource(R.string.subscription_sort_label), style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SubscriptionSort.entries.forEach { sort ->
                            FilterChip(
                                selected = state.sort == sort,
                                onClick = { onSort(sort) },
                                label = { Text(subscriptionSortLabel(sort)) },
                            )
                        }
                    }
                }
            }
            item("actions") {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.subscription_visible_count, state.items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        ToolboxButton(onClick = {
                            editingId = null
                            onClearEditError()
                            editorBaseRevision = state.saveSuccessRevision
                            editor = true
                        }) { Text(stringResource(R.string.subscription_add)) }
                    }
                }
            }
            if (state.loading) {
                item("loading") { ToolboxLoadingState(stringResource(R.string.subscription_loading)) }
            } else if (state.items.isEmpty()) {
                item("empty") { SubscriptionEmptyState(state) }
            } else {
                items(state.items, key = { it.id }) { item ->
                    SubscriptionCard(
                        item = item,
                        today = state.todayEpochDay,
                        busy = item.id in state.busyItemIds,
                        categoryLabel = categoryLabels[item.category.ordinal],
                        dateFormatter = dateFormatter,
                        onRenew = { onRenew(item) },
                        onSetActive = { onSetActive(item, it) },
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
                    stringResource(R.string.subscription_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }

    if (editor) {
        SubscriptionEditorDialog(
            initial = editing,
            error = state.editError,
            saving = state.saving,
            todayEpochDay = state.todayEpochDay,
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
        SubscriptionDetailDialog(
            item = item,
            today = state.todayEpochDay,
            categoryLabel = categoryLabels[item.category.ordinal],
            dateFormatter = dateFormatter,
            onDismiss = { detailId = null },
            onRenew = { detailId = null; onRenew(item) },
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
        SubscriptionDeleteDialog(
            itemName = item.name,
            onDismiss = { deleteId = null },
            onConfirm = {
                deleteId = null
                detailId = null
                onDelete(item)
            },
        )
    }
}

@Composable
private fun SubscriptionStatsCard(state: SubscriptionUiState) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SubscriptionStat(
                    stringResource(R.string.subscription_stat_active),
                    state.stats.activeCount.toString(),
                    Modifier.weight(1f),
                    emphasized = state.stats.activeCount > 0,
                )
                SubscriptionStat(
                    stringResource(R.string.subscription_stat_monthly),
                    SubscriptionPolicy.formatPrice(state.stats.monthlyCostCents),
                    Modifier.weight(1f),
                    emphasized = true,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SubscriptionStat(
                    stringResource(R.string.subscription_stat_yearly),
                    SubscriptionPolicy.formatPrice(state.stats.yearlyCostCents),
                    Modifier.weight(1f),
                )
                SubscriptionStat(
                    stringResource(R.string.subscription_stat_due),
                    state.stats.dueSoonCount.toString(),
                    Modifier.weight(1f),
                    emphasized = state.stats.dueSoonCount > 0,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SubscriptionStat(
                    stringResource(R.string.subscription_stat_overdue),
                    state.stats.overdueCount.toString(),
                    Modifier.weight(1f),
                    emphasized = state.stats.overdueCount > 0,
                    error = state.stats.overdueCount > 0,
                )
                SubscriptionStat(
                    stringResource(R.string.subscription_stat_paused),
                    state.stats.pausedCount.toString(),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SubscriptionStat(
    label: String,
    value: String,
    modifier: Modifier,
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
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NotificationPermissionCard(onRequest: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.subscription_notification_permission_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.subscription_notification_permission_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ToolboxOutlinedButton(onClick = onRequest) {
                    Text(stringResource(R.string.subscription_notification_permission_action))
                }
            }
        }
    }
}

@Composable
private fun SubscriptionEmptyState(state: SubscriptionUiState) {
    val filtered = state.query.isNotBlank() || state.filter != SubscriptionFilter.ALL
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
                stringResource(if (filtered) R.string.subscription_no_matches_title else R.string.subscription_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(if (filtered) R.string.subscription_no_matches else R.string.subscription_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SubscriptionCard(
    item: SubscriptionItem,
    today: Long,
    busy: Boolean,
    categoryLabel: String,
    dateFormatter: DateTimeFormatter,
    onRenew: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onDetails: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val due = item.dueState(today)
    val next = SubscriptionPolicy.effectiveNextPayment(item, today)
    val renewDescription = stringResource(
        R.string.subscription_action_for_item,
        stringResource(R.string.subscription_renew),
        item.name,
    )
    val activeDescription = stringResource(
        R.string.subscription_action_for_item,
        stringResource(if (item.active) R.string.subscription_pause else R.string.subscription_resume),
        item.name,
    )
    val detailsDescription = stringResource(
        R.string.subscription_action_for_item,
        stringResource(R.string.subscription_details),
        item.name,
    )
    val editDescription = stringResource(
        R.string.subscription_action_for_item,
        stringResource(R.string.subscription_edit),
        item.name,
    )
    val deleteDescription = stringResource(
        R.string.subscription_action_for_item,
        stringResource(R.string.subscription_delete),
        item.name,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.provider,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(
                        R.string.subscription_price_per_cycle,
                        SubscriptionPolicy.formatPrice(item.priceCents),
                        subscriptionCycleLabel(item.billingCycle, item.customCycleDays),
                    ),
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
                ToolboxBadge(
                    stringResource(
                        if (item.autoRenew) R.string.subscription_auto_renew_badge
                        else R.string.subscription_manual_renew_badge,
                    ),
                )
                when (due) {
                    DueState.DUE_SOON -> SubscriptionBadge(
                        stringResource(R.string.subscription_due_soon_badge),
                        SubscriptionBadgeTone.WARNING,
                    )
                    DueState.OVERDUE -> SubscriptionBadge(
                        stringResource(R.string.subscription_overdue_badge),
                        SubscriptionBadgeTone.ERROR,
                    )
                    DueState.PAUSED -> SubscriptionBadge(
                        stringResource(R.string.subscription_paused_badge),
                        SubscriptionBadgeTone.NEUTRAL,
                    )
                    DueState.NORMAL -> Unit
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(R.string.subscription_next_payment_value, dateFormatter.format(LocalDate.ofEpochDay(next))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.subscription_monthly_value, SubscriptionPolicy.formatPrice(item.monthlyCostCents)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.paymentMethod.isNotBlank()) {
                    Text(
                        stringResource(R.string.subscription_payment_method_value, item.paymentMethod),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.reminderEnabled) {
                    Text(
                        subscriptionReminderLabel(item.reminderDays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (item.active) {
                    ToolboxOutlinedButton(
                        onClick = onRenew,
                        enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = renewDescription },
                    ) { Text(stringResource(R.string.subscription_renew)) }
                }
                ToolboxOutlinedButton(
                    onClick = { onSetActive(!item.active) },
                    enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = activeDescription },
                ) {
                    Text(stringResource(if (item.active) R.string.subscription_pause else R.string.subscription_resume))
                }
                ToolboxTextButton(
                    onClick = onDetails,
                    enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = detailsDescription },
                ) { Text(stringResource(R.string.subscription_details)) }
                ToolboxTextButton(
                    onClick = onEdit,
                    enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = editDescription },
                ) { Text(stringResource(R.string.subscription_edit)) }
                ToolboxTextButton(
                    onClick = onDelete,
                    enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = deleteDescription },
                ) {
                    Text(stringResource(R.string.subscription_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private enum class SubscriptionBadgeTone { WARNING, ERROR, NEUTRAL }

@Composable
private fun SubscriptionBadge(text: String, tone: SubscriptionBadgeTone) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = when (tone) {
            SubscriptionBadgeTone.ERROR -> colors.errorContainer
            SubscriptionBadgeTone.WARNING -> colors.tertiaryContainer
            SubscriptionBadgeTone.NEUTRAL -> colors.surfaceVariant
        },
        contentColor = when (tone) {
            SubscriptionBadgeTone.ERROR -> colors.onErrorContainer
            SubscriptionBadgeTone.WARNING -> colors.onTertiaryContainer
            SubscriptionBadgeTone.NEUTRAL -> colors.onSurfaceVariant
        },
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
private fun SubscriptionEditorDialog(
    initial: SubscriptionItem?,
    error: SubscriptionError?,
    saving: Boolean,
    todayEpochDay: Long,
    categoryLabels: List<String>,
    dateFormatter: DateTimeFormatter,
    onDismiss: () -> Unit,
    onClearError: () -> Unit,
    onSave: (SubscriptionInput) -> Unit,
) {
    val initialPrice = remember(initial?.priceCents) {
        initial?.priceCents?.let { BigDecimal.valueOf(it, 2).stripTrailingZeros().toPlainString() }.orEmpty()
    }
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var provider by rememberSaveable(initial?.id) { mutableStateOf(initial?.provider.orEmpty()) }
    var category by rememberSaveable(initial?.id) { mutableStateOf(initial?.category ?: SubscriptionCategory.SOFTWARE) }
    var price by rememberSaveable(initial?.id) { mutableStateOf(initialPrice) }
    var cycle by rememberSaveable(initial?.id) { mutableStateOf(initial?.billingCycle ?: BillingCycle.MONTHLY) }
    var customDays by rememberSaveable(initial?.id) {
        mutableStateOf((initial?.customCycleDays ?: 30).toString())
    }
    var start by rememberSaveable(initial?.id) { mutableLongStateOf(initial?.startEpochDay ?: todayEpochDay) }
    var next by rememberSaveable(initial?.id) { mutableLongStateOf(initial?.nextPaymentEpochDay ?: todayEpochDay) }
    var paymentMethod by rememberSaveable(initial?.id) { mutableStateOf(initial?.paymentMethod.orEmpty()) }
    var notes by rememberSaveable(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var reminderEnabled by rememberSaveable(initial?.id) { mutableStateOf(initial?.reminderEnabled ?: true) }
    var reminderDays by rememberSaveable(initial?.id) { mutableStateOf((initial?.reminderDays ?: 7).toString()) }
    var autoRenew by rememberSaveable(initial?.id) { mutableStateOf(initial?.autoRenew ?: true) }
    var active by rememberSaveable(initial?.id) { mutableStateOf(initial?.active ?: true) }
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
                modifier = Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxWidth()
                    .heightIn(max = 840.dp).fillMaxHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val compactHeight = maxHeight < 580.dp
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            stringResource(
                                if (initial == null) R.string.subscription_add else R.string.subscription_edit_title,
                            ),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Column(
                            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SubscriptionTextField(
                                value = name,
                                onValueChange = { name = take(it, SubscriptionPolicy.MAX_NAME_CODE_POINTS); onClearError() },
                                label = stringResource(R.string.subscription_name_label),
                                error = error.takeIf { it == SubscriptionError.NAME },
                            )
                            SubscriptionTextField(
                                value = provider,
                                onValueChange = { provider = take(it, SubscriptionPolicy.MAX_PROVIDER_CODE_POINTS); onClearError() },
                                label = stringResource(R.string.subscription_provider_label),
                                error = error.takeIf { it == SubscriptionError.PROVIDER },
                            )
                            Text(stringResource(R.string.subscription_category_label), style = MaterialTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SubscriptionCategory.entries.forEach { option ->
                                    FilterChip(
                                        selected = category == option,
                                        onClick = { category = option; onClearError() },
                                        label = { Text(categoryLabels[option.ordinal]) },
                                    )
                                }
                            }
                            ToolboxTextField(
                                value = price,
                                onValueChange = {
                                    if (it.length <= 10 && it.all { char -> char.isDigit() || char == '.' }) price = it
                                    onClearError()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = stringResource(R.string.subscription_price_label),
                                isError = error == SubscriptionError.PRICE,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                supportingText = if (error == SubscriptionError.PRICE) {
                                    { SubscriptionErrorText(SubscriptionError.PRICE) }
                                } else null,
                            )
                            Text(stringResource(R.string.subscription_cycle_label), style = MaterialTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                BillingCycle.entries.forEach { option ->
                                    FilterChip(
                                        selected = cycle == option,
                                        onClick = { cycle = option; onClearError() },
                                        label = { Text(subscriptionCycleLabel(option, customDays.toIntOrNull() ?: 30)) },
                                    )
                                }
                            }
                            if (cycle == BillingCycle.CUSTOM) {
                                ToolboxTextField(
                                    value = customDays,
                                    onValueChange = {
                                        if (it.length <= 4 && it.all(Char::isDigit)) customDays = it
                                        onClearError()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = stringResource(R.string.subscription_custom_days_label),
                                    isError = error == SubscriptionError.CUSTOM_DAYS,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    supportingText = if (error == SubscriptionError.CUSTOM_DAYS) {
                                        { SubscriptionErrorText(SubscriptionError.CUSTOM_DAYS) }
                                    } else null,
                                )
                            }
                            SubscriptionDateField(
                                label = stringResource(R.string.subscription_start_date_label),
                                epochDay = start,
                                formatter = dateFormatter,
                                onChoose = {
                                    val selected = LocalDate.ofEpochDay(start)
                                    val dialog = DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            start = LocalDate.of(year, month + 1, day).toEpochDay()
                                            if (next < start) next = start
                                            onClearError()
                                        },
                                        selected.year,
                                        selected.monthValue - 1,
                                        selected.dayOfMonth,
                                    )
                                    dialog.datePicker.minDate = minMillis(SubscriptionPolicy.MIN_DATE)
                                    dialog.datePicker.maxDate = minMillis(LocalDate.ofEpochDay(next))
                                    dialog.show()
                                },
                            )
                            SubscriptionDateField(
                                label = stringResource(R.string.subscription_next_payment_label),
                                epochDay = next,
                                formatter = dateFormatter,
                                onChoose = {
                                    val selected = LocalDate.ofEpochDay(next)
                                    val dialog = DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            next = LocalDate.of(year, month + 1, day).toEpochDay()
                                            onClearError()
                                        },
                                        selected.year,
                                        selected.monthValue - 1,
                                        selected.dayOfMonth,
                                    )
                                    dialog.datePicker.minDate = minMillis(LocalDate.ofEpochDay(start))
                                    dialog.datePicker.maxDate = minMillis(SubscriptionPolicy.MAX_DATE)
                                    dialog.show()
                                },
                            )
                            if (error == SubscriptionError.DATE) SubscriptionErrorText(SubscriptionError.DATE)
                            SubscriptionTextField(
                                value = paymentMethod,
                                onValueChange = {
                                    paymentMethod = take(it, SubscriptionPolicy.MAX_PAYMENT_METHOD_CODE_POINTS)
                                    onClearError()
                                },
                                label = stringResource(R.string.subscription_payment_method_label),
                                placeholder = stringResource(R.string.subscription_payment_method_hint),
                                error = error.takeIf { it == SubscriptionError.PAYMENT_METHOD },
                            )
                            ToolboxTextField(
                                value = notes,
                                onValueChange = {
                                    notes = take(it, SubscriptionPolicy.MAX_NOTES_CODE_POINTS)
                                    onClearError()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = stringResource(R.string.subscription_notes_label),
                                placeholder = stringResource(R.string.subscription_notes_hint),
                                minLines = 3,
                                maxLines = 6,
                                isError = error == SubscriptionError.NOTES,
                                supportingText = if (error == SubscriptionError.NOTES) {
                                    { SubscriptionErrorText(SubscriptionError.NOTES) }
                                } else null,
                            )
                            SettingSwitchRow(
                                stringResource(R.string.subscription_reminder_enabled_label),
                                reminderEnabled,
                            ) { reminderEnabled = it; onClearError() }
                            ToolboxTextField(
                                value = reminderDays,
                                onValueChange = {
                                    if (it.length <= 2 && it.all(Char::isDigit)) reminderDays = it
                                    onClearError()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = stringResource(R.string.subscription_reminder_days_label),
                                enabled = reminderEnabled,
                                isError = error == SubscriptionError.REMINDER_DAYS,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = if (error == SubscriptionError.REMINDER_DAYS) {
                                    { SubscriptionErrorText(SubscriptionError.REMINDER_DAYS) }
                                } else null,
                            )
                            SettingSwitchRow(
                                stringResource(R.string.subscription_auto_renew_label),
                                autoRenew,
                            ) { autoRenew = it; onClearError() }
                            SettingSwitchRow(
                                stringResource(R.string.subscription_active_label),
                                active,
                            ) { active = it; onClearError() }
                            error?.takeIf { it == SubscriptionError.LIMIT || it == SubscriptionError.STORAGE }
                                ?.let { SubscriptionErrorText(it) }
                            if (compactHeight) {
                                SubscriptionEditorActions(
                                    saving = saving,
                                    onDismiss = onDismiss,
                                    onSave = {
                                        onSave(
                                            SubscriptionInput(
                                                name, provider, category, price, cycle, customDays, start, next,
                                                paymentMethod, notes, reminderEnabled, reminderDays, autoRenew, active,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                        if (!compactHeight) {
                            SubscriptionEditorActions(
                                saving = saving,
                                onDismiss = onDismiss,
                                onSave = {
                                    onSave(
                                        SubscriptionInput(
                                            name, provider, category, price, cycle, customDays, start, next,
                                            paymentMethod, notes, reminderEnabled, reminderDays, autoRenew, active,
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
private fun SubscriptionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    error: SubscriptionError?,
) {
    ToolboxTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = label,
        placeholder = placeholder,
        isError = error != null,
        supportingText = error?.let { { SubscriptionErrorText(it) } },
    )
}

@Composable
private fun SubscriptionDateField(
    label: String,
    epochDay: Long,
    formatter: DateTimeFormatter,
    onChoose: () -> Unit,
) {
    val display = formatter.format(LocalDate.ofEpochDay(epochDay))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        ToolboxOutlinedButton(
            onClick = onChoose,
            modifier = Modifier.semantics { contentDescription = "$label: $display" },
        ) { Text(display) }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        ToolboxSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SubscriptionEditorActions(saving: Boolean, onDismiss: () -> Unit, onSave: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolboxTextButton(onClick = onDismiss, enabled = !saving) {
            Text(stringResource(R.string.subscription_cancel))
        }
        ToolboxButton(onClick = onSave, enabled = !saving) {
            Text(stringResource(if (saving) R.string.subscription_saving else R.string.subscription_save))
        }
    }
}

@Composable
private fun SubscriptionDetailDialog(
    item: SubscriptionItem,
    today: Long,
    categoryLabel: String,
    dateFormatter: DateTimeFormatter,
    onDismiss: () -> Unit,
    onRenew: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val next = SubscriptionPolicy.effectiveNextPayment(item, today)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxWidth().heightIn(max = 760.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.subscription_details_title, item.name), style = MaterialTheme.typography.titleLarge)
                    Column(
                        Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ToolboxBadge(categoryLabel)
                            ToolboxBadge(
                                stringResource(
                                    if (item.autoRenew) R.string.subscription_auto_renew_badge
                                    else R.string.subscription_manual_renew_badge,
                                ),
                            )
                            when (item.dueState(today)) {
                                DueState.DUE_SOON -> SubscriptionBadge(
                                    stringResource(R.string.subscription_due_soon_badge),
                                    SubscriptionBadgeTone.WARNING,
                                )
                                DueState.OVERDUE -> SubscriptionBadge(
                                    stringResource(R.string.subscription_overdue_badge),
                                    SubscriptionBadgeTone.ERROR,
                                )
                                DueState.PAUSED -> SubscriptionBadge(
                                    stringResource(R.string.subscription_paused_badge),
                                    SubscriptionBadgeTone.NEUTRAL,
                                )
                                DueState.NORMAL -> Unit
                            }
                        }
                        DetailRow(stringResource(R.string.subscription_provider_label), item.provider)
                        DetailRow(stringResource(R.string.subscription_category_label), categoryLabel)
                        DetailRow(
                            stringResource(R.string.subscription_status_label),
                            stringResource(
                                if (item.active) R.string.subscription_status_active
                                else R.string.subscription_status_paused,
                            ),
                        )
                        DetailRow(
                            stringResource(R.string.subscription_price_label),
                            stringResource(
                                R.string.subscription_price_per_cycle,
                                SubscriptionPolicy.formatPrice(item.priceCents),
                                subscriptionCycleLabel(item.billingCycle, item.customCycleDays),
                            ),
                        )
                        DetailRow(
                            stringResource(R.string.subscription_start_date_label),
                            dateFormatter.format(LocalDate.ofEpochDay(item.startEpochDay)),
                        )
                        DetailRow(
                            stringResource(R.string.subscription_next_payment_label),
                            dateFormatter.format(LocalDate.ofEpochDay(next)),
                        )
                        DetailRow(
                            stringResource(R.string.subscription_stat_monthly),
                            SubscriptionPolicy.formatPrice(item.monthlyCostCents),
                        )
                        DetailRow(
                            stringResource(R.string.subscription_stat_yearly),
                            SubscriptionPolicy.formatPrice(item.yearlyCostCents),
                        )
                        if (item.paymentMethod.isNotBlank()) {
                            DetailRow(stringResource(R.string.subscription_payment_method_label), item.paymentMethod)
                        }
                        DetailRow(
                            stringResource(R.string.subscription_reminder_enabled_label),
                            if (item.reminderEnabled) subscriptionReminderLabel(item.reminderDays)
                            else stringResource(R.string.subscription_reminder_disabled),
                        )
                        if (item.notes.isNotBlank()) {
                            DetailRow(stringResource(R.string.subscription_notes_label), item.notes)
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ToolboxTextButton(onClick = onDismiss) { Text(stringResource(R.string.subscription_cancel)) }
                        if (item.active) {
                            ToolboxOutlinedButton(onClick = onRenew) { Text(stringResource(R.string.subscription_renew)) }
                        }
                        ToolboxOutlinedButton(onClick = onEdit) { Text(stringResource(R.string.subscription_edit)) }
                        ToolboxOutlinedButton(onClick = onDelete) {
                            Text(stringResource(R.string.subscription_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionDeleteDialog(itemName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxWidth().heightIn(max = 520.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.subscription_delete_title, itemName),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            stringResource(R.string.subscription_delete_message, itemName),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ToolboxTextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.subscription_cancel))
                        }
                        ToolboxTextButton(onClick = onConfirm) {
                            Text(
                                stringResource(R.string.subscription_delete_confirm),
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
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SubscriptionErrorText(error: SubscriptionError) {
    Text(
        stringResource(
            when (error) {
                SubscriptionError.NAME -> R.string.subscription_error_name
                SubscriptionError.PROVIDER -> R.string.subscription_error_provider
                SubscriptionError.PAYMENT_METHOD -> R.string.subscription_error_payment_method
                SubscriptionError.NOTES -> R.string.subscription_error_notes
                SubscriptionError.PRICE -> R.string.subscription_error_price
                SubscriptionError.DATE -> R.string.subscription_error_date
                SubscriptionError.CUSTOM_DAYS -> R.string.subscription_error_custom_days
                SubscriptionError.REMINDER_DAYS -> R.string.subscription_error_reminder_days
                SubscriptionError.LIMIT -> R.string.subscription_error_limit
                SubscriptionError.STORAGE -> R.string.subscription_storage_failed
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun subscriptionFilterLabel(filter: SubscriptionFilter): String = stringResource(
    when (filter) {
        SubscriptionFilter.ALL -> R.string.subscription_filter_all
        SubscriptionFilter.ACTIVE -> R.string.subscription_filter_active
        SubscriptionFilter.PAUSED -> R.string.subscription_filter_paused
        SubscriptionFilter.DUE -> R.string.subscription_filter_due
    },
)

@Composable
private fun subscriptionSortLabel(sort: SubscriptionSort): String = stringResource(
    when (sort) {
        SubscriptionSort.NEXT_PAYMENT -> R.string.subscription_sort_next
        SubscriptionSort.UPDATED -> R.string.subscription_sort_updated
        SubscriptionSort.PRICE -> R.string.subscription_sort_price
        SubscriptionSort.NAME -> R.string.subscription_sort_name
    },
)

@Composable
private fun subscriptionCycleLabel(cycle: BillingCycle, customDays: Int): String = when (cycle) {
    BillingCycle.WEEKLY -> stringResource(R.string.subscription_cycle_weekly)
    BillingCycle.MONTHLY -> stringResource(R.string.subscription_cycle_monthly)
    BillingCycle.QUARTERLY -> stringResource(R.string.subscription_cycle_quarterly)
    BillingCycle.HALF_YEARLY -> stringResource(R.string.subscription_cycle_half_yearly)
    BillingCycle.YEARLY -> stringResource(R.string.subscription_cycle_yearly)
    BillingCycle.CUSTOM -> pluralStringResource(
        R.plurals.subscription_cycle_custom_days,
        customDays,
        customDays,
    )
}

@Composable
private fun subscriptionReminderLabel(days: Int): String =
    if (days == 0) stringResource(R.string.subscription_reminder_same_day)
    else pluralStringResource(R.plurals.subscription_reminder_days_value, days, days)

private fun take(value: String, max: Int): String =
    UnicodeText.takeCodePointsWithoutSplittingCluster(value, max)

private fun minMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
