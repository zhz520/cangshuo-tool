package com.cangshuo.toolbox.feature.uuid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun UuidRoute(factory: ViewModelProvider.Factory) {
    val viewModel: UuidViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    UuidScreen(
        state = uiState,
        onCountSelected = viewModel::setCount,
        onUppercaseToggled = viewModel::setUppercase,
        onHyphensToggled = viewModel::setHyphens,
        onBracedToggled = viewModel::setBraced,
        onRegenerate = viewModel::regenerate,
    )
}
