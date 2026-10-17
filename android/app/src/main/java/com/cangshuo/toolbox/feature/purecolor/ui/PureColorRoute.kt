package com.cangshuo.toolbox.feature.purecolor.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PureColorRoute(factory: ViewModelProvider.Factory) {
    val model: PureColorViewModel = viewModel(factory = factory)
    val state by model.uiState.collectAsStateWithLifecycle()
    PureColorScreen(
        state = state,
        onColorChanged = model::updateColor,
        onWidthChanged = model::updateWidth,
        onHeightChanged = model::updateHeight,
        onPreset = model::setPreset,
        onSave = { model.onSave(it?.let(Uri::parse)) },
        onShare = model::onShare,
        onShareHandled = model::onShareHandled,
        onMessageConsumed = model::onMessageConsumed,
    )
}
