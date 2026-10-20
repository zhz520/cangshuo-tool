package com.cangshuo.toolbox.feature.reaction.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
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
import com.cangshuo.toolbox.feature.reaction.domain.*
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun ReactionRoute(factory: ViewModelProvider.Factory) {
    val model: ReactionViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(model) { model.foreground(); onPauseOrDispose { model.background() } }
    val view = LocalView.current
    val active = state.stage in ReactionViewModel.ACTIVE
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val targetView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val stats = ReactionPolicy.statistics(state.samples)
    val stageLabel = stringResource(when (state.stage) {
        ReactionStage.IDLE -> R.string.reaction_idle
        ReactionStage.WAITING -> R.string.reaction_waiting
        ReactionStage.CUE, ReactionStage.READY -> R.string.reaction_tap_now
        ReactionStage.RESULT -> R.string.reaction_round_done
        ReactionStage.EARLY -> R.string.reaction_early
        ReactionStage.TIMEOUT -> R.string.reaction_timeout
        ReactionStage.INTERRUPTED -> R.string.reaction_interrupted
        ReactionStage.COMPLETE -> R.string.reaction_complete
    })
    val note = stringResource(R.string.reaction_note)
    val rows = stats?.let { listOf(
        stringResource(R.string.reaction_best) to ms(it.best.toDouble()),
        stringResource(R.string.reaction_average) to ms(it.mean),
        stringResource(R.string.reaction_median) to ms(it.median),
        stringResource(R.string.reaction_worst) to ms(it.worst.toDouble()),
        stringResource(R.string.reaction_deviation) to ms(it.deviation),
    ) } ?: emptyList()
    val report = if (state.samples.isEmpty()) null else if (state.csv) ReactionPolicy.csv(state.samples) else buildString {
        append(stringResource(R.string.reaction_name)).append('\n')
        append(stringResource(R.string.reaction_progress, state.samples.size, state.rounds)).append('\n')
        rows.forEach { (label, value) -> append("$label: $value\n") }
        append(stringResource(R.string.reaction_failures, state.early, state.missed)).append('\n')
        append(note)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxWidth()
            .verticalScroll(rememberScrollState(), enabled = !active).padding(ToolboxDimens.pageGutter),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.reaction_instructions), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReactionPolicy.roundOptions.forEach { count ->
                    val label = stringResource(R.string.reaction_rounds, count)
                    if (count == state.rounds) ToolboxButton(onClick = { model.selectRounds(count) }, enabled = state.stage == ReactionStage.IDLE && !state.busy) { Text(label) }
                    else ToolboxOutlinedButton(onClick = { model.selectRounds(count) }, enabled = state.stage == ReactionStage.IDLE && !state.busy) { Text(label) }
                }
            }
            Text(stringResource(R.string.reaction_progress, state.samples.size, state.rounds), style = MaterialTheme.typography.titleSmall)
            val cue = state.stage == ReactionStage.CUE || state.stage == ReactionStage.READY
            val failed = state.stage == ReactionStage.EARLY || state.stage == ReactionStage.TIMEOUT
            val color = when { cue -> MaterialTheme.colorScheme.primaryContainer; failed -> MaterialTheme.colorScheme.errorContainer; else -> MaterialTheme.colorScheme.surfaceContainer }
            val ink = when { cue -> MaterialTheme.colorScheme.onPrimaryContainer; failed -> MaterialTheme.colorScheme.onErrorContainer; else -> MaterialTheme.colorScheme.onSurface }
            val action = stringResource(R.string.reaction_tap_action)
            // No color tween: the cue appears in the same draw pass that timestamps its onset.
            Box(Modifier.fillMaxWidth().heightIn(min = 220.dp).bringIntoViewRequester(targetView).clip(MaterialTheme.shapes.large).background(color)
                .drawWithContent { drawContent(); if (cue) model.cueDrawn() }
                .pointerInput(model) { awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    model.tap(down.uptimeMillis)
                    down.consume()
                    waitForUpOrCancellation()
                } }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    stateDescription = stageLabel
                    if (active) onClick(action) { model.tap(); true } else disabled()
                    liveRegion = LiveRegionMode.Polite
                }
                .onKeyEvent { event ->
                    if (event.key in listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionCenter)) {
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) model.tap()
                        true
                    } else false
                }.focusable().padding(20.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stageLabel, color = ink, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    if (state.stage in setOf(ReactionStage.RESULT, ReactionStage.COMPLETE)) state.samples.lastOrNull()?.let {
                        Text(ms(it.toDouble()), color = ink, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.displaySmall)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!active && state.stage != ReactionStage.COMPLETE) ToolboxButton(onClick = {
                    scope.launch { targetView.bringIntoView(); model.beginRound() }
                }, enabled = !state.busy && state.storageError == null) {
                    Text(stringResource(if (state.stage == ReactionStage.IDLE) R.string.reaction_start else R.string.reaction_next))
                }
                if (state.stage != ReactionStage.IDLE) ToolboxOutlinedButton(onClick = { model.interruptRound(); confirmReset = true }, enabled = !state.busy && state.storageError != ReactionStorageError.SAVE) {
                    Text(stringResource(R.string.reaction_restart))
                }
            }
            if (state.busy) ToolboxLoadingIndicator(compact = true)
            state.storageError?.let { error ->
                Text(stringResource(when (error) {
                    ReactionStorageError.LOAD -> R.string.reaction_load_error
                    ReactionStorageError.SAVE -> R.string.reaction_save_error
                    ReactionStorageError.CLEAR -> R.string.reaction_clear_error
                }), color = MaterialTheme.colorScheme.error)
                ToolboxTextButton(onClick = model::retry, enabled = !state.busy) { Text(stringResource(R.string.reaction_retry)) }
                if (error == ReactionStorageError.LOAD) ToolboxTextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.reaction_clear)) }
            }
            Text(stringResource(R.string.reaction_failures, state.early, state.missed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!active) {
                ToolboxDivider()
                ToolSectionHeader(stringResource(R.string.reaction_statistics))
                if (stats == null) Text(stringResource(R.string.reaction_no_samples), color = MaterialTheme.colorScheme.onSurfaceVariant)
                rows.forEach { (label, value) -> ToolResultRow(label, value) }
                state.samples.forEachIndexed { index, value -> ToolResultRow(stringResource(R.string.reaction_round, index + 1), ms(value.toDouble())) }
                if (stats != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolboxOutlinedButton(onClick = { model.selectCsv(false) }, enabled = state.csv) { Text(stringResource(R.string.reaction_summary)) }
                        ToolboxOutlinedButton(onClick = { model.selectCsv(true) }, enabled = !state.csv) { Text(stringResource(R.string.reaction_csv)) }
                    }
                    SystemReportActions(report, false, {}, showRefresh = false)
                }
                ToolboxDivider()
                ToolSectionHeader(stringResource(R.string.reaction_history))
                Text(stringResource(R.string.reaction_history_note, ReactionPolicy.HISTORY_LIMIT), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.history.isEmpty()) Text(stringResource(R.string.reaction_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.history.forEach { record ->
                    val average = ReactionPolicy.statistics(record.samples)!!
                    val date = remember(record.completedAt) { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(record.completedAt)) }
                    ToolResultRow(date, stringResource(R.string.reaction_history_value, record.samples.size, ms(average.mean), ms(average.best.toDouble())))
                }
                if (state.history.isNotEmpty()) ToolboxTextButton(onClick = { confirmClear = true }, enabled = !state.busy && state.storageError != ReactionStorageError.SAVE) { Text(stringResource(R.string.reaction_clear)) }
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(ToolboxDimens.pageBottom))
        }
    }
    if (confirmReset || confirmClear) AlertDialog(onDismissRequest = { confirmReset = false; confirmClear = false },
        title = { Text(stringResource(if (confirmClear) R.string.reaction_clear else R.string.reaction_restart)) },
        text = { Text(stringResource(if (confirmClear) R.string.reaction_clear_confirm else R.string.reaction_restart_confirm)) },
        confirmButton = { ToolboxTextButton(onClick = {
            if (confirmClear) model.clearHistory() else model.newSession()
            confirmReset = false; confirmClear = false
        }) { Text(stringResource(R.string.reaction_confirm)) } },
        dismissButton = { ToolboxTextButton(onClick = { confirmReset = false; confirmClear = false }) { Text(stringResource(R.string.reaction_cancel)) } })
}

@Composable
private fun ms(value: Double) = stringResource(R.string.reaction_ms, String.format(LocalConfiguration.current.locales[0], "%.1f", value))
