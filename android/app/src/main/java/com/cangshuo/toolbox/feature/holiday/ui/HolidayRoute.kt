package com.cangshuo.toolbox.feature.holiday.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HolidayRoute(factory: ViewModelProvider.Factory) {
    val viewModel: HolidayViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.start()
        onStopOrDispose { viewModel.stop() }
    }
    HolidayScreen(
        state = state,
        onSelect = viewModel::select,
        onFilter = viewModel::setFilter,
        onRefresh = viewModel::refresh,
    )
}
