package com.cangshuo.toolbox.feature.drawboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DrawingBoardRoute(factory: ViewModelProvider.Factory) {
    val model: DrawingBoardViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    DisposableEffect(model) {
        onDispose { model.onLeavePage() }
    }
    DrawingBoardScreen(
        state = state,
        onStroke = model::addStroke,
        onUndo = model::undo,
        onRedo = model::redo,
        onClear = model::clear,
        onColor = model::setColor,
        onEraser = model::setEraser,
        onGridMode = model::setGridMode,
        onBrushSize = model::setBrushSize,
        onSave = model::save,
        onShare = model::share,
        onShareHandled = model::shareHandled,
        onMessageConsumed = model::consumeMessage,
    )
}
