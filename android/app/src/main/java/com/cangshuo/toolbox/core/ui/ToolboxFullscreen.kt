package com.cangshuo.toolbox.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Hide system bars for a full-screen Compose dialog and restore them when it leaves composition. */
@Composable
fun ToolboxFullscreenWindowEffect(keepScreenOn: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        val window = (view.parent as? DialogWindowProvider)?.window
            ?: context.findActivity()?.window
        val controller = window?.let { target -> WindowCompat.getInsetsController(target, view) }
        val previousBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (previousBehavior != null) controller.systemBarsBehavior = previousBehavior
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
