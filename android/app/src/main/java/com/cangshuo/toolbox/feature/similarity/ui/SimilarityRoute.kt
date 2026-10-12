package com.cangshuo.toolbox.feature.similarity.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SimilarityRoute(factory: ViewModelProvider.Factory) {
    val viewModel: SimilarityViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SimilarityScreen(
        state = uiState,
        onFirstChanged = viewModel::updateFirst,
        onSecondChanged = viewModel::updateSecond,
        onIgnoreCaseChanged = viewModel::toggleIgnoreCase,
        onIgnoreWhitespaceChanged = viewModel::toggleIgnoreWhitespace,
        onClear = viewModel::clear,
    )
}
