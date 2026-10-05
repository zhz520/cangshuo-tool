package com.cangshuo.toolbox.feature.level.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.*
import com.cangshuo.toolbox.feature.level.domain.*
import java.util.Locale
import kotlin.math.hypot

@Composable
fun LevelRoute(factory: ViewModelProvider.Factory) {
    val model: LevelViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) { model.start();onStopOrDispose { model.stop() } }
    val note = stringResource(R.string.level_note)
    val reading = state.reading
    val angles = reading?.let { LevelMath.relative(it.angles, state.reference) }
    val rows = angles?.let { a -> listOf(
        stringResource(R.string.level_x) to String.format(Locale.ROOT,"%.2f°",a.x),
        stringResource(R.string.level_y) to String.format(Locale.ROOT,"%.2f°",a.y),
        stringResource(R.string.level_tilt) to String.format(Locale.ROOT,"%.2f°",a.tilt),
        stringResource(R.string.level_reference) to stringResource(if (state.reference == null) R.string.level_absolute else R.string.level_relative),
        stringResource(R.string.compass_source) to stringResource(if (reading?.accelerationFallback == true) R.string.level_accelerometer else R.string.level_gravity)
    ) }.orEmpty()
    val report = rows.takeIf { it.isNotEmpty() }?.joinToString("\n", postfix = "\n\n$note") { "${it.first}: ${it.second}" }
    val outline = MaterialTheme.colorScheme.outlineVariant
    val bubble = if (angles != null && LevelMath.nearZero(angles)) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(note,style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        SystemReportActions(report,false,model::refresh)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = model::togglePause) { Text(stringResource(if (state.paused) R.string.sensors_resume else R.string.sensors_pause)) }
            OutlinedButton(onClick = model::zero,enabled = reading?.angles?.faceUp == true) { Text(stringResource(R.string.level_zero)) }
            OutlinedButton(onClick = model::reset,enabled = state.reference != null) { Text(stringResource(R.string.level_reset)) }
        }
        if (state.waiting) ToolboxLoadingState(stringResource(R.string.level_loading))
        if (state.unsupported) Text(stringResource(R.string.level_unsupported))
        else if (state.failed) Text(stringResource(R.string.level_error),color = MaterialTheme.colorScheme.error)
        else if (!state.waiting && (angles == null || !angles.faceUp)) Text(stringResource(R.string.level_unsteady))
        if (reading != null && reading.accuracy != 3) Text(stringResource(R.string.level_accuracy))
        angles?.takeIf { it.faceUp }?.let { a ->
            Text(stringResource(if (LevelMath.nearZero(a)) R.string.level_near_zero else R.string.level_tilted))
            Canvas(Modifier.fillMaxWidth().height(240.dp)) {
                val center = Offset(size.width/2,size.height/2)
                val radius = minOf(size.width,size.height)*.42f
                drawCircle(outline,radius,center,style = Stroke(2.dp.toPx()))
                drawCircle(outline,radius*.15f,center,style = Stroke(2.dp.toPx()))
                drawLine(outline,Offset(center.x-radius,center.y),Offset(center.x+radius,center.y),1.dp.toPx())
                drawLine(outline,Offset(center.x,center.y-radius),Offset(center.x,center.y+radius),1.dp.toPx())
                val x = a.x/10;val y = -a.y/10;val scale = maxOf(1.0,hypot(x,y))
                drawCircle(bubble,radius*.12f,Offset(center.x+(x/scale*radius*.85).toFloat(),center.y+(y/scale*radius*.85).toFloat()))
            }
        }
        if (rows.isNotEmpty()) SystemReportCard(stringResource(R.string.level_name),rows)
    }
}
