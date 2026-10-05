package com.cangshuo.toolbox.feature.urlcodec.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun UrlCodecRoute(factory: ViewModelProvider.Factory) {
    val viewModel: UrlCodecViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    UrlCodecScreen(
        state = uiState,
        onModeChanged = viewModel::setMode,
        onEncodeTypeChanged = viewModel::setEncodeType,
        onInputChanged = viewModel::updateInput,
        onSwap = viewModel::swapContent,
        onClear = viewModel::clear,
        onSample = viewModel::loadSample,
        onCancel = viewModel::cancelProcessing,
        onRetry = viewModel::retryProcessing,
    )
}
