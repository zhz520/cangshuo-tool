package com.cangshuo.toolbox.feature.colorpicker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ColorPickerRoute(factory: ViewModelProvider.Factory) {
    val model: ColorPickerViewModel = viewModel(factory = factory)
    val state by model.uiState.collectAsStateWithLifecycle()
    ColorPickerScreen(
        state = state,
        onImagePicked = model::onImagePicked,
        onSample = model::onSample,
        onPaletteSelected = model::onPaletteSelected,
        onClearImage = model::onClearImage,
        onClearPalette = model::onClearPalette,
        onRetry = model::onRetry,
        onCancel = model::onCancelLoad,
        onPickerUnavailable = model::onPickerUnavailable,
    )
}
