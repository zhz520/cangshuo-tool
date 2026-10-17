package com.cangshuo.toolbox.feature.decisionwheel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DecisionWheelRoute(factory: ViewModelProvider.Factory) {
    val model: DecisionWheelViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    DecisionWheelScreen(
        state = state,
        onSpin = model::spin,
        onSettled = model::complete,
        onRotationChanged = model::setRotation,
        onPreset = model::applyPreset,
        onResetOptions = model::resetOptions,
        onSaveOption = model::saveOption,
        onRemoveOption = model::removeOption,
        onAvoidRepeat = model::setAvoidRepeat,
        onHaptics = model::setHaptics,
        onClearEditError = model::clearEditError,
        onClearHistory = model::clearHistory,
        onDismissResult = model::dismissResult,
    )
}
