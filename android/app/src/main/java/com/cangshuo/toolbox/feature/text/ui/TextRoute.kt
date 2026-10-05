package com.cangshuo.toolbox.feature.text.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TextRoute(factory: ViewModelProvider.Factory) {
    val viewModel: TextViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TextScreen(
        state = uiState,
        onGroupChanged = viewModel::setGroup,
        onOperationChanged = viewModel::setOperation,
        onInputChanged = viewModel::updateInput,
        onFindChanged = viewModel::updateFind,
        onReplaceChanged = viewModel::updateReplace,
        onMatchCaseChanged = viewModel::setMatchCase,
        onUseRegexChanged = viewModel::setUseRegex,
        onExpandGroupsChanged = viewModel::setExpandGroups,
        onMultilineChanged = viewModel::setMultiline,
        onDotAllChanged = viewModel::setDotAll,
        onCancel = viewModel::cancelProcessing,
        onRetry = viewModel::retryProcessing,
        onSwap = viewModel::swapContent,
        onLoadSample = viewModel::loadSample,
        onClear = viewModel::clear,
    )
}
