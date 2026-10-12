package com.cangshuo.toolbox.feature.color.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ColorRoute(factory: ViewModelProvider.Factory) {
    val viewModel: ColorViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ColorScreen(
        state = uiState,
        onInputChanged = viewModel::updateInput,
        onClear = viewModel::clear,
    )
}
