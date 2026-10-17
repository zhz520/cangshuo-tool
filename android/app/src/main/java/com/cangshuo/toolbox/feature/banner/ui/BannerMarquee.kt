package com.cangshuo.toolbox.feature.banner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cangshuo.toolbox.feature.banner.domain.BannerDirection
import com.cangshuo.toolbox.feature.banner.domain.BannerPolicy
import kotlinx.coroutines.isActive

@Composable
fun BannerMarquee(
    text: String,
    speedDpPerSecond: Float,
    fontSizeSp: Float,
    direction: BannerDirection,
    mirrored: Boolean,
    running: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    contentDescription: String = text,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 8)
    val style = remember(fontSizeSp, color) {
        TextStyle(
            color = color,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
    }
    val measured = remember(text, style) {
        textMeasurer.measure(
            text = AnnotatedString(text),
            style = style,
            maxLines = 1,
            softWrap = false,
        )
    }
    val textWidth = measured.size.width.toFloat()
    val gapPx = with(density) { BannerPolicy.GAP_DP.dp.toPx() }
    val cycleWidth = textWidth + gapPx
    val speedPxPerSecond = with(density) { speedDpPerSecond.dp.toPx() }
    var travel by remember(text, direction) { mutableFloatStateOf(0f) }

    LaunchedEffect(running, speedPxPerSecond, cycleWidth) {
        if (!running || cycleWidth <= 0f || speedPxPerSecond <= 0f) return@LaunchedEffect
        var previous = 0L
        while (isActive) {
            withFrameNanos { frame ->
                if (previous != 0L) {
                    // A resumed activity can report a long frame gap; cap a single step so the
                    // banner does not jump across the viewport after spending time in background.
                    val seconds = ((frame - previous) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    travel = (travel + speedPxPerSecond * seconds) % cycleWidth
                }
                previous = frame
            }
        }
    }

    Canvas(
        modifier = modifier
            .graphicsLayer { scaleX = if (mirrored) -1f else 1f }
            .semantics { this.contentDescription = contentDescription },
    ) {
        if (text.isEmpty() || size.width <= 0f || size.height <= 0f || cycleWidth <= 0f) return@Canvas
        val startX = BannerPolicy.motionStartX(
            progressPx = travel,
            cycleWidth = cycleWidth,
            textWidth = textWidth,
            viewportWidth = size.width,
            direction = direction,
        )
        val top = (size.height - measured.size.height) / 2f
        BannerPolicy.visibleCopyPositions(startX, cycleWidth, textWidth, size.width).forEach { x ->
            drawText(measured, topLeft = Offset(x, top))
        }
    }
}
