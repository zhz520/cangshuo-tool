package com.cangshuo.toolbox.feature.stopwatch.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.*
import com.cangshuo.toolbox.feature.stopwatch.domain.*
import com.cangshuo.toolbox.ui.theme.ToolboxDimens

@Composable
fun StopwatchRoute(factory: ViewModelProvider.Factory) {
    val model: StopwatchViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(model) { model.start(); onStopOrDispose { model.stop() } }
    val view = LocalView.current
    DisposableEffect(state.running, state.keepScreenOn) {
        view.keepScreenOn = state.running && state.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    val total = StopwatchPolicy.format(state.displayMs)
    val laps = state.laps
    val fastest = StopwatchPolicy.fastestLap(laps)
    val slowest = StopwatchPolicy.slowestLap(laps)
    val average = StopwatchPolicy.averageSplitMs(laps)
    val unknown = stringResource(R.string.stopwatch_none)
    val rows = listOf(
        stringResource(R.string.stopwatch_total) to total,
        stringResource(R.string.stopwatch_lap_count) to laps.size.toString(),
        stringResource(R.string.stopwatch_fastest) to (fastest?.let { StopwatchPolicy.format(it.splitMs) } ?: unknown),
        stringResource(R.string.stopwatch_slowest) to (slowest?.let { StopwatchPolicy.format(it.splitMs) } ?: unknown),
        stringResource(R.string.stopwatch_average) to (average?.let(StopwatchPolicy::format) ?: unknown),
    )
    val status = stringResource(when {
        state.running -> R.string.stopwatch_running
        state.displayMs > 0 -> R.string.stopwatch_paused
        else -> R.string.stopwatch_ready
    })
    val note = stringResource(R.string.stopwatch_note)
    val historyNote = stringResource(R.string.stopwatch_history_note, laps.size, StopwatchPolicy.MAX_LAPS)
    val summary = if (!state.started) null else buildString {
        append(stringResource(R.string.stopwatch_status_label)).append(": ").append(status).append('\n')
        rows.forEach { (label, value) -> append("$label: $value\n") }
        append(note)
    }
    val report = if (state.csvReport) laps.takeIf { it.isNotEmpty() }?.let(StopwatchPolicy::csv)
    else summary
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val runLabel = stringResource(if (state.running) R.string.stopwatch_pause else if (state.started) R.string.stopwatch_resume else R.string.stopwatch_start)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(ToolboxDimens.pageGutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(total, Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace, maxLines = 1)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolboxButton(onClick = model::toggleRun) { Text(runLabel) }
                ToolboxOutlinedButton(onClick = model::lap, enabled = state.running) { Text(stringResource(R.string.stopwatch_lap)) }
                ToolboxTextButton(onClick = { if (laps.isEmpty()) model.reset() else confirmReset = true }, enabled = state.started) {
                    Text(stringResource(R.string.stopwatch_reset))
                }
            }
            state.error?.let { error ->
                Text(stringResource(when (error) {
                    StopwatchError.LAP_LIMIT -> R.string.stopwatch_error_lap_limit
                    StopwatchError.NOT_RUNNING -> R.string.stopwatch_error_not_running
                    StopwatchError.LAP_TOO_SOON -> R.string.stopwatch_error_lap_too_soon
                }), color = MaterialTheme.colorScheme.error)
            }
            ToolboxDivider()
            ToolSectionHeader(stringResource(R.string.stopwatch_stats))
            rows.forEach { (label, value) -> ToolResultRow(label, value) }
            Row(Modifier.fillMaxWidth().toggleable(state.keepScreenOn, role = Role.Switch, onValueChange = model::setKeepScreenOn)
                .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.stopwatch_keep_screen), Modifier.weight(1f))
                ToolboxSwitch(state.keepScreenOn, null)
            }
            ToolboxDivider()
            ToolSectionHeader(stringResource(R.string.stopwatch_laps))
            if (laps.isEmpty()) Text(stringResource(R.string.stopwatch_lap_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                Text(historyNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                laps.asReversed().take(if (state.showAll) StopwatchPolicy.MAX_LAPS else StopwatchPolicy.DISPLAY_LIMIT).forEach { lap ->
                    val badge = when (lap.number) {
                        fastest?.number -> " · " + stringResource(R.string.stopwatch_fastest_badge)
                        slowest?.number -> " · " + stringResource(R.string.stopwatch_slowest_badge)
                        else -> ""
                    }
                    ToolResultRow(stringResource(R.string.stopwatch_lap_number, lap.number),
                        stringResource(R.string.stopwatch_lap_value, StopwatchPolicy.format(lap.splitMs), StopwatchPolicy.format(lap.totalMs)) + badge)
                }
                if (laps.size > StopwatchPolicy.DISPLAY_LIMIT) ToolboxTextButton(onClick = { model.showAll(!state.showAll) }) {
                    Text(stringResource(if (state.showAll) R.string.stopwatch_show_less else R.string.stopwatch_show_all))
                }
            }
            ToolboxDivider()
            ToolSectionHeader(stringResource(R.string.stopwatch_report))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StopwatchOption(stringResource(R.string.stopwatch_summary), !state.csvReport) { model.selectReport(false) }
                StopwatchOption(stringResource(R.string.stopwatch_csv), state.csvReport, laps.isNotEmpty()) { model.selectReport(true) }
            }
            SystemReportActions(report, false, {}, showRefresh = false)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(ToolboxDimens.pageBottom))
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text(stringResource(R.string.stopwatch_reset)) },
        text = { Text(stringResource(R.string.stopwatch_reset_confirm)) }, confirmButton = {
            ToolboxTextButton(onClick = { model.reset(); confirmReset = false }) { Text(stringResource(R.string.stopwatch_reset_action)) }
        }, dismissButton = { ToolboxTextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.stopwatch_cancel)) } })
}

@Composable
private fun StopwatchOption(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val modifier = Modifier.semantics { this.selected = selected }
    if (selected) ToolboxButton(onClick, modifier, enabled) { Text(label) }
    else ToolboxOutlinedButton(onClick, modifier, enabled) { Text(label) }
}
