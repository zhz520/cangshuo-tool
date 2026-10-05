package com.cangshuo.toolbox.feature.qr.domain

import kotlin.math.roundToInt

/** Normalized square selection: center fractions of the image and side as a fraction of its shorter edge. */
data class QrCropRegion(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val sideFraction: Float = 1f,
)

/** Pixel square that always fits inside the decoded bitmap. */
data class QrCropRect(val left: Int, val top: Int, val side: Int)

/**
 * Shared by the overlay and the decoder so the visible square is exactly the decoded area.
 * Non-finite input is rejected; the side is clamped to the shorter edge and the square stays in bounds.
 */
object QrCropPolicy {
    const val MIN_SIDE_FRACTION = 0.1f
    private const val MIN_SIDE_PIXELS = 8

    fun rect(region: QrCropRegion, width: Int, height: Int): QrCropRect? {
        if (width < MIN_SIDE_PIXELS || height < MIN_SIDE_PIXELS) return null
        if (!region.centerX.isFinite() || !region.centerY.isFinite() || !region.sideFraction.isFinite()) return null
        val limit = minOf(width, height)
        val fraction = region.sideFraction.coerceIn(MIN_SIDE_FRACTION, 1f)
        val side = (fraction * limit).roundToInt().coerceIn(MIN_SIDE_PIXELS, limit)
        val centerX = (region.centerX.coerceIn(0f, 1f) * width).roundToInt()
        val centerY = (region.centerY.coerceIn(0f, 1f) * height).roundToInt()
        return QrCropRect(
            left = (centerX - side / 2).coerceIn(0, width - side),
            top = (centerY - side / 2).coerceIn(0, height - side),
            side = side,
        )
    }
}
