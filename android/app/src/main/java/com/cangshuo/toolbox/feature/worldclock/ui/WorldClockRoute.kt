package com.cangshuo.toolbox.feature.worldclock.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun WorldClockRoute(factory: ViewModelProvider.Factory) {
    val viewModel: WorldClockViewModel = viewModel(factory = factory)
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(locale) { viewModel.updateLocale(locale) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.start()
        onStopOrDispose { viewModel.stop() }
    }
    WorldClockScreen(
        state = state,
        onAddZone = viewModel::addZone,
        onRemoveZone = viewModel::removeZone,
        onHourFormatSelected = viewModel::setHourFormat,
        onQueryChanged = viewModel::updateQuery,
        onClearQuery = viewModel::clearQuery,
        onDismissError = viewModel::dismissError,
    )
}
