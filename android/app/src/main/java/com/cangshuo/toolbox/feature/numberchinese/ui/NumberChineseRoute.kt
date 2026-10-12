package com.cangshuo.toolbox.feature.numberchinese.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun NumberChineseRoute(factory: ViewModelProvider.Factory) {
    val viewModel: NumberChineseViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NumberChineseScreen(
        state = uiState,
        onInputChanged = viewModel::updateInput,
        onStyleSelected = viewModel::selectStyle,
        onDirectionSelected = viewModel::selectDirection,
        onColloquialChanged = viewModel::toggleColloquialTen,
        onClear = viewModel::clear,
    )
}
