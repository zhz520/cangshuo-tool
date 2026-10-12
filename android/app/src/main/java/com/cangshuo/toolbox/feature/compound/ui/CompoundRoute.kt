package com.cangshuo.toolbox.feature.compound.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CompoundRoute(factory: ViewModelProvider.Factory) {
    val viewModel: CompoundViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CompoundScreen(
        state = uiState,
        onPrincipalChanged = viewModel::updatePrincipal,
        onContributionChanged = viewModel::updateContribution,
        onRateChanged = viewModel::updateRate,
        onYearsChanged = viewModel::updateYears,
        onPeriodSelected = viewModel::selectPeriod,
        onClear = viewModel::clear,
    )
}
