package com.cangshuo.toolbox.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class ToolTrendPoint(val time: Double, val value: Double)

/** Bounded scalar trend with proportional timestamps and a caller-provided accessible summary. */
@Composable
fun ToolboxTrendChart(points: List<ToolTrendPoint>, description: String, modifier: Modifier = Modifier) {
    val bounded = remember(points) { points.takeLast(600).filter { it.time.isFinite() && it.value.isFinite() } }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.height(180.dp).semantics { contentDescription = description }) {
        val padding = 8.dp.toPx()
        val width = (size.width - padding * 2).coerceAtLeast(1f)
        val height = (size.height - padding * 2).coerceAtLeast(1f)
        for (index in 0..2) {
            val y = padding + height * index / 2
            drawLine(grid, Offset(padding, y), Offset(padding + width, y), 1.dp.toPx())
        }
        if (bounded.isNotEmpty()) {
            val low = bounded.minOf { it.value }
            val high = bounded.maxOf { it.value }
            val start = bounded.minOf { it.time }
            val duration = bounded.maxOf { it.time } - start
            fun location(point: ToolTrendPoint) = Offset(
                padding + (if (duration == 0.0) .5 else (point.time - start) / duration).toFloat() * width,
                padding + (if (high == low) .5 else 1.0 - (point.value - low) / (high - low)).toFloat() * height,
            )
            val path = Path()
            bounded.forEachIndexed { index, point ->
                val position = location(point)
                if (index == 0) path.moveTo(position.x, position.y) else path.lineTo(position.x, position.y)
            }
            drawPath(path, line, style = Stroke(2.dp.toPx()))
            drawCircle(line, 3.dp.toPx(), location(bounded.last()))
        }
    }
}
