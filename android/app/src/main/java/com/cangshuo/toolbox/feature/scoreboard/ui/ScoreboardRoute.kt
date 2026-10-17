package com.cangshuo.toolbox.feature.scoreboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ScoreboardRoute(factory: ViewModelProvider.Factory) {
    val model: ScoreboardViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(model) {
        model.start()
        onStopOrDispose { model.stop() }
    }
    val view = LocalView.current
    DisposableEffect(state.keepScreenOn) {
        view.keepScreenOn = state.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    ScoreboardScreen(
        state = state,
        onIncrement = model::increment,
        onDecrement = model::decrement,
        onSetScore = model::setScore,
        onEditNames = model::editNames,
        onUndo = model::undo,
        onSwap = model::swap,
        onNextPeriod = model::nextPeriod,
        onToggleTimer = model::toggleTimer,
        onResetTimer = model::resetTimer,
        onReset = model::reset,
        onStep = model::setStep,
        onKeepScreenOn = model::setKeepScreenOn,
        onShowAllHistory = model::showAllHistory,
    )
}
