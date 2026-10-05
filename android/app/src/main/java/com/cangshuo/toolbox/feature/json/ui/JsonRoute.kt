package com.cangshuo.toolbox.feature.json.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun JsonRoute(factory: ViewModelProvider.Factory) {
    val viewModel: JsonViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    JsonScreen(
        state = uiState,
        onModeChanged = viewModel::setMode,
        onIndentChanged = viewModel::setIndent,
        onStringInputChanged = viewModel::setStringInput,
        onInputChanged = viewModel::updateInput,
        onSwap = viewModel::swapContent,
        onClear = viewModel::clear,
        onLoadSample = viewModel::loadSample,
        onCancel = viewModel::cancelProcessing,
        onRetry = viewModel::retryProcessing,
    )
}
