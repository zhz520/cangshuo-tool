package com.cangshuo.toolbox.feature.pinyin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PinyinRoute(factory: ViewModelProvider.Factory) {
    val viewModel: PinyinViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PinyinScreen(
        state = uiState,
        onInputChanged = viewModel::updateInput,
        onModeSelected = viewModel::selectMode,
        onToneStyleSelected = viewModel::selectToneStyle,
        onCaseModeSelected = viewModel::selectCaseMode,
        onSeparatorSelected = viewModel::selectSeparator,
        onVCharTypeSelected = viewModel::selectVCharType,
        onKeepNonChineseChanged = viewModel::toggleKeepNonChinese,
        onExampleSelected = viewModel::useExample,
        onCancel = viewModel::cancel,
        onRetry = viewModel::retry,
        onClear = viewModel::clear,
    )
}
