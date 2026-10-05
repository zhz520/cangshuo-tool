package com.cangshuo.toolbox.feature.qr.data

import java.nio.ByteBuffer
import java.util.Arrays

/** A luminance frame in display orientation. */
data class QrFrame(val data: ByteArray, val width: Int, val height: Int)

/**
 * Geometry helpers for camera frames.
 *
 * PlanarYUVLuminanceSource indexes rows as `(y + top) * dataWidth + left`, so the Y plane must be
 * copied without row-stride padding. Quarter turns are materialised because ZXing's 1D readers scan
 * horizontal rows; half turns are left alone since every reader handles them.
 */
object QrFrameLuminance {

    /** Two reused buffers of at most 4 MB each; the analysis target is 1280x720. */
    const val MAX_FRAME_PIXELS: Long = 4_000_000L

    /** Copies the Y plane into [target] with the row stride removed. */
    fun copyPlanarY(buffer: ByteBuffer, rowStride: Int, width: Int, height: Int, target: ByteArray): Boolean {
        if (width <= 0 || height <= 0) return false
        val count = width.toLong() * height
        if (count > MAX_FRAME_PIXELS || target.size < count) return false
        val size = count.toInt()
        val limit = buffer.limit()
        if (rowStride == width && limit >= size) {
            buffer.rewind()
            buffer.get(target, 0, size)
            return true
        }
        if (rowStride < width) return false
        val rows = limit / rowStride
        if (rows < height) return false
        Arrays.fill(target, 0, size, 0)
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            buffer.get(target, row * width, width)
        }
        return true
    }

    /**
     * Returns the frame rotated clockwise to display orientation. Rotations of 0 and 180 degrees
     * return [data] unchanged; quarter turns are written into [target].
     */
    fun orient(data: ByteArray, width: Int, height: Int, rotationDegrees: Int, target: ByteArray): QrFrame? {
        if (width <= 0 || height <= 0) return null
        val count = width.toLong() * height
        if (count > MAX_FRAME_PIXELS || data.size < count) return null
        val size = count.toInt()
        val rotation = ((rotationDegrees % 360) + 360) % 360
        if (rotation != ROTATE_90 && rotation != ROTATE_270) return QrFrame(data, width, height)
        if (target.size < size) return null
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val destination = if (rotation == ROTATE_90) {
                    // (x, y) -> (height - 1 - y, x)
                    x * height + (height - 1 - y)
                } else {
                    // (x, y) -> (y, width - 1 - x)
                    (width - 1 - x) * height + y
                }
                target[destination] = data[row + x]
            }
        }
        return QrFrame(target, height, width)
    }

    private const val ROTATE_90 = 90
    private const val ROTATE_270 = 270
}
