package com.cangshuo.toolbox.feature.qr.data

import com.cangshuo.toolbox.feature.qr.domain.QrDecodeEntry
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.HybridBinarizer

/**
 * Bounded multi-code detection for linear barcodes in a still image.
 *
 * ZXing's GenericMultipleBarcodeReader recurses into quadrants and can run hundreds of decode
 * attempts on a camera-sized frame, so this follows the idea behind ZXing's ByQuadrantReader but
 * splits the frame into a fixed number of overlapping horizontal bands and decodes each band once.
 * Cost stays near one full-frame decode, identical payloads collapse into one entry, and at least
 * one band always contains a horizontal symbol in full.
 */
object QrBandDecoder {
    const val HORIZONTAL_BANDS = 3
    const val VERTICAL_BANDS = 3
    private const val OVERLAP_PERCENT = 12
    private const val MIN_BAND_HEIGHT = 24
    private const val MIN_BAND_WIDTH = 24

    /**
     * Distinct payloads in band order (horizontal first, then vertical); empty means nothing
     * decoded. Pass [deadlineNanos] to stop between bands when the caller's budget is exhausted.
     */
    fun decode(pixels: IntArray, width: Int, height: Int,
               deadlineNanos: Long = Long.MAX_VALUE): List<QrDecodeEntry> {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return emptyList()
        val reader = QrBarcodeFormats.newReader()
        val entries = LinkedHashMap<String, QrDecodeEntry>()
        try {
            for ((top, rows) in bands(height, MIN_BAND_HEIGHT, HORIZONTAL_BANDS)) {
                if (System.nanoTime() > deadlineNanos) break
                val band = IntArray(width * rows)
                System.arraycopy(pixels, top * width, band, 0, band.size)
                decodeInto(reader, entries, RGBLuminanceSource(width, rows, band))
            }
            if (System.nanoTime() > deadlineNanos) return entries.values.toList()
            for ((left, columns) in bands(width, MIN_BAND_WIDTH, VERTICAL_BANDS)) {
                if (System.nanoTime() > deadlineNanos) break
                // ZXing's 1D readers scan horizontal rows, so a vertical strip is transposed
                // before decoding instead of relying on the reader's own rotation fallback.
                val band = IntArray(columns * height)
                for (row in 0 until height) {
                    val sourceRow = row * width + left
                    for (column in 0 until columns) {
                        band[column * height + row] = pixels[sourceRow + column]
                    }
                }
                decodeInto(reader, entries, RGBLuminanceSource(height, columns, band))
            }
        } finally {
            reader.reset()
        }
        return entries.values.toList()
    }

    /** Overlapping band ranges along one axis: (offset, size) pairs. */
    private fun bands(length: Int, minimum: Int, count: Int): List<Pair<Int, Int>> {
        if (length < count * minimum) return emptyList()
        val bandSize = length / count
        val overlap = bandSize * OVERLAP_PERCENT / 100
        return (0 until count).map { index ->
            val start = (index * bandSize - overlap).coerceAtLeast(0)
            val end = ((index + 1) * bandSize + overlap).coerceAtMost(length)
            start to (end - start)
        }
    }

    private fun decodeInto(reader: MultiFormatReader, entries: MutableMap<String, QrDecodeEntry>, source: RGBLuminanceSource) {
        val result = try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
        val text = result?.text
        if (text != null && text.isNotEmpty()) {
            entries.putIfAbsent(text, QrDecodeEntry(text, QrBarcodeFormats.symbologyOf(result.barcodeFormat)))
        }
    }
}
