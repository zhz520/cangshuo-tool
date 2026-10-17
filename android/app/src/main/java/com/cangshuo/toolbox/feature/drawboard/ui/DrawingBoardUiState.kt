package com.cangshuo.toolbox.feature.drawboard.ui

import com.cangshuo.toolbox.feature.drawboard.domain.DrawingDocument

enum class DrawingBoardMessage { SAVED, SAVE_FAILED, SHARE_FAILED, TOO_LARGE }

data class DrawingBoardUiState(
    val document: DrawingDocument = DrawingDocument(),
    val selectedArgb: Int,
    val brushSizeDp: Int,
    val eraser: Boolean = false,
    val gridMode: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val saving: Boolean = false,
    val sharing: Boolean = false,
    val shareRequest: String? = null,
    val message: DrawingBoardMessage? = null,
) {
    val busy: Boolean get() = saving || sharing
    val hasContent: Boolean get() = document.strokes.isNotEmpty()
}
