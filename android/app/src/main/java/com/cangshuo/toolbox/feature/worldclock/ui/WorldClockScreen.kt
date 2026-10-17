package com.cangshuo.toolbox.feature.worldclock.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.SystemReportActions
import com.cangshuo.toolbox.core.ui.ToolboxBadge
import com.cangshuo.toolbox.core.ui.ToolListItem
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import com.cangshuo.toolbox.core.ui.ToolboxDivider
import com.cangshuo.toolbox.core.ui.ToolboxIconButton
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockDstState
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockHourFormat
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockPolicy
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockReading
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockZone

@Composable
fun WorldClockScreen(
    state: WorldClockUiState,
    onAddZone: (String) -> Unit,
    onRemoveZone: (String) -> Unit,
    onHourFormatSelected: (WorldClockHourFormat) -> Unit,
    onQueryChanged: (String) -> Unit,
    onClearQuery: () -> Unit,
    onDismissError: () -> Unit,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val reportTitle = stringResource(R.string.world_clock_report_title)
    val reportLine = stringResource(R.string.world_clock_report_line)
    val reportNote = stringResource(R.string.world_clock_note)
    val report = state.systemReading?.let { system ->
        buildString {
            appendLine(reportTitle)
            appendLine(
                String.format(
                    locale,
                    reportLine,
                    system.city,
                    system.time,
                    system.date,
                    system.utcOffset,
                ),
            )
            state.readings.forEach { reading ->
                appendLine(
                    String.format(
                        locale,
                        reportLine,
                        reading.city,
                        reading.time,
                        reading.date,
                        reading.utcOffset,
                    ),
                )
            }
            append(reportNote)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "system_clock") {
            state.systemReading?.let { SystemClockPanel(it) }
        }
        item(key = "controls") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    WorldClockHourFormat.entries.forEach { format ->
                        FilterChip(
                            selected = state.hourFormat == format,
                            onClick = { onHourFormatSelected(format) },
                            label = {
                                Text(
                                    stringResource(
                                        if (format == WorldClockHourFormat.HOUR_24) {
                                            R.string.world_clock_24_hour
                                        } else {
                                            R.string.world_clock_12_hour
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.world_clock_live_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.error?.let { error ->
            item(key = "error") {
                WorldClockErrorBanner(error, onDismissError)
            }
        }
        item(key = "zones_header") {
            ToolSectionHeader(
                title = stringResource(R.string.world_clock_zones_title),
                trailing = {
                    Text(
                        text = stringResource(
                            R.string.world_clock_zone_count,
                            state.readings.size,
                            WorldClockPolicy.MAX_TOTAL_ZONES - 1,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
        item(key = "add_button") {
            ToolboxOutlinedButton(onClick = { showAddDialog = true }) {
                Icon(
                    painterResource(R.drawable.ic_world_clock),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp).size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.world_clock_add_city))
            }
        }
        if (state.readings.isEmpty()) {
            item(key = "empty") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(R.string.world_clock_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.world_clock_empty_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            itemsIndexed(state.readings, key = { _, reading -> reading.zoneId }) { index, reading ->
                Column(Modifier.fillMaxWidth()) {
                    if (index > 0) ToolboxDivider(startIndent = 0.dp, modifier = Modifier.padding(bottom = 12.dp))
                    CityClockRow(reading, onRemoveZone)
                }
            }
        }
        item(key = "report") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolSectionHeader(stringResource(R.string.world_clock_report))
                SystemReportActions(report, loading = false, onRefresh = {}, showRefresh = false)
            }
        }
        item(key = "note") {
            Text(
                text = reportNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
    }

    if (showAddDialog) {
        AddZoneDialog(
            state = state,
            onQueryChanged = onQueryChanged,
            onClearQuery = onClearQuery,
            onAddZone = onAddZone,
            onDismissError = onDismissError,
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun SystemClockPanel(reading: WorldClockReading) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ToolboxBadge(
            text = stringResource(R.string.world_clock_device_zone),
            emphasized = true,
        )
        Text(
            text = reading.city,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = reading.time,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.world_clock_date_weekday, reading.date, reading.weekday),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReadingMetadata(reading)
        Text(
            text = reading.zoneId,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CityClockRow(reading: WorldClockReading, onRemove: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = reading.city,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = reading.zoneId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ToolboxIconButton(
                icon = R.drawable.ic_close,
                contentDescription = stringResource(R.string.world_clock_remove_zone, reading.city),
                onClick = { onRemove(reading.zoneId) },
            )
        }
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = reading.time,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ToolboxBadge(dayDeltaText(reading.dayDelta))
        }
        Text(
            text = stringResource(R.string.world_clock_date_weekday, reading.date, reading.weekday),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReadingMetadata(reading)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingMetadata(reading: WorldClockReading) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ToolboxBadge(reading.utcOffset)
        ToolboxBadge(
            text = stringResource(
                when (reading.dstState) {
                    WorldClockDstState.DAYLIGHT -> R.string.world_clock_dst_daylight
                    WorldClockDstState.STANDARD -> R.string.world_clock_dst_standard
                    WorldClockDstState.FIXED_OFFSET -> R.string.world_clock_dst_fixed
                },
            ),
            emphasized = reading.dstState == WorldClockDstState.DAYLIGHT,
        )
    }
    val transition = reading.nextTransition
    if (transition != null) {
        Text(
            text = stringResource(
                R.string.world_clock_next_change,
                transition.localDateTime,
                transition.newOffset,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (reading.dstState == WorldClockDstState.FIXED_OFFSET) {
        Text(
            text = stringResource(R.string.world_clock_fixed_offset),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddZoneDialog(
    state: WorldClockUiState,
    onQueryChanged: (String) -> Unit,
    onClearQuery: () -> Unit,
    onAddZone: (String) -> Unit,
    onDismissError: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        title = { Text(stringResource(R.string.world_clock_add_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ToolboxTextField(
                    value = state.query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.world_clock_search_label),
                    placeholder = stringResource(R.string.world_clock_search_hint),
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.ic_search),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = if (state.query.isNotEmpty()) {
                        {
                            ToolboxIconButton(
                                icon = R.drawable.ic_close,
                                contentDescription = stringResource(R.string.world_clock_clear_search),
                                onClick = onClearQuery,
                            )
                        }
                    } else {
                        null
                    },
                )
                state.error?.let { WorldClockErrorBanner(it, onDismiss = onDismissError) }
                Text(
                    text = stringResource(
                        R.string.world_clock_search_count,
                        state.searchResults.size,
                        state.allZoneCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ToolboxDivider(startIndent = 0.dp)
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 380.dp)) {
                    if (state.searchResults.isEmpty()) {
                        Text(
                            text = stringResource(R.string.world_clock_search_empty),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxWidth()) {
                            items(state.searchResults, key = { it.id }) { zone ->
                                AddZoneRow(
                                    zone = zone,
                                    added = zone.id == state.systemZoneId || zone.id in state.addedZoneIds,
                                    onAdd = { onAddZone(zone.id) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            ToolboxTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.world_clock_done))
            }
        },
    )
}

@Composable
private fun AddZoneRow(zone: WorldClockZone, added: Boolean, onAdd: () -> Unit) {
    ToolListItem(
        title = zone.city,
        subtitle = zone.id,
        onClick = if (added) null else onAdd,
        showDivider = true,
        dividerIndent = 0.dp,
        trailing = {
            if (added) {
                Text(
                    text = stringResource(R.string.world_clock_already_added),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.world_clock_add),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun WorldClockErrorBanner(error: WorldClockError, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(error.messageResource()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            ToolboxTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.world_clock_dismiss))
            }
        }
    }
}

@StringRes
private fun WorldClockError.messageResource(): Int = when (this) {
    WorldClockError.DUPLICATE_ZONE -> R.string.world_clock_error_duplicate
    WorldClockError.ZONE_LIMIT -> R.string.world_clock_error_limit
    WorldClockError.INVALID_ZONE -> R.string.world_clock_error_invalid
    WorldClockError.QUERY_TOO_LONG -> R.string.world_clock_error_query
    WorldClockError.READ_FAILED -> R.string.world_clock_error_read
}

@Composable
private fun dayDeltaText(delta: Long): String = when {
    delta == 0L -> stringResource(R.string.world_clock_same_day)
    delta == 1L -> stringResource(R.string.world_clock_next_day)
    delta == -1L -> stringResource(R.string.world_clock_previous_day)
    delta > 0L -> pluralStringResource(R.plurals.world_clock_days_ahead, delta.toInt(), delta)
    else -> pluralStringResource(R.plurals.world_clock_days_behind, (-delta).toInt(), -delta)
}
