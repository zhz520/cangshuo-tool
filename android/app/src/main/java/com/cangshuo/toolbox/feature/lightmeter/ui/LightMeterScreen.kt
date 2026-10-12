package com.cangshuo.toolbox.feature.lightmeter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.*
import com.cangshuo.toolbox.feature.lightmeter.domain.*
import com.cangshuo.toolbox.ui.theme.ToolboxDimens

@Composable
fun LightMeterRoute(factory: ViewModelProvider.Factory) {
    val model: LightMeterViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(model) { model.start(); onStopOrDispose { model.stop() } }
    val unknown = stringResource(R.string.device_info_unknown)
    val locale = LocalConfiguration.current.locales[0]
    val unitLabel = stringResource(if (state.unit == LightUnit.LUX) R.string.light_unit_lux else R.string.light_unit_fc)
    fun value(raw: Double?): String = raw?.let {
        String.format(locale, if (state.unit == LightUnit.LUX) "%.1f %s" else "%.2f %s",
            state.unit.convert(it * state.factor), unitLabel)
    } ?: unknown
    val statusText = stringResource(if (state.paused) R.string.light_paused else when (state.status) {
        LightStatus.IDLE -> R.string.light_idle
        LightStatus.WAITING -> R.string.light_waiting
        LightStatus.READY -> R.string.light_live
        LightStatus.UNSUPPORTED -> R.string.light_unsupported
        LightStatus.NO_READING -> R.string.light_no_reading
        LightStatus.FAILED -> R.string.light_error
    })
    val stats = state.session
    val rows = listOf(
        stringResource(R.string.light_current) to value(state.sample?.lux),
        stringResource(R.string.light_minimum) to value(stats.minimum),
        stringResource(R.string.light_maximum) to value(stats.maximum),
        stringResource(R.string.light_average) to value(stats.mean.takeIf { stats.count > 0 }),
        stringResource(R.string.light_samples) to stats.count.toString(),
    )
    val note = stringResource(R.string.light_note)
    val factorText = stringResource(R.string.light_factor, state.factor)
    val statusLabel = stringResource(R.string.light_status)
    val rawLabel = stringResource(R.string.light_raw)
    val sensorLabel = stringResource(R.string.light_sensor)
    val luxLabel = stringResource(R.string.light_unit_lux)
    val window = stats.window(state.windowSeconds)
    val report = if (state.csvReport) {
        window.takeIf { it.isNotEmpty() }?.let { LightPolicy.csv(it, state.factor) }
    } else state.sample?.let { sample ->
        buildString {
            append(rows.joinToString("\n") { "${it.first}: ${it.second}" })
            append("\n$statusLabel: $statusText\n$factorText")
            append("\n$rawLabel: ${String.format(locale, "%.1f", sample.lux)} $luxLabel")
            append("\n$sensorLabel: ${state.sensor?.name ?: unknown}\n\n$note")
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(ToolboxDimens.pageGutter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(statusText, style = MaterialTheme.typography.bodyMedium,
                color = if (state.status == LightStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.status == LightStatus.WAITING && !state.paused) ToolboxLoadingState(stringResource(R.string.light_waiting))
            if (state.sample != null) {
                Text(value(state.sample?.lux), style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace)
                Text(stringResource(R.string.light_band,
                    stringResource(when (LightPolicy.band(state.sample!!.lux * state.factor)) {
                        LightBand.DARK -> R.string.light_dark
                        LightBand.DIM -> R.string.light_dim
                        LightBand.INDOOR -> R.string.light_indoor
                        LightBand.BRIGHT -> R.string.light_bright
                        LightBand.VERY_BRIGHT -> R.string.light_very_bright
                    })), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LightOption(stringResource(R.string.light_unit_lux), state.unit == LightUnit.LUX) { model.selectUnit(LightUnit.LUX) }
                LightOption(stringResource(R.string.light_unit_fc), state.unit == LightUnit.FOOT_CANDLE) { model.selectUnit(LightUnit.FOOT_CANDLE) }
                ToolboxOutlinedButton(onClick = model::togglePause, enabled = state.status !in setOf(LightStatus.UNSUPPORTED, LightStatus.FAILED, LightStatus.NO_READING)) {
                    Text(stringResource(if (state.paused) R.string.sensors_resume else R.string.sensors_pause))
                }
                if (state.status in setOf(LightStatus.UNSUPPORTED, LightStatus.FAILED, LightStatus.NO_READING)) {
                    ToolboxOutlinedButton(onClick = model::retry) { Text(stringResource(R.string.device_info_refresh)) }
                }
            }
            if (state.invalidReading) Text(stringResource(R.string.light_invalid), color = MaterialTheme.colorScheme.error)
            val sensor = state.sensor
            if (sensor != null && sensor.range.isFinite() && sensor.range > 0 && state.sample?.lux?.let { it >= sensor.range } == true) {
                Text(stringResource(R.string.light_saturated), color = MaterialTheme.colorScheme.error)
            }
            ToolboxDivider()
            ToolSectionHeader(stringResource(R.string.light_statistics))
            rows.drop(1).forEach { (label, number) -> ToolResultRow(label, number) }
            Text(stringResource(R.string.light_average_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ToolboxTextButton(onClick = model::clearSession, enabled = stats.count > 0) { Text(stringResource(R.string.light_clear)) }
            ToolboxDivider()
            ToolSectionHeader(stringResource(R.string.light_trend))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LightPolicy.WINDOWS.forEach { seconds ->
                    LightOption(stringResource(R.string.light_window, seconds), state.windowSeconds == seconds) { model.selectWindow(seconds) }
                }
            }
            if (window.isEmpty()) Text(stringResource(R.string.light_trend_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                val min = value(window.minOf { it.lux })
                val max = value(window.maxOf { it.lux })
                Text(stringResource(R.string.light_trend_range, min, max), style = MaterialTheme.typography.bodySmall)
                val start = window.first().timestampNanos
                ToolboxTrendChart(window.map { ToolTrendPoint((it.timestampNanos - start) / 1e9, state.unit.convert(it.lux * state.factor)) },
                    stringResource(R.string.light_trend_accessibility, window.size, min, max), Modifier.fillMaxWidth())
            }
            Text(stringResource(R.string.light_trend_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ToolboxDivider()
            ToolSectionHeader(stringResource(R.string.light_calibration))
            Text(stringResource(R.string.light_calibration_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(factorText, style = MaterialTheme.typography.bodyMedium)
            state.sample?.let { sample ->
                ToolResultRow(rawLabel, "${String.format(locale, "%.1f", sample.lux)} $luxLabel")
            }
            ToolboxTextField(state.reference, model::updateReference, Modifier.fillMaxWidth(),
                label = stringResource(R.string.light_reference), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.calibrationError != null)
            state.calibrationError?.let { error ->
                Text(stringResource(when (error) {
                    LightCalibrationError.INVALID_REFERENCE -> R.string.light_reference_error
                    LightCalibrationError.NO_READING -> R.string.light_calibration_no_reading
                    LightCalibrationError.FACTOR_OUT_OF_RANGE -> R.string.light_factor_error
                }), color = MaterialTheme.colorScheme.error)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolboxOutlinedButton(onClick = model::calibrate, enabled = state.reference.isNotBlank() && state.status == LightStatus.READY && !state.invalidReading) {
                    Text(stringResource(R.string.light_apply_calibration))
                }
                ToolboxTextButton(onClick = model::resetCalibration, enabled = state.factor != 1.0) { Text(stringResource(R.string.light_reset_calibration)) }
            }
            sensor?.let {
                ToolboxDivider()
                ToolSectionHeader(stringResource(R.string.light_sensor))
                ToolResultRow(stringResource(R.string.light_sensor_name), it.name)
                ToolResultRow(stringResource(R.string.sensors_vendor), it.vendor.ifBlank { unknown })
                ToolResultRow(stringResource(R.string.sensors_range), "${it.range} $luxLabel")
                ToolResultRow(stringResource(R.string.sensors_resolution), "${it.resolution} $luxLabel")
            }
            ToolboxDivider()
            ToolSectionHeader(stringResource(R.string.light_report))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LightOption(stringResource(R.string.light_summary), !state.csvReport) { model.selectReport(false) }
                LightOption(stringResource(R.string.light_csv), state.csvReport) { model.selectReport(true) }
            }
            SystemReportActions(report, false, model::retry, showRefresh = false)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(ToolboxDimens.pageBottom))
        }
    }
}

@Composable
private fun LightOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.semantics { this.selected = selected }
    if (selected) ToolboxButton(onClick = onClick, modifier = modifier) { Text(label) }
    else ToolboxOutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
}
