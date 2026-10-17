package com.cangshuo.toolbox.feature.decisionwheel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cangshuo.toolbox.feature.decisionwheel.domain.DecisionWheelPolicy
import com.cangshuo.toolbox.feature.decisionwheel.domain.WheelOption
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private data class WheelLabel(
    val text: androidx.compose.ui.text.TextLayoutResult,
    val position: Offset,
)

internal fun decisionWheelSliceColor(index: Int, scheme: ColorScheme): Color {
    val tints = listOf(scheme.primary, scheme.secondary, scheme.tertiary)
    return tints[index % tints.size].copy(alpha = 0.10f + (index % 4) * 0.035f)
}

internal fun wheelSegmentLabel(index: Int, option: WheelOption, sweep: Float): String =
    if (sweep < 18f) (index + 1).toString() else option.label

@Composable
fun DecisionWheelCanvas(
    options: List<WheelOption>,
    rotation: Float,
    selectedIndex: Int?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = DecisionWheelPolicy.MAX_OPTIONS)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val textStyle = TextStyle(
        color = scheme.onSurface,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val labels = remember(options, canvasSize, textStyle) {
        if (canvasSize == IntSize.Zero || options.isEmpty()) return@remember emptyList()
        val side = min(canvasSize.width, canvasSize.height).toFloat()
        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        val radius = side / 2f - with(density) { 8.dp.toPx() }
        val total = options.sumOf { it.weight.coerceIn(DecisionWheelPolicy.MIN_WEIGHT, DecisionWheelPolicy.MAX_WEIGHT) }
            .coerceAtLeast(1)
        var before = 0
        options.mapIndexed { index, option ->
            val weight = option.weight.coerceIn(DecisionWheelPolicy.MIN_WEIGHT, DecisionWheelPolicy.MAX_WEIGHT)
            val sweep = 360f * weight / total
            val middle = before * 360f / total + sweep / 2f
            before += weight
            val radians = Math.toRadians(middle.toDouble() - 90.0)
            val narrow = sweep < 18f
            val angleWidth = 2f * Math.PI.toFloat() * radius * 0.64f * (sweep / 360f)
            val minimumTextWidth = with(density) { (if (narrow) 28.dp else 8.dp).toPx().toInt() }
            val maxTextWidth = (angleWidth * 0.78f).toInt()
                .coerceIn(minimumTextWidth, (radius * 1.25f).toInt().coerceAtLeast(minimumTextWidth))
            val layout = textMeasurer.measure(
                text = AnnotatedString(wheelSegmentLabel(index, option, sweep)),
                style = if (narrow) textStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold) else textStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxTextWidth),
            )
            WheelLabel(
                text = layout,
                position = Offset(
                    x = center.x + radius * 0.62f * cos(radians).toFloat() - layout.size.width / 2f,
                    y = center.y + radius * 0.62f * sin(radians).toFloat() - layout.size.height / 2f,
                ),
            )
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it },
    ) {
        if (options.isEmpty()) return@Canvas
        val outer = min(size.width, size.height)
        val radius = outer / 2f - 8.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val total = options.sumOf { it.weight.coerceIn(DecisionWheelPolicy.MIN_WEIGHT, DecisionWheelPolicy.MAX_WEIGHT) }
            .coerceAtLeast(1)
        drawCircle(color = scheme.surfaceContainerHigh, radius = radius, center = center)
        rotate(degrees = rotation, pivot = center) {
            var start = -90f
            options.forEachIndexed { index, option ->
                val weight = option.weight.coerceIn(DecisionWheelPolicy.MIN_WEIGHT, DecisionWheelPolicy.MAX_WEIGHT)
                val sweep = 360f * weight / total
                val selected = index == selectedIndex
                drawArc(
                    color = if (selected) scheme.primary.copy(alpha = 0.34f)
                    else decisionWheelSliceColor(index, scheme),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                )
                if (selected) {
                    drawArc(
                        color = scheme.primary,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                val dividerRadians = Math.toRadians(start.toDouble())
                drawLine(
                    color = scheme.outlineVariant,
                    start = center,
                    end = Offset(
                        center.x + radius * cos(dividerRadians).toFloat(),
                        center.y + radius * sin(dividerRadians).toFloat(),
                    ),
                    strokeWidth = 1.dp.toPx(),
                )
                start += sweep
            }
            labels.forEach { label -> drawText(label.text, topLeft = label.position) }
        }
        drawCircle(
            color = scheme.outlineVariant,
            radius = radius,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )

        val pointer = Path().apply {
            moveTo(center.x - 11.dp.toPx(), 0f)
            lineTo(center.x + 11.dp.toPx(), 0f)
            lineTo(center.x, 18.dp.toPx())
            close()
        }
        drawPath(pointer, color = scheme.primary)
    }
}
