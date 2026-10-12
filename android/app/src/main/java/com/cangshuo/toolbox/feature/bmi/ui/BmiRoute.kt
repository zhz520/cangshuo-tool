package com.cangshuo.toolbox.feature.bmi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun BmiRoute(factory: ViewModelProvider.Factory) {
    val viewModel: BmiViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BmiScreen(
        state = uiState,
        onHeightChanged = viewModel::updateHeight,
        onWeightChanged = viewModel::updateWeight,
        onUnitSelected = viewModel::selectUnits,
        onStandardSelected = viewModel::selectStandard,
        onClear = viewModel::clear,
    )
}
