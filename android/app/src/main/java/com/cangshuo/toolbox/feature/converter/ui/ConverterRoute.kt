package com.cangshuo.toolbox.feature.converter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Entry composable for the unit converter; creates the ViewModel and connects state. */
@Composable
fun ConverterRoute(factory: ViewModelProvider.Factory) {
    val viewModel: ConverterViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ConverterScreen(
        state = uiState,
        onTypeSelected = viewModel::selectType,
        onFromUnitSelected = viewModel::selectFromUnit,
        onToUnitSelected = viewModel::selectToUnit,
        onInputChanged = viewModel::updateInput,
        onSwap = viewModel::swap,
    )
}
