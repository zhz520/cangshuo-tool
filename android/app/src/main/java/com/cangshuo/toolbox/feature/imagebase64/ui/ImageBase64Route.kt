package com.cangshuo.toolbox.feature.imagebase64.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ImageBase64Route(factory: ViewModelProvider.Factory) {
    val model: ImageBase64ViewModel = viewModel(factory = factory)
    val state by model.uiState.collectAsStateWithLifecycle()
    ImageBase64Screen(
        state = state,
        onModeChanged = model::onModeChanged,
        onInputChanged = model::updateInput,
        onClearInput = model::clearInput,
        onImagePicked = model::onImagePicked,
        onDecode = model::onDecode,
        onRetry = model::onRetry,
        onCancel = model::onCancelLoad,
        onPickerUnavailable = model::onPickerUnavailable,
        onDataUrl = model::setDataUrl,
        onUrlSafe = model::setUrlSafe,
        onLineWrap = model::setLineWrap,
        onSave = model::onSave,
        onShare = model::onShare,
        onShareHandled = model::onShareHandled,
        onMessageConsumed = model::onMessageConsumed,
        onClearResult = model::onClearResult,
    )
}
