package com.cangshuo.toolbox.feature.charconvert.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CharConvertRoute(factory: ViewModelProvider.Factory) {
    val viewModel: CharConvertViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CharConvertScreen(
        state = uiState,
        onInputChanged = viewModel::updateInput,
        onWidthSelected = viewModel::selectWidth,
        onCaseSelected = viewModel::selectCase,
        onCollapseChanged = viewModel::toggleCollapse,
        onZeroWidthChanged = viewModel::toggleZeroWidth,
        onNewlinesChanged = viewModel::toggleNewlines,
        onClear = viewModel::clear,
    )
}
