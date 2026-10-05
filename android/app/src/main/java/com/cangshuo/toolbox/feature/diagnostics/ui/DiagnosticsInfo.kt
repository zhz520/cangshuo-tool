package com.cangshuo.toolbox.feature.diagnostics.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import kotlinx.coroutines.launch

@Composable
fun DiagnosticsInfo(factory: ViewModelProvider.Factory) {
    val model: DiagnosticsViewModel = viewModel(key = "local.diagnostics", factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    TextButton(onClick = { visible = true; copied = false; model.refresh() }) { Text(stringResource(R.string.diagnostics_title)) }
    if (visible) AlertDialog(onDismissRequest = { visible = false }, title = { Text(stringResource(R.string.diagnostics_title)) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.diagnostics_privacy))
            if (state.loading) ToolboxLoadingIndicator()
            if (state.failed) Text(stringResource(R.string.diagnostics_failed), color = MaterialTheme.colorScheme.error)
            state.data?.let { data ->
                if (!data.systemHistoryAvailable) Text(stringResource(R.string.diagnostics_limited))
                if (data.events.isEmpty()) Text(stringResource(R.string.diagnostics_empty))
                data.events.forEach { event -> Text("${java.time.Instant.ofEpochMilli(event.timestamp)} · ${event.reason.name}") }
                TextButton(enabled = !state.loading, onClick = { scope.launch { model.report(Build.VERSION.SDK_INT)?.let { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("diagnostics", it))); copied = true } } }) {
                    Text(stringResource(if (copied) R.string.diagnostics_copied else R.string.diagnostics_copy))
                }
                TextButton(enabled = !state.loading, onClick = { confirmClear = true }) { Text(stringResource(R.string.diagnostics_clear)) }
            }
            TextButton(enabled = !state.loading, onClick = { copied = false; model.refresh() }) { Text(stringResource(R.string.diagnostics_refresh)) }
        } }, confirmButton = { TextButton(onClick = { visible = false }) { Text(stringResource(R.string.permission_info_close)) } })
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text(stringResource(R.string.diagnostics_clear)) },
        text = { Text(stringResource(R.string.diagnostics_clear_notice)) },
        confirmButton = { TextButton(onClick = { confirmClear = false; copied = false; model.clear() }) { Text(stringResource(R.string.diagnostics_clear)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } })
}
