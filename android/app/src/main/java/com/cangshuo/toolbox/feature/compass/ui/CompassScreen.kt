package com.cangshuo.toolbox.feature.compass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.*
import com.cangshuo.toolbox.feature.compass.domain.CompassAngles
import java.util.Locale

@Composable
fun CompassRoute(factory: ViewModelProvider.Factory) {
    val model: CompassViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) { model.start(); onStopOrDispose { model.stop() } }
    val note = stringResource(R.string.compass_note)
    val reading = state.reading
    val heading = reading?.heading
    val unknown = stringResource(R.string.device_info_unknown)
    val directions = listOf(R.string.compass_n, R.string.compass_ne, R.string.compass_e, R.string.compass_se,
        R.string.compass_s, R.string.compass_sw, R.string.compass_w, R.string.compass_nw)
    val rows = reading?.let { listOf(
        stringResource(R.string.compass_heading) to (heading?.let { String.format(Locale.ROOT,"%.1f°",it) } ?: unknown),
        stringResource(R.string.compass_direction) to (heading?.let(CompassAngles::sector)?.let { stringResource(directions[it]) } ?: unknown),
        stringResource(R.string.sensors_accuracy) to stringResource(when(it.accuracy) {
            3 -> R.string.sensors_accuracy_high; 2 -> R.string.sensors_accuracy_medium; 1 -> R.string.sensors_accuracy_low
            0 -> R.string.sensors_accuracy_unreliable; else -> R.string.device_info_unknown
        }),
        stringResource(R.string.compass_error_angle) to (it.angularError?.let { value -> String.format(Locale.ROOT,"±%.1f°",value) } ?: unknown),
        stringResource(R.string.compass_source) to stringResource(if (it.rotationVector) R.string.compass_vector else R.string.compass_fallback)
    ) }.orEmpty()
    val report = rows.takeIf { it.isNotEmpty() }?.joinToString("\n", postfix = "\n\n$note") { "${it.first}: ${it.second}" }
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SystemReportActions(report, false, model::refresh)
        OutlinedButton(onClick = model::togglePause) { Text(stringResource(if (state.paused) R.string.sensors_resume else R.string.sensors_pause)) }
        if (state.waiting && reading == null) ToolboxLoadingState(stringResource(R.string.compass_loading))
        if (state.unsupported) Text(stringResource(R.string.compass_unsupported))
        else if (state.failed) Text(stringResource(R.string.compass_error), color = MaterialTheme.colorScheme.error)
        else if (reading?.needsFlat == true) Text(stringResource(R.string.compass_flat))
        else if (!state.waiting && heading == null) Text(stringResource(R.string.compass_waiting))
        if (reading != null && reading.accuracy != 3) Text(stringResource(R.string.compass_calibration))
        heading?.let { degrees ->
            Canvas(Modifier.fillMaxWidth().height(240.dp)) {
                val center = Offset(size.width/2, size.height/2)
                val radius = minOf(size.width, size.height)*.42f
                drawCircle(outline, radius, center, style = Stroke(2.dp.toPx()))
                for (tick in 0 until 24) rotate(tick*15f, center) {
                    drawLine(ink, Offset(center.x, center.y-radius), Offset(center.x, center.y-radius+(if (tick%6==0) 16 else 8).dp.toPx()), 2.dp.toPx())
                }
                rotate(-degrees.toFloat(), center) {
                    drawLine(primary, center, Offset(center.x, center.y-radius*.8f), 6.dp.toPx())
                    drawLine(outline, center, Offset(center.x, center.y+radius*.65f), 4.dp.toPx())
                }
                drawCircle(primary, 6.dp.toPx(), center)
            }
        }
        if (rows.isNotEmpty()) SystemReportCard(stringResource(R.string.compass_name), rows)
    }
}
