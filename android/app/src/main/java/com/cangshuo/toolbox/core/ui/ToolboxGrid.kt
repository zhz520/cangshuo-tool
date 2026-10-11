package com.cangshuo.toolbox.core.ui

/** Bound desired columns by actual pane width and font size, rather than device model. */
fun toolboxGridColumns(widthDp: Float, fontScale: Float, requestedColumns: Int = 0): Int {
    val gap = 8f
    val minimumTileWidth = 64f * fontScale.coerceAtLeast(1f)
    val fitting = ((widthDp.coerceAtLeast(0f) + gap) / (minimumTileWidth + gap)).toInt().coerceAtLeast(1)
    return fitting.coerceAtMost(if (requestedColumns > 0) requestedColumns else 6)
}
