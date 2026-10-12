package com.cangshuo.toolbox.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Theme-only coin with finite motion and readable, unmirrored faces. No random generation here. */
@Composable
fun ToolboxFlipCoin(
    frontText: String,
    backText: String,
    onBack: Boolean,
    revealing: Boolean,
    flipId: Long,
    animate: Boolean,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onSettled: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(if (onBack) 180f else 0f) }
    val settle by rememberUpdatedState(onSettled)
    LaunchedEffect(revealing, flipId, onBack, animate) {
        val target = if (onBack) 180f else 0f
        if (revealing && animate) {
            rotation.animateTo(720f + target, tween(ToolboxMotion.COIN_FLIP_DURATION_MS, easing = FastOutSlowInEasing))
        }
        rotation.snapTo(target)
        if (revealing) settle(flipId)
    }
    val backVisible = rotation.value % 360f in 90f..270f
    Box(modifier.size(156.dp).semantics(mergeDescendants = true) {
        contentDescription = description
    }.clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationY = rotation.value; cameraDistance = 18 * density }
            .clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Text(if (backVisible) backText else frontText,
                Modifier.padding(20.dp).graphicsLayer { rotationY = if (backVisible) 180f else 0f }.clearAndSetSemantics { },
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
