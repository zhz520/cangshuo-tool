package com.cangshuo.toolbox.feature.timescreen.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cangshuo.toolbox.feature.timescreen.domain.TimeScreenSettings
import com.cangshuo.toolbox.feature.timescreen.domain.TimeScreenSnapshot
import kotlin.math.min

@Composable
fun TimeDisplay(
    settings: TimeScreenSettings,
    snapshot: TimeScreenSnapshot,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    driftX: Float = 0f,
    driftY: Float = 0f,
) {
    val density = LocalDensity.current
    val animatedX by animateFloatAsState(
        targetValue = if (settings.antiBurnIn) driftX else 0f,
        animationSpec = tween(800),
        label = "clock drift x",
    )
    val animatedY by animateFloatAsState(
        targetValue = if (settings.antiBurnIn) driftY else 0f,
        animationSpec = tween(800),
        label = "clock drift y",
    )
    BoxWithConstraints(
        modifier = modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val fontScale = density.fontScale
        val characterSlots = snapshot.clock.main.length.coerceAtLeast(1) +
            if (snapshot.clock.period == null) 0f else 0.8f
        val byWidth = maxWidth.value / (characterSlots * 0.61f)
        val metaFraction = if (settings.showDate || settings.showZone) 0.43f else 0.56f
        val byHeight = maxHeight.value * metaFraction
        val mainSize = (min(byWidth, byHeight) / fontScale).coerceIn(18f, 180f)
        val periodSize = (mainSize * 0.24f).coerceIn(12f, 34f)
        Column(
            modifier = Modifier.graphicsLayer {
                translationX = with(density) { animatedX.dp.toPx() }
                translationY = with(density) { animatedY.dp.toPx() }
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = snapshot.clock.main,
                    color = color,
                    style = MaterialTheme.typography.displayLarge,
                    fontSize = mainSize.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.03).em,
                    maxLines = 1,
                    softWrap = false,
                )
                snapshot.clock.period?.let { period ->
                    Text(
                        text = period,
                        modifier = Modifier.padding(start = 8.dp, bottom = mainSize.dp * 0.12f),
                        color = color.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = periodSize.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            if (settings.showDate || settings.showZone) {
                Spacer(Modifier.height(8.dp))
            }
            if (settings.showDate) {
                Text(
                    text = snapshot.date,
                    color = color.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (settings.showZone) {
                Text(
                    text = snapshot.zone,
                    color = color.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
