package com.cangshuo.toolbox.feature.workvalue.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun WorkValueRoute(factory: ViewModelProvider.Factory) {
    val viewModel: WorkValueViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkValueScreen(
        state = state,
        onValueChange = viewModel::update,
        onPreset = viewModel::applyPreset,
        onClear = viewModel::clear,
    )
}
