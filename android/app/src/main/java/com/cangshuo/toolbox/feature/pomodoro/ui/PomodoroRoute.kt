package com.cangshuo.toolbox.feature.pomodoro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PomodoroRoute(factory: ViewModelProvider.Factory) {
    val model: PomodoroViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(model) {
        model.start()
        onStopOrDispose { model.stop() }
    }
    PomodoroScreen(
        state = state,
        onToggle = model::toggleTimer,
        onSkip = model::skip,
        onReset = model::reset,
        onSelectPhase = model::selectPhase,
        onSaveLabel = model::setLabel,
        onSaveSettings = model::saveSettings,
        onClearHistory = model::clearHistory,
        onClearError = model::clearError,
        onMessageConsumed = model::consumeMessage,
    )
}
