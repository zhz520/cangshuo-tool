package com.cangshuo.toolbox.feature.fps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.*
import com.cangshuo.toolbox.feature.fps.domain.*
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.isActive

@Composable
fun FpsRoute(factory: ViewModelProvider.Factory) {
    val model: FpsViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val active = state.stage in FpsViewModel.ACTIVE
    LifecycleResumeEffect(model) { model.foreground(); onPauseOrDispose { model.background() } }
    val view = LocalView.current
    DisposableEffect(active, view) {
        val previous = view.keepScreenOn
        if (active) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    var clear by rememberSaveable { mutableStateOf(false) }
    val stage = stringResource(when (state.stage) {
        FpsStage.IDLE -> R.string.fps_idle; FpsStage.WAITING -> R.string.fps_waiting; FpsStage.RUNNING -> R.string.fps_running
        FpsStage.COMPLETE -> R.string.fps_complete; FpsStage.STOPPED -> R.string.fps_stopped; FpsStage.INTERRUPTED -> R.string.fps_interrupted
        FpsStage.NO_FRAMES -> R.string.fps_no_frames; FpsStage.FAILED -> R.string.fps_failed; FpsStage.LIMIT -> R.string.fps_limit
    })
    val configurable = state.stage == FpsStage.IDLE && !state.busy
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(ToolboxDimens.pageGutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.fps_scope), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ToolSectionHeader(stringResource(R.string.fps_duration))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FpsPolicy.durations.forEach { seconds ->
                    FpsChoice(stringResource(R.string.fps_seconds, seconds), state.request.seconds == seconds, configurable) { model.configure(state.request.copy(seconds = seconds)) }
                }
            }
            ToolSectionHeader(stringResource(R.string.fps_budget))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FpsPolicy.budgets.forEach { hz ->
                    FpsChoice(if (hz == 0) stringResource(R.string.fps_auto) else stringResource(R.string.fps_hz_int, hz), state.request.targetHz == hz, configurable) { model.configure(state.request.copy(targetHz = hz)) }
                }
            }
            ToolSectionHeader(stringResource(R.string.fps_load))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FpsPolicy.loads.forEach { count ->
                    FpsChoice(stringResource(R.string.fps_objects, count), state.request.objects == count, configurable) { model.configure(state.request.copy(objects = count)) }
                }
            }
            Text(stage, style = MaterialTheme.typography.titleMedium)
            FpsAnimation(active, state.request.objects)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.stage == FpsStage.IDLE) ToolboxButton({ model.start(view.display?.displayId ?: 0) }, enabled = !state.busy && state.error == null) { Text(stringResource(R.string.fps_start)) }
                else if (active) ToolboxOutlinedButton(model::stop) { Text(stringResource(R.string.fps_stop)) }
                else ToolboxOutlinedButton(model::reset, enabled = !state.busy && state.error != FpsStorageError.SAVE) { Text(stringResource(R.string.fps_again)) }
            }
            if (state.busy) ToolboxLoadingIndicator(compact = true)
            state.error?.let { error ->
                Text(stringResource(when (error) { FpsStorageError.LOAD -> R.string.fps_load_error; FpsStorageError.SAVE -> R.string.fps_save_error; FpsStorageError.CLEAR -> R.string.fps_clear_error }), color = MaterialTheme.colorScheme.error)
                ToolboxTextButton(model::retry) { Text(stringResource(R.string.fps_retry)) }
                if (error == FpsStorageError.LOAD) ToolboxTextButton({ clear = true }) { Text(stringResource(R.string.fps_clear)) }
            }
            state.result?.let { result ->
                val rows = listOf(
                    stringResource(R.string.fps_average) to stringResource(R.string.fps_rate, number(result.fps)),
                    stringResource(R.string.fps_current) to stringResource(R.string.fps_rate, number(result.windows.last().fps)),
                    stringResource(R.string.fps_frames) to result.frames.toString(),
                    stringResource(R.string.fps_elapsed) to stringResource(R.string.fps_seconds_decimal, number(result.elapsedNanos / 1e9)),
                    stringResource(R.string.fps_mean) to stringResource(R.string.fps_ms, number(result.averageMs)),
                    stringResource(R.string.fps_p95) to stringResource(R.string.fps_ms, number(result.p95Nanos / 1e6)),
                    stringResource(R.string.fps_p99) to stringResource(R.string.fps_ms, number(result.p99Nanos / 1e6)),
                    stringResource(R.string.fps_worst) to stringResource(R.string.fps_ms, number(result.worstNanos / 1e6)),
                    stringResource(R.string.fps_long) to stringResource(R.string.fps_long_value, result.longFrames, number(result.longPercent)),
                    stringResource(R.string.fps_missed) to result.estimatedMissed.toString(),
                    stringResource(R.string.fps_actual_budget) to stringResource(R.string.fps_hz, number(result.budgetHz)),
                    stringResource(R.string.fps_refresh) to stringResource(R.string.fps_refresh_range, number(result.minRefresh), number(result.maxRefresh)),
                )
                ToolSectionHeader(stringResource(R.string.fps_statistics))
                rows.forEach { (key, value) -> ToolResultRow(key, value) }
                if (result.maxRefresh - result.minRefresh > 1) Text(stringResource(R.string.fps_variable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                val points = remember(result.windows) {
                    var elapsed = 0L
                    result.windows.map { w -> elapsed += w.nanos; ToolTrendPoint(elapsed / 1e9, w.fps) }
                }
                ToolboxTrendChart(points, stringResource(R.string.fps_chart_description, number(result.fps)), Modifier.fillMaxWidth())
                Text(stringResource(R.string.fps_chart_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!active) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FpsChoice(stringResource(R.string.fps_summary), !state.csv, true) { model.csv(false) }
                        FpsChoice(stringResource(R.string.fps_csv), state.csv, true) { model.csv(true) }
                    }
                    val report = if (state.csv) FpsPolicy.csv(result) else buildString {
                        append(stringResource(R.string.fps_name)).append(" · ").append(stage).append('\n')
                        rows.forEach { (key, value) -> append("$key: $value\n") }
                        append(stringResource(R.string.fps_scope))
                    }
                    SystemReportActions(report, loading = false, onRefresh = {}, showRefresh = false)
                }
            }
            Text(stringResource(R.string.fps_method), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ToolboxDivider()
            ToolSectionHeader(stringResource(R.string.fps_history))
            if (state.history.isEmpty()) Text(stringResource(R.string.fps_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.history.forEach { record ->
                val at = remember(record.at) { DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(record.at)) }
                ToolListItem(title = stringResource(R.string.fps_history_row, at, number(record.fps)),
                    subtitle = stringResource(R.string.fps_history_detail, record.request.seconds, record.request.objects,
                        stringResource(if (record.complete) R.string.fps_complete else R.string.fps_stopped)),
                    onClick = { model.select(record) })
            }
            ToolboxTextButton({ clear = true }, enabled = !active && !state.busy && state.error != FpsStorageError.SAVE && (state.history.isNotEmpty() || state.error == FpsStorageError.LOAD)) { Text(stringResource(R.string.fps_clear)) }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text(stringResource(R.string.fps_clear)) },
        text = { Text(stringResource(R.string.fps_clear_confirm)) }, confirmButton = { ToolboxDangerButton({ clear = false; model.clear() }) { Text(stringResource(R.string.fps_clear)) } },
        dismissButton = { ToolboxTextButton({ clear = false }) { Text(stringResource(R.string.fps_cancel)) } })
}

@Composable private fun FpsChoice(label: String, selected: Boolean, enabled: Boolean, action: () -> Unit) {
    if (selected) ToolboxButton(action, enabled = enabled) { Text(label) }
    else ToolboxOutlinedButton(action, enabled = enabled) { Text(label) }
}

@Composable private fun FpsAnimation(active: Boolean, count: Int) {
    // Reading animation time in the draw phase avoids recomposing the whole page on each frame.
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (active) {
            var first = -1L
            while (isActive) withFrameNanos { time ->
                if (first < 0) first = time
                phase.floatValue = ((time - first) % 4_000_000_000L) / 4_000_000_000f
            }
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val faint = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(R.string.fps_animation_description)
    Canvas(Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceVariant)
        .semantics { contentDescription = description }) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension * .32f
        drawCircle(faint, radius, center, style = Stroke(1.dp.toPx()))
        repeat(count) { i ->
            val theta = (phase.floatValue + i.toFloat() / count) * 2 * PI
            val orbit = radius * (.45f + (i % 3) * .25f)
            drawCircle(primary.copy(alpha = .4f + (i % 3) * .3f), 3.dp.toPx(),
                center + Offset(cos(theta).toFloat() * orbit, sin(theta).toFloat() * orbit))
        }
    }
}
private fun number(value: Double) = String.format(Locale.getDefault(), "%.2f", value)
