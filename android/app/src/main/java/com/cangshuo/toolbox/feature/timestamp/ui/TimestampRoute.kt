package com.cangshuo.toolbox.feature.timestamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TimestampRoute(factory: ViewModelProvider.Factory) {
    val viewModel: TimestampViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TimestampScreen(
        state = uiState,
        onTimestampInputChanged = viewModel::updateTimestampInput,
        onTimestampUnitChanged = viewModel::updateTimestampUnit,
        onTimestampTimeZoneChanged = viewModel::updateTimestampTimeZone,
        onFillCurrentTimestamp = viewModel::fillCurrentTimestamp,
        onDateInputChanged = viewModel::updateDateInput,
        onDateTimeZoneChanged = viewModel::updateDateTimeZone,
        onFillCurrentDateTime = viewModel::fillCurrentDateTime,
        onToggleLive = viewModel::toggleLive,
        onRefreshNow = viewModel::refreshNow,
    )
}
