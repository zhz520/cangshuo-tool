package com.cangshuo.toolbox.feature.password.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PasswordRoute(factory: ViewModelProvider.Factory) {
    val viewModel: PasswordViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PasswordScreen(
        state = uiState,
        onLengthChanged = viewModel::updateLength,
        onCountChanged = viewModel::updateCount,
        onExcludeChanged = viewModel::updateExclude,
        onUppercaseChanged = viewModel::toggleUppercase,
        onLowercaseChanged = viewModel::toggleLowercase,
        onDigitsChanged = viewModel::toggleDigits,
        onSymbolsChanged = viewModel::toggleSymbols,
        onExcludeSimilarChanged = viewModel::toggleExcludeSimilar,
        onRequireEachClassChanged = viewModel::toggleRequireEachClass,
        onUniqueCharactersChanged = viewModel::toggleUniqueCharacters,
        onRegenerate = viewModel::regenerate,
    )
}
