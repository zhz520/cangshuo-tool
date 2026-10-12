package com.cangshuo.toolbox.feature.textcount.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TextCountRoute(factory: ViewModelProvider.Factory) {
    val viewModel: TextCountViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TextCountScreen(
        state = uiState,
        onTextChanged = viewModel::updateText,
        onClear = viewModel::clear,
    )
}
