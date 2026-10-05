package com.cangshuo.toolbox.core.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SystemReportActions(report: String?, loading: Boolean, onRefresh: () -> Unit, showRefresh: Boolean = true) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showRefresh) OutlinedButton(onClick = { failed = false; onRefresh() }, enabled = !loading) {
                if (loading) ToolboxLoadingIndicator(compact = true)
                Text(stringResource(R.string.device_info_refresh))
            }
            OutlinedButton(enabled = report != null, onClick = {
                failed = false
                scope.launch {
                    try { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Report", report))) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { failed = true }
                }
            }) { Text(stringResource(R.string.device_info_copy)) }
            OutlinedButton(enabled = report != null, onClick = {
                failed = false
                try {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, report)
                    }, null))
                } catch (_: Exception) { failed = true }
            }) { Text(stringResource(R.string.device_info_share)) }
        }
        if (failed) Text(stringResource(R.string.system_report_action_error), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun SystemReportCard(title: String, rows: List<Pair<String, String>>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEach { (label, value) ->
                Column {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }
    }
}
