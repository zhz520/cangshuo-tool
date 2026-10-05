package com.cangshuo.toolbox.feature.hash.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HashRoute(factory: ViewModelProvider.Factory) {
    val viewModel: HashViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HashScreen(
        state = uiState,
        onInputChanged = viewModel::updateInput,
        onUppercaseToggled = viewModel::setUppercase,
        onClear = viewModel::clear,
    )
}
