package com.cangshuo.toolbox.feature.qr.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.cangshuo.toolbox.feature.qr.domain.QrSymbology

/**
 * Symbologies shared by the camera scanner and by gallery import.
 *
 * The list keeps formats that are common in practice and drops partly supported niche readers such
 * as RSS and MAXICODE. JourneyApps' DefaultDecoderFactory fills DecodeHintType.POSSIBLE_FORMATS and
 * CHARACTER_SET on a MultiFormatReader; TRY_HARDER is added here because camera frames and photos
 * are usually blurrier or more angled than a clean flat scan.
 */
object QrBarcodeFormats {
    val supported: List<BarcodeFormat> = listOf(
        BarcodeFormat.QR_CODE,
        BarcodeFormat.DATA_MATRIX,
        BarcodeFormat.AZTEC,
        BarcodeFormat.PDF_417,
        BarcodeFormat.CODE_128,
        BarcodeFormat.CODE_93,
        BarcodeFormat.CODE_39,
        BarcodeFormat.CODABAR,
        BarcodeFormat.ITF,
        BarcodeFormat.EAN_13,
        BarcodeFormat.EAN_8,
        BarcodeFormat.UPC_A,
        BarcodeFormat.UPC_E,
    )

    /** Callers own the returned reader and must call reset() after each decode attempt. */
    /** Shared by the single-code reader and by the QR multi reader. */
    val hints: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to supported,
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8",
    )

    fun newReader(): MultiFormatReader = MultiFormatReader().apply { setHints(hints) }

    /** Maps a ZXing format to the label shown with a decode result. */
    fun symbologyOf(format: BarcodeFormat?): QrSymbology = when (format) {
        BarcodeFormat.QR_CODE -> QrSymbology.QR_CODE
        BarcodeFormat.DATA_MATRIX -> QrSymbology.DATA_MATRIX
        BarcodeFormat.AZTEC -> QrSymbology.AZTEC
        BarcodeFormat.PDF_417 -> QrSymbology.PDF_417
        BarcodeFormat.CODE_128 -> QrSymbology.CODE_128
        BarcodeFormat.CODE_93 -> QrSymbology.CODE_93
        BarcodeFormat.CODE_39 -> QrSymbology.CODE_39
        BarcodeFormat.CODABAR -> QrSymbology.CODABAR
        BarcodeFormat.ITF -> QrSymbology.ITF
        BarcodeFormat.EAN_13 -> QrSymbology.EAN_13
        BarcodeFormat.EAN_8 -> QrSymbology.EAN_8
        BarcodeFormat.UPC_A -> QrSymbology.UPC_A
        BarcodeFormat.UPC_E -> QrSymbology.UPC_E
        else -> QrSymbology.UNKNOWN
    }
}
