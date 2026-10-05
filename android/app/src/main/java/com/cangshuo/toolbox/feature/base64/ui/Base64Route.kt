package com.cangshuo.toolbox.feature.base64.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun Base64Route(factory: ViewModelProvider.Factory) {
    val viewModel: Base64ViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Base64Screen(
        state = uiState,
        onModeChanged = viewModel::setMode,
        onInputChanged = viewModel::updateInput,
        onUrlSafeToggled = viewModel::setUrlSafe,
        onSwap = viewModel::swapContent,
        onClear = viewModel::clear,
    )
}
