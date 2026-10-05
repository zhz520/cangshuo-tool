package com.cangshuo.toolbox.feature.qr.data

import com.cangshuo.toolbox.feature.qr.domain.QrDecodeEntry
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Bounded linear-only pass used when several QR codes already matched in one frame.
 *
 * ZXing's multi reader is QR-only and the band decoder costs several extra passes, so a dense mixed
 * frame would otherwise lose its linear symbols. One additional full-frame pass restricted to the
 * linear formats finds at most one extra symbol and keeps the cost near a single normal decode.
 */
object QrLinearScanner {
    private val formats = listOf(
        BarcodeFormat.CODE_128, BarcodeFormat.CODE_93, BarcodeFormat.CODE_39,
        BarcodeFormat.CODABAR, BarcodeFormat.ITF,
        BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
    )

    /** First linear symbol in normal or inverted luminance; null when nothing decodes. */
    fun scanFirst(pixels: IntArray, width: Int, height: Int): QrDecodeEntry? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val reader = MultiFormatReader().apply {
            setHints(mapOf(
                DecodeHintType.POSSIBLE_FORMATS to formats,
                DecodeHintType.TRY_HARDER to true,
            ))
        }
        try {
            val source = RGBLuminanceSource(width, height, pixels)
            for (inverted in listOf(false, true)) {
                val luminance = if (inverted) source.invert() else source
                val result = try {
                    reader.decodeWithState(BinaryBitmap(HybridBinarizer(luminance)))
                } catch (_: Exception) {
                    null
                } finally {
                    reader.reset()
                }
                if (result != null && result.text.isNotEmpty()) {
                    return QrDecodeEntry(result.text, QrBarcodeFormats.symbologyOf(result.barcodeFormat))
                }
            }
        } finally {
            reader.reset()
        }
        return null
    }
}
