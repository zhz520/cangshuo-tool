package com.cangshuo.toolbox.feature.radix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun RadixRoute(factory: ViewModelProvider.Factory) {
    val viewModel: RadixViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RadixScreen(
        state = uiState,
        onInputChanged = viewModel::updateInput,
        onRadixSelected = viewModel::selectRadix,
        onCustomModeEnabled = viewModel::enableCustomMode,
        onCustomRadixChanged = viewModel::setCustomRadix,
        onUppercaseToggled = viewModel::setUppercase,
        onClear = viewModel::clear,
    )
}
