package com.cangshuo.toolbox.feature.vibrator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.*
import com.cangshuo.toolbox.feature.vibrator.domain.*
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun VibratorRoute(factory: ViewModelProvider.Factory) {
    val model: VibratorViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val pattern = state.pattern
    val cap = state.capability
    LifecycleResumeEffect(model) { model.foreground(); onPauseOrDispose { model.background() } }
    var clear by rememberSaveable { mutableStateOf(false) }
    val playing = state.phase == VibrationPhase.PLAYING
    val phase = stringResource(when (state.phase) {
        VibrationPhase.IDLE -> R.string.vibrator_ready; VibrationPhase.PLAYING -> R.string.vibrator_playing
        VibrationPhase.FINISHED -> R.string.vibrator_finished; VibrationPhase.CANCELLED -> R.string.vibrator_cancelled
        VibrationPhase.INTERRUPTED -> R.string.vibrator_interrupted
    })
    val historyLines = state.history.map { record ->
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(record.at))
        "$date · ${presetName(record.preset)} · ${record.actualDurationMs} ms"
    }
    val report = when {
        state.history.isEmpty() -> null
        state.csv -> VibrationPolicy.csv(state.history)
        else -> buildString {
            append(stringResource(R.string.vibrator_name)).append('\n')
            append(stringResource(R.string.vibrator_parameter, presetName(pattern.preset), pattern.durationMs,
                pattern.strengthPercent, state.waveform?.totalMs ?: 0)).append('\n')
            historyLines.forEach { append(it).append('\n') }
            append(stringResource(R.string.vibrator_note))
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(ToolboxDimens.pageGutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.vibrator_instructions), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (cap == null) ToolboxLoadingState(stringResource(R.string.vibrator_loading))
            else if (!cap.hasVibrator) {
                Text(stringResource(R.string.vibrator_no_motor), color = MaterialTheme.colorScheme.error)
            } else {
                Text(if (cap.amplitudeControl) stringResource(R.string.vibrator_capability_amplitude)
                    else stringResource(R.string.vibrator_capability_onoff), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    VibrationPreset.entries.forEach { preset ->
                        val label = presetName(preset)
                        val selected = preset == pattern.preset
                        if (selected) ToolboxButton(onClick = { model.selectPreset(preset) }, enabled = !state.busy && !playing) { Text(label) }
                        else ToolboxOutlinedButton(onClick = { model.selectPreset(preset) }, enabled = !state.busy && !playing) { Text(label) }
                    }
                }
                val configurable = pattern.preset in setOf(VibrationPreset.SINGLE, VibrationPreset.DOUBLE, VibrationPreset.TRIPLE)
                Text(stringResource(R.string.vibrator_pulse_duration, pattern.durationMs), style = MaterialTheme.typography.titleSmall)
                Slider(value = pattern.durationMs.toFloat(), onValueChange = { model.duration((it / 50).toInt() * 50) },
                    valueRange = VibrationPattern.MIN_DURATION.toFloat()..VibrationPattern.MAX_DURATION.toFloat(),
                    steps = 38, enabled = configurable && !state.busy && !playing)
                if (!configurable) Text(stringResource(R.string.vibrator_fixed_duration), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.vibrator_strength, pattern.strengthPercent), style = MaterialTheme.typography.titleSmall)
                Slider(value = pattern.strengthPercent.toFloat(), onValueChange = { model.strength((it / 5).toInt() * 5) },
                    valueRange = VibrationPattern.MIN_STRENGTH.toFloat()..VibrationPattern.MAX_STRENGTH.toFloat(),
                    steps = 18, enabled = cap.amplitudeControl && !state.busy && !playing)
                if (!cap.amplitudeControl) Text(stringResource(R.string.vibrator_no_amplitude), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.vibrator_waveform, presetName(pattern.preset), state.waveform?.totalMs ?: 0),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(phase, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (playing) ToolboxDangerButton(onClick = model::stop) { Text(stringResource(R.string.vibrator_stop)) }
                    else ToolboxButton(onClick = model::play, enabled = !state.busy && state.error != VibrationStorageError.SAVE) { Text(stringResource(R.string.vibrator_play)) }
                }
                if (state.hardwareError) Text(stringResource(R.string.vibrator_hardware_error), color = MaterialTheme.colorScheme.error)
                if (state.busy) ToolboxLoadingIndicator(compact = true)
            }
            state.error?.let { error ->
                Text(stringResource(when (error) { VibrationStorageError.LOAD -> R.string.vibrator_load_error
                    VibrationStorageError.SAVE -> R.string.vibrator_save_error; VibrationStorageError.CLEAR -> R.string.vibrator_clear_error }),
                    color = MaterialTheme.colorScheme.error)
                ToolboxTextButton(model::retry) { Text(stringResource(R.string.vibrator_retry)) }
            }
            ToolboxDivider()
            ToolSectionHeader(stringResource(R.string.vibrator_history))
            Text(stringResource(R.string.vibrator_history_note, VibrationPolicy.HISTORY_LIMIT), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.history.isEmpty()) Text(stringResource(R.string.vibrator_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.history.forEach { record ->
                val date = remember(record.at) { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(record.at)) }
                ToolListItem(title = "$date · ${presetName(record.preset)}", subtitle = stringResource(R.string.vibrator_history_value,
                    record.durationMs, record.strengthPercent, record.actualDurationMs, stringResource(if (record.amplitudeControlled)
                        R.string.vibrator_amplitude_yes else R.string.vibrator_amplitude_no)), subtitleMaxLines = 4)
            }
            if (state.history.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolboxOutlinedButton({ model.csv(false) }, enabled = state.csv) { Text(stringResource(R.string.vibrator_summary)) }
                    ToolboxOutlinedButton({ model.csv(true) }, enabled = !state.csv) { Text(stringResource(R.string.vibrator_csv)) }
                }
                SystemReportActions(report, false, {}, showRefresh = false)
                ToolboxTextButton({ clear = true }, enabled = !state.busy && state.phase != VibrationPhase.PLAYING && state.error != VibrationStorageError.SAVE) { Text(stringResource(R.string.vibrator_clear)) }
            }
            Text(stringResource(R.string.vibrator_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(ToolboxDimens.pageBottom))
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text(stringResource(R.string.vibrator_clear)) },
        text = { Text(stringResource(R.string.vibrator_clear_confirm)) }, confirmButton = {
            ToolboxTextButton({ model.clearHistory(); clear = false }) { Text(stringResource(R.string.vibrator_confirm)) }
        }, dismissButton = { ToolboxTextButton({ clear = false }) { Text(stringResource(R.string.vibrator_cancel)) } })
}

@Composable
private fun presetName(preset: VibrationPreset): String = stringResource(when (preset) {
    VibrationPreset.SINGLE -> R.string.vibrator_single; VibrationPreset.DOUBLE -> R.string.vibrator_double
    VibrationPreset.TRIPLE -> R.string.vibrator_triple; VibrationPreset.HEARTBEAT -> R.string.vibrator_heartbeat
    VibrationPreset.ALARM -> R.string.vibrator_alarm; VibrationPreset.RAMP -> R.string.vibrator_ramp
    VibrationPreset.WAVE -> R.string.vibrator_wave
})
