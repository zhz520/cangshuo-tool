package com.cangshuo.toolbox.feature.kinship.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun KinshipRoute(factory: ViewModelProvider.Factory) {
    val viewModel: KinshipViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    KinshipScreen(
        state = uiState,
        onTextChanged = viewModel::updateText,
        onTargetChanged = viewModel::updateTarget,
        onAppendRelation = viewModel::appendRelation,
        onRemoveLast = viewModel::removeLastRelation,
        onSexSelected = viewModel::selectSex,
        onTypeSelected = viewModel::selectType,
        onReverseChanged = viewModel::toggleReverse,
        onOptimalChanged = viewModel::toggleOptimal,
        onClear = viewModel::clear,
    )
}
