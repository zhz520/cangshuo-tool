package com.cangshuo.toolbox.feature.datecalc.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DateCalcRoute(factory: ViewModelProvider.Factory) {
    val viewModel: DateCalcViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DateCalcScreen(
        state = uiState,
        onFirstChanged = viewModel::updateFirst,
        onSecondChanged = viewModel::updateSecond,
        onBaseChanged = viewModel::updateBase,
        onAmountChanged = viewModel::updateAmount,
        onUnitSelected = viewModel::selectUnit,
        onDirectionSelected = viewModel::selectDirection,
        onFillFirstToday = viewModel::fillFirstToday,
        onFillSecondToday = viewModel::fillSecondToday,
        onFillBaseToday = viewModel::fillBaseToday,
        onClear = viewModel::clear,
    )
}
