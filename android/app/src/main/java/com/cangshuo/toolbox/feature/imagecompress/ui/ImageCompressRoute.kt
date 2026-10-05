package com.cangshuo.toolbox.feature.imagecompress.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ImageCompressRoute(factory: ViewModelProvider.Factory) {
    val viewModel: ImageCompressViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ImageCompressScreen(
        state = uiState,
        onImageSelected = viewModel::onImageSelected,
        onQualityChanged = viewModel::onQualityChanged,
        onResizeModeChanged = viewModel::onResizeModeChanged,
        onScalePercentChanged = viewModel::onScalePercentChanged,
        onMaxDimensionChanged = viewModel::onMaxDimensionChanged,
        onFormatChanged = viewModel::onFormatChanged,
        onSaveRequested = viewModel::onSaveRequested,
        onDocumentCreated = viewModel::onDocumentCreated,
        onExportUnavailable = viewModel::onExportUnavailable,
        onShareRequested = viewModel::onShareRequested,
        onShareHandled = viewModel::onShareHandled,
        onPreviewTabChanged = viewModel::onPreviewTabChanged,
        onClear = viewModel::onClear,
        onDismissMessage = viewModel::onDismissMessage,
        onRetry = viewModel::onRetry,
    )
}
