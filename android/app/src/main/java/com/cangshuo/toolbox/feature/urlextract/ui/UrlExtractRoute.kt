package com.cangshuo.toolbox.feature.urlextract.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun UrlExtractRoute(factory: ViewModelProvider.Factory) {
    val viewModel: UrlExtractViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    UrlExtractScreen(
        state = uiState,
        onTextChanged = viewModel::updateText,
        onBareDomainsChanged = viewModel::toggleBareDomains,
        onDedupeChanged = viewModel::toggleDedupe,
        onFormatSelected = viewModel::selectFormat,
        onClear = viewModel::clear,
    )
}
