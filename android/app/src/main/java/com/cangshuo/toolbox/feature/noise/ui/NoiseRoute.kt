package com.cangshuo.toolbox.feature.noise.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun NoiseRoute(factory: ViewModelProvider.Factory) {
    val model: NoiseViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    NoiseScreen(
        state = state,
        onToggleLayer = model::toggleLayer,
        onLevel = model::setLevel,
        onMaster = model::setMaster,
        onTimer = model::setTimer,
        onPreset = model::applyPreset,
        onStart = model::start,
        onPause = model::pause,
        onResume = model::resume,
        onStop = model::stop,
        onRefreshPlayback = model::refreshPlayback,
    )
}
