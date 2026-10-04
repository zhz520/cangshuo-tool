package com.cangshuo.toolbox.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

/** Shared native progress animation. The owner supplies localized status text/semantics. */
@Composable
fun ToolboxLoadingIndicator(modifier: Modifier = Modifier, compact: Boolean = false) {
    CircularProgressIndicator(
        modifier = modifier.size(if (compact) 20.dp else 40.dp),
        color = MaterialTheme.colorScheme.primary,
        strokeWidth = if (compact) 2.dp else 3.dp,
        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    )
}

/** An inline content state: navigation and other available actions remain usable. */
@Composable
fun ToolboxLoadingState(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.heightIn(min = 184.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(64.dp).background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) { ToolboxLoadingIndicator() }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(name = "Loading Chinese", locale = "zh", widthDp = 360, showBackground = true)
@Preview(name = "Loading English", locale = "en", widthDp = 360, showBackground = true)
@Composable
private fun LoadingPreview() {
    ToolboxTheme(darkTheme = false) {
        ToolboxLoadingState(stringResource(R.string.home_loading), Modifier.padding(16.dp))
    }
}

@Preview(name = "Loading dark", locale = "zh", widthDp = 360, showBackground = true)
@Composable
private fun DarkLoadingPreview() {
    ToolboxTheme(darkTheme = true) {
        ToolboxLoadingState(stringResource(R.string.favorites_loading), Modifier.padding(16.dp))
    }
}
