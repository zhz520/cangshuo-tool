package com.cangshuo.toolbox.feature.sensors.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.*
import com.cangshuo.toolbox.feature.sensors.domain.*
import java.util.Locale

@Composable
fun SensorsRoute(factory: ViewModelProvider.Factory) {
    val model: SensorsViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) { model.start(); onStopOrDispose { model.stop() } }
    val unknown = stringResource(R.string.device_info_unknown)
    val note = stringResource(R.string.sensors_note)
    val groups = state.catalog?.sensors.orEmpty().map { sensor ->
        sensor to listOf(
            stringResource(R.string.sensors_vendor) to sensor.vendor.ifEmpty { unknown },
            stringResource(R.string.sensors_type) to "${sensor.type} / v${sensor.version}",
            stringResource(R.string.sensors_range) to "${sensor.range} ${SensorUnits.unit(sensor.type)}",
            stringResource(R.string.sensors_resolution) to "${sensor.resolution} ${SensorUnits.unit(sensor.type)}",
            stringResource(R.string.sensors_power) to "${sensor.powerMa} mA",
            stringResource(R.string.sensors_delay) to "${sensor.minimumDelayUs} µs",
            stringResource(R.string.sensors_wakeup) to stringResource(if (sensor.wakeUp) R.string.storage_yes else R.string.storage_no)
        )
    }
    val selected = state.catalog?.sensors?.firstOrNull { it.id == state.selected }
    val reading = state.reading
    val live = reading?.let { r -> buildList {
        r.values.forEachIndexed { index, value ->
            val vector = selected?.type in setOf(1,2,4,9,10,14,16)
            val label = if (vector) when(index) { 0 -> "[0] X"; 1 -> "[1] Y"; 2 -> "[2] Z"; else -> "[$index]" } else "[$index]"
            add(label to (value?.let { String.format(Locale.ROOT,"%.5f %s",it,SensorUnits.unit(selected?.type ?: 0)) } ?: unknown))
        }
        add(stringResource(R.string.sensors_accuracy) to stringResource(when(r.accuracy) {
            3 -> R.string.sensors_accuracy_high; 2 -> R.string.sensors_accuracy_medium; 1 -> R.string.sensors_accuracy_low
            0 -> R.string.sensors_accuracy_unreliable; else -> R.string.device_info_unknown
        }))
        add(stringResource(R.string.sensors_timestamp) to String.format(Locale.ROOT,"%.3f s",r.elapsedNanos/1e9))
    }}.orEmpty()
    val report = groups.takeIf { it.isNotEmpty() }?.joinToString("\n\n", postfix = "\n\n$note") { (s, rows) ->
        s.name + "\n" + rows.joinToString("\n") { "${it.first}: ${it.second}" }
    }?.let { text -> if (live.isEmpty()) text else text + "\n\n" + selected?.name + "\n" + live.joinToString("\n") { "${it.first}: ${it.second}" } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SystemReportActions(report, state.loading, model::refresh) }
        if (state.loading) item { ToolboxLoadingState(stringResource(R.string.sensors_loading)) }
        if (state.failed) item { Text(stringResource(R.string.sensors_error), color = MaterialTheme.colorScheme.error) }
        if (state.catalog?.sensors?.isEmpty() == true) item { Text(stringResource(R.string.sensors_empty)) }
        if (state.catalog?.truncated == true || reading?.truncated == true) item { Text(stringResource(R.string.sensors_truncated)) }
        selected?.let { sensor -> item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sensor.name, style = MaterialTheme.typography.titleMedium)
                if (!sensor.liveSupported) Text(stringResource(R.string.sensors_metadata_only))
                else {
                    OutlinedButton(onClick = model::togglePause) { Text(stringResource(if (state.paused) R.string.sensors_resume else R.string.sensors_pause)) }
                    if (live.isEmpty()) Text(stringResource(R.string.sensors_waiting))
                    else SystemReportCard(stringResource(R.string.sensors_values), live)
                }
            }
        } }
        items(groups, key = { it.first.id }) { (sensor, rows) ->
            Column {
                TextButton(onClick = { model.select(sensor.id) }, enabled = !state.loading) { Text(sensor.name) }
                SystemReportCard(sensor.name, rows)
            }
        }
    }
}
