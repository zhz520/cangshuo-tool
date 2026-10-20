package com.cangshuo.toolbox.feature.tapspeed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.*
import com.cangshuo.toolbox.feature.tapspeed.domain.*
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun TapRoute(factory: ViewModelProvider.Factory) {
    val model: TapViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val s = state.session
    val active = s.stage in TapViewModel.ACTIVE
    val acceptsTouch by rememberUpdatedState(active)
    LifecycleResumeEffect(model) { model.foreground(); onPauseOrDispose { model.background() } }
    val view = LocalView.current
    DisposableEffect(active) { view.keepScreenOn = active; onDispose { view.keepScreenOn = false } }
    var reset by rememberSaveable { mutableStateOf(false) }
    var clear by rememberSaveable { mutableStateOf(false) }
    val bring = remember { BringIntoViewRequester() }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val stage = stringResource(when (s.stage) {
        TapStage.IDLE -> R.string.tap_idle; TapStage.ARMED -> R.string.tap_armed; TapStage.RUNNING -> R.string.tap_running
        TapStage.COMPLETE -> R.string.tap_complete; TapStage.INTERRUPTED -> R.string.tap_interrupted; TapStage.LIMIT -> R.string.tap_limit
    })
    val mode = stringResource(if (s.multiTouch) R.string.tap_multi else R.string.tap_single)
    val sources = tapSources(s.sources)
    val best = state.history.filter { it.seconds == s.seconds && it.multiTouch == s.multiTouch && it.sources == TapPolicy.TOUCH }.maxOfOrNull { it.cps }
    val rows = listOf(
        stringResource(R.string.tap_total) to s.taps.toString(),
        stringResource(R.string.tap_average) to decimal(s.average),
        stringResource(R.string.tap_peak) to s.peak.toString(),
        stringResource(R.string.tap_elapsed) to stringResource(R.string.tap_seconds_value, decimal(s.elapsedMs / 1000.0)),
        stringResource(R.string.tap_source) to sources,
    )
    val note = stringResource(R.string.tap_note)
    val report = if (s.taps == 0) null else if (state.csv) TapPolicy.csv(s) else buildString {
        append(stringResource(R.string.tap_name)).append(" · ").append(stage).append('\n')
        append(stringResource(R.string.tap_duration, s.seconds)).append(" · ").append(mode).append('\n')
        rows.forEach { (key, value) -> append("$key: $value\n") }; append(note)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState(), enabled = !active)
            .padding(ToolboxDimens.pageGutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.tap_instructions), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TapPolicy.durations.forEach { seconds ->
                    val label = stringResource(R.string.tap_duration, seconds)
                    if (seconds == s.seconds) ToolboxButton({ model.selectSeconds(seconds) }, enabled = s.stage == TapStage.IDLE && !state.busy) { Text(label) }
                    else ToolboxOutlinedButton({ model.selectSeconds(seconds) }, enabled = s.stage == TapStage.IDLE && !state.busy) { Text(label) }
                }
            }
            Row(Modifier.fillMaxWidth().toggleable(s.multiTouch, enabled = s.stage == TapStage.IDLE && !state.busy,
                role = Role.Switch, onValueChange = model::selectMulti).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tap_multi_setting), Modifier.weight(1f))
                ToolboxSwitch(s.multiTouch, null, enabled = s.stage == TapStage.IDLE && !state.busy)
            }
            Text(mode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stage, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleSmall)
            ToolResultRow(stringResource(R.string.tap_remaining), stringResource(R.string.tap_seconds_value, decimal(s.remainingMs / 1000.0)))
            val touchLabel = stringResource(R.string.tap_press)
            val targetDescription = stringResource(R.string.tap_target_description, stage, s.taps)
            Box(Modifier.fillMaxWidth().heightIn(min = 200.dp).bringIntoViewRequester(bring)
                .clip(MaterialTheme.shapes.large).background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                .pointerInput(model, s.multiTouch) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (acceptsTouch) {
                                val fresh = event.changes.filter { it.pressed && !it.previousPressed && it.position.x >= 0 && it.position.y >= 0 && it.position.x < size.width && it.position.y < size.height }
                                fresh.take(TapPolicy.downCount(s.multiTouch, event.changes.any { it.previousPressed }, fresh.size)).forEach { model.tap(it.uptimeMillis, TapPolicy.TOUCH) }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }.semantics(mergeDescendants = true) {
                    role = Role.Button; contentDescription = targetDescription
                    if (active) onClick(touchLabel) { model.tap(source = TapPolicy.ACCESSIBILITY); true } else disabled()
                }.onKeyEvent { event ->
                    if (event.key in listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionCenter) && active) {
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) model.tap(source = TapPolicy.KEYBOARD)
                        true
                    } else false
                }.focusRequester(focus).focusable().padding(20.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.taps.toString(), style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace)
                    Text(stringResource(if (s.stage == TapStage.ARMED) R.string.tap_first else if (s.stage == TapStage.RUNNING) R.string.tap_keep_tapping else R.string.tap_target_idle), textAlign = TextAlign.Center)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.stage == TapStage.IDLE) ToolboxButton({ scope.launch { bring.bringIntoView(); model.arm(); focus.requestFocus() } }, enabled = !state.busy && state.error == null) { Text(stringResource(R.string.tap_start)) }
                else ToolboxOutlinedButton({ model.interrupt(); reset = true }, enabled = !state.busy && state.error != TapStorageError.SAVE) { Text(stringResource(R.string.tap_restart)) }
            }
            if (state.busy) ToolboxLoadingIndicator(compact = true)
            state.error?.let { error ->
                Text(stringResource(when (error) { TapStorageError.LOAD -> R.string.tap_load_error; TapStorageError.SAVE -> R.string.tap_save_error; TapStorageError.CLEAR -> R.string.tap_clear_error }), color = MaterialTheme.colorScheme.error)
                ToolboxTextButton(model::retry) { Text(stringResource(R.string.tap_retry)) }
                if (error == TapStorageError.LOAD) ToolboxTextButton({ clear = true }) { Text(stringResource(R.string.tap_clear)) }
            }
            ToolboxDivider()
            ToolSectionHeader(stringResource(R.string.tap_statistics))
            rows.forEach { (key, value) -> ToolResultRow(key, value) }
            ToolResultRow(stringResource(R.string.tap_best), best?.let { decimal(it) } ?: stringResource(R.string.tap_none))
            if (s.taps > 0) {
                val bins = TapPolicy.visibleBins(s)
                val chartDescription = stringResource(R.string.tap_chart_description, s.taps, s.peak)
                ToolSectionHeader(stringResource(R.string.tap_trend))
                ToolboxTrendChart(bins.mapIndexed { index, count -> ToolTrendPoint((index + 1).toDouble(), count.toDouble()) }, chartDescription, Modifier.fillMaxWidth())
                Text(stringResource(R.string.tap_trend_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!active) {
                    bins.forEachIndexed { index, count -> ToolResultRow(stringResource(R.string.tap_second, index + 1), count.toString()) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolboxOutlinedButton({ model.csv(false) }, enabled = state.csv) { Text(stringResource(R.string.tap_summary)) }
                        ToolboxOutlinedButton({ model.csv(true) }, enabled = !state.csv) { Text(stringResource(R.string.tap_csv)) }
                    }
                    SystemReportActions(report, false, {}, showRefresh = false)
                }
            }
            if (!active) {
                ToolboxDivider(); ToolSectionHeader(stringResource(R.string.tap_history))
                Text(stringResource(R.string.tap_history_note, TapPolicy.HISTORY_LIMIT), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.history.isEmpty()) Text(stringResource(R.string.tap_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.history.forEach { item ->
                    val date = remember(item.at) { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.at)) }
                    ToolResultRow(date, stringResource(R.string.tap_history_value, item.seconds, item.taps, decimal(item.cps),
                        stringResource(if (item.multiTouch) R.string.tap_multi else R.string.tap_single), tapSources(item.sources)))
                }
                if (state.history.isNotEmpty()) ToolboxTextButton({ clear = true }, enabled = !state.busy && state.error != TapStorageError.SAVE) { Text(stringResource(R.string.tap_clear)) }
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(ToolboxDimens.pageBottom))
        }
    }
    if (reset || clear) AlertDialog(onDismissRequest = { reset = false; clear = false },
        title = { Text(stringResource(if (clear) R.string.tap_clear else R.string.tap_restart)) },
        text = { Text(stringResource(if (clear) R.string.tap_clear_confirm else R.string.tap_reset_confirm)) },
        confirmButton = { ToolboxTextButton({ if (clear) model.clearHistory() else model.restart(); reset = false; clear = false }) { Text(stringResource(R.string.tap_confirm)) } },
        dismissButton = { ToolboxTextButton({ reset = false; clear = false }) { Text(stringResource(R.string.tap_cancel)) } })
}

@Composable
private fun decimal(value: Double): String {
    val locale = LocalConfiguration.current.locales[0]
    return String.format(locale, "%.2f", value)
}

@Composable
private fun tapSources(mask: Int): String = when (mask) {
    0 -> stringResource(R.string.tap_none); TapPolicy.TOUCH -> stringResource(R.string.tap_touch)
    TapPolicy.KEYBOARD -> stringResource(R.string.tap_keyboard); TapPolicy.ACCESSIBILITY -> stringResource(R.string.tap_accessibility)
    else -> stringResource(R.string.tap_mixed)
}
