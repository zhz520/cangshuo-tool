package com.cangshuo.toolbox.feature.mortgage.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MortgageRoute(factory: ViewModelProvider.Factory) {
    val viewModel: MortgageViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MortgageScreen(
        state = uiState,
        onAmountChanged = viewModel::updateAmount,
        onRateChanged = viewModel::updateRate,
        onYearsChanged = viewModel::updateYears,
        onMethodSelected = viewModel::selectMethod,
        onShowAllRowsChanged = viewModel::setShowAllRows,
        onClear = viewModel::clear,
    )
}
