package com.cangshuo.toolbox.feature.fuel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FuelRoute(factory: ViewModelProvider.Factory) {
    val viewModel: FuelViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FuelScreen(
        state = uiState,
        onDistanceChanged = viewModel::updateDistance,
        onFuelChanged = viewModel::updateFuel,
        onPriceChanged = viewModel::updatePrice,
        onDistanceUnitSelected = viewModel::selectDistanceUnit,
        onFuelUnitSelected = viewModel::selectFuelUnit,
        onClear = viewModel::clear,
    )
}
