package com.cangshuo.toolbox.feature.chineseconvert.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ChineseConversionRoute(factory: ViewModelProvider.Factory) {
    val model: ChineseConversionViewModel = viewModel(factory = factory)
    val state by model.uiState.collectAsStateWithLifecycle()
    ChineseConversionScreen(
        state, model::updateInput, model::selectMode, model::reverseResult,
        model::clear, model::cancel, model::retry,
    )
}
