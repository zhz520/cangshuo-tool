package com.cangshuo.toolbox.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.ui.theme.ToolboxDimens

/** Consume system bars and cutouts once; descendants handle only the remaining IME inset. */
@Composable
fun ToolboxScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ToolboxTopBar(
                title = title,
                navigationIcon = {
                    if (onBack != null) {
                        ToolboxIconButton(R.drawable.ic_back, stringResource(R.string.action_back), onBack)
                    } else navigationIcon()
                },
                actions = actions,
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            content(Modifier.widthIn(max = ToolboxDimens.contentMaxWidth).fillMaxSize())
        }
    }
}
