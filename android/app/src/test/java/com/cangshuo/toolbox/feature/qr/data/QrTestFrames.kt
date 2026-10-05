package com.cangshuo.toolbox.feature.qr.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/** Renders ZXing symbols into ARGB frames for the JVM decode tests; no Android classes are used. */
object QrTestFrames {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()

    /** Linear symbol with the module axis along X. */
    fun horizontal(frame: IntArray, frameWidth: Int, text: String, format: BarcodeFormat,
                   left: Int, top: Int, scale: Int, barHeight: Int) {
        val matrix = linear(text, format)
        val frameHeight = frame.size / frameWidth
        for (x in 0 until matrix.width) {
            if (!matrix.get(x, 0)) continue
            for (dx in 0 until scale) {
                val px = left + x * scale + dx
                if (px !in 0 until frameWidth) continue
                for (dy in 0 until barHeight) {
                    val py = top + dy
                    if (py in 0 until frameHeight) frame[py * frameWidth + px] = BLACK
                }
            }
        }
    }

    /** Rotated linear symbol with the module axis along Y. */
    fun vertical(frame: IntArray, frameWidth: Int, text: String, format: BarcodeFormat,
                 left: Int, top: Int, scale: Int, barLength: Int) {
        val matrix = linear(text, format)
        val frameHeight = frame.size / frameWidth
        for (module in 0 until matrix.width) {
            if (!matrix.get(module, 0)) continue
            for (dy in 0 until scale) {
                val py = top + module * scale + dy
                if (py !in 0 until frameHeight) continue
                for (dx in 0 until barLength) {
                    val px = left + dx
                    if (px in 0 until frameWidth) frame[py * frameWidth + px] = BLACK
                }
            }
        }
    }

    /** QR symbol scaled by an integer factor; the matrix already includes its quiet zone. */
    fun qr(frame: IntArray, frameWidth: Int, text: String, left: Int, top: Int, scale: Int) {
        val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 4))
        val frameHeight = frame.size / frameWidth
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (!matrix.get(x, y)) continue
            for (dy in 0 until scale) for (dx in 0 until scale) {
                val px = left + x * scale + dx
                val py = top + y * scale + dy
                if (px in 0 until frameWidth && py in 0 until frameHeight) {
                    frame[py * frameWidth + px] = BLACK
                }
            }
        }
    }

    private fun linear(text: String, format: BarcodeFormat): BitMatrix =
        MultiFormatWriter().encode(text, format, 0, 0, mapOf(EncodeHintType.MARGIN to 12))
}
