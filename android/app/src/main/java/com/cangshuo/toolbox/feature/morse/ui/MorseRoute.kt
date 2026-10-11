package com.cangshuo.toolbox.feature.morse.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MorseRoute(factory: ViewModelProvider.Factory) {
    val viewModel: MorseViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MorseScreen(
        state = uiState,
        onInputChanged = viewModel::updateInput,
        onDirectionSelected = viewModel::selectDirection,
        onWpmChanged = viewModel::setWpm,
        onPlayToggle = viewModel::play,
        onSwap = viewModel::swap,
        onClear = viewModel::clear,
    )
}
