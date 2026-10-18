package com.cangshuo.toolbox.feature.timer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.awaitCancellation

@Composable
fun TimerRoute(factory: ViewModelProvider.Factory) {
    val model: TimerViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(model) {
        onDispose {
            model.leaveForeground()
            model.consumeMessage()
        }
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            model.enterForeground()
            try {
                awaitCancellation()
            } finally {
                model.leaveForeground()
            }
        }
    }
    TimerScreen(
        state = state,
        onHours = model::setHours,
        onMinutes = model::setMinutes,
        onSeconds = model::setSeconds,
        onLabel = model::setLabel,
        onPreset = model::choosePreset,
        onStart = model::start,
        onPause = model::pause,
        onResume = model::resume,
        onReset = model::reset,
        onExtend = model::extend,
        onKeepScreen = model::setKeepScreenOn,
        onRefreshService = model::refreshNotification,
        onClearError = model::clearError,
        onMessageConsumed = model::consumeMessage,
    )
}
