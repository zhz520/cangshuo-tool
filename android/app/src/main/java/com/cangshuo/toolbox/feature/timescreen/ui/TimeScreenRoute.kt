package com.cangshuo.toolbox.feature.timescreen.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TimeScreenRoute(factory: ViewModelProvider.Factory) {
    val model: TimeScreenViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(model) {
        model.start()
        onStopOrDispose { model.stop() }
    }
    TimeScreenScreen(
        state = state,
        onUse24Hour = model::setUse24Hour,
        onShowSeconds = model::setShowSeconds,
        onShowDate = model::setShowDate,
        onShowZone = model::setShowZone,
        onAntiBurnIn = model::setAntiBurnIn,
        onDriftRadius = model::setDriftRadius,
        onKeepScreenOn = model::setKeepScreenOn,
        onReset = model::reset,
    )
}
