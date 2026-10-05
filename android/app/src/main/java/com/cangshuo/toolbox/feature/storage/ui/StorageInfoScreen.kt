package com.cangshuo.toolbox.feature.storage.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.*
import com.cangshuo.toolbox.feature.deviceinfo.domain.DeviceInfoFormat
import com.cangshuo.toolbox.feature.storage.domain.*

@Composable
fun StorageInfoRoute(factory: ViewModelProvider.Factory) {
    val model: StorageInfoViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) { model.refresh(); onStopOrDispose { } }
    val unknown = stringResource(R.string.device_info_unknown)
    val groups = state.snapshot?.volumes.orEmpty().mapIndexed { index, volume ->
        val title = if (volume.internal) stringResource(R.string.storage_internal) else volume.name ?: stringResource(R.string.storage_volume, index)
        val rows = buildList {
            add(stringResource(R.string.storage_state) to stringResource(when(volume.state) {
                VolumeState.MOUNTED -> R.string.storage_mounted
                VolumeState.READ_ONLY -> R.string.storage_read_only
                VolumeState.UNAVAILABLE -> R.string.storage_unavailable
            }))
            if (!volume.internal) add(stringResource(R.string.storage_removable) to stringResource(if (volume.removable) R.string.storage_yes else R.string.storage_no))
            val c = volume.capacity
            listOf(R.string.storage_total to c?.total, R.string.storage_used to c?.used, R.string.storage_free to c?.free,
                R.string.storage_available to c?.available, R.string.storage_reserved to c?.reserved).forEach { (label, value) ->
                add(stringResource(label) to (DeviceInfoFormat.bytes(value) ?: unknown))
            }
        }
        title to rows
    }
    val note = stringResource(R.string.storage_note)
    val report = groups.takeIf { it.isNotEmpty() }?.joinToString("\n\n", postfix = "\n\n$note") { (title, rows) ->
        title + "\n" + rows.joinToString("\n") { "${it.first}: ${it.second}" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SystemReportActions(report, state.loading, model::refresh)
        if (state.loading && state.snapshot == null) ToolboxLoadingState(stringResource(R.string.storage_loading))
        if (state.failed || state.snapshot?.partial == true) Text(stringResource(R.string.storage_partial), color = MaterialTheme.colorScheme.error)
        groups.forEach { (title, rows) -> SystemReportCard(title, rows) }
    }
}
