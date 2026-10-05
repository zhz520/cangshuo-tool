package com.cangshuo.toolbox.feature.qr.data

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.cangshuo.toolbox.feature.qr.domain.QrDecodeEntry

/**
 * QR multi-code detection on a single luminance frame.
 *
 * ZXing's multi reader locates every QR code in the image through its multi finder-pattern search;
 * linear barcodes are not part of this path. Identical payloads are collapsed so one duplicate code
 * does not produce two list entries.
 */
object QrMultiDecoder {

    /** Distinct non-empty payloads in detection order; an empty list means nothing was decoded. */
    fun decode(source: LuminanceSource): List<QrDecodeEntry> = try {
        QRCodeMultiReader().decodeMultiple(BinaryBitmap(HybridBinarizer(source)), QrBarcodeFormats.hints)
            .mapNotNull { result ->
                result.text?.takeIf { text -> text.isNotEmpty() }?.let { text ->
                    QrDecodeEntry(text, QrBarcodeFormats.symbologyOf(result.barcodeFormat))
                }
            }
            .distinctBy { entry -> entry.text }
    } catch (_: Exception) {
        // Not found, damaged or unsupported content: the caller tries the inverted frame and then
        // the single-code path, which is where the failure reason is classified.
        emptyList()
    }
}
