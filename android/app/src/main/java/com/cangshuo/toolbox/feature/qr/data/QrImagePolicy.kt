package com.cangshuo.toolbox.feature.qr.data

import com.cangshuo.toolbox.feature.qr.domain.QrFailure

internal class QrProcessingException(val reason: QrFailure) : Exception()

internal object QrImagePolicy {
    private const val MAX_EDGE = 2048
    private const val MAX_PIXELS = 4_000_000L
    private const val RESERVE_BYTES = 16L * 1024 * 1024
    private const val PREVIEW_MAX_EDGE = 1024
    private const val PREVIEW_RESERVE_BYTES = 8L * 1024 * 1024

    fun sample(width: Int, height: Int, heapBytes: Long): Int =
        sampleFor(width, height, MAX_EDGE) { w, h -> fits(w, h, heapBytes) }

    /** Bounded preview keeps at most 1024 px on the long edge; the overlay only needs screen detail. */
    fun previewSample(width: Int, height: Int, heapBytes: Long): Int =
        sampleFor(width, height, PREVIEW_MAX_EDGE) { w, h -> fitsPreview(w, h, heapBytes) }

    fun validateDecoded(width: Int, height: Int, heapBytes: Long) {
        if (width <= 0 || height <= 0 || width > MAX_EDGE || height > MAX_EDGE ||
            !fits(width.toLong(), height.toLong(), heapBytes)) {
            throw QrProcessingException(QrFailure.MEMORY_LIMIT)
        }
    }

    fun validatePreview(width: Int, height: Int, heapBytes: Long) {
        if (width <= 0 || height <= 0 || width > PREVIEW_MAX_EDGE || height > PREVIEW_MAX_EDGE ||
            !fitsPreview(width.toLong(), height.toLong(), heapBytes)) {
            throw QrProcessingException(QrFailure.MEMORY_LIMIT)
        }
    }

    private fun sampleFor(width: Int, height: Int, maxEdge: Int, fits: (Long, Long) -> Boolean): Int {
        if (width <= 0 || height <= 0) throw QrProcessingException(QrFailure.INVALID_IMAGE)
        if (width > 100_000 || height > 100_000 || width.toLong() * height > 500_000_000L) {
            throw QrProcessingException(QrFailure.IMAGE_TOO_LARGE)
        }
        var sample = 1
        while (true) {
            val w = (width.toLong() + sample - 1) / sample
            val h = (height.toLong() + sample - 1) / sample
            if (w <= maxEdge && h <= maxEdge && fits(w, h)) return sample
            if (w <= 1 && h <= 1) throw QrProcessingException(QrFailure.MEMORY_LIMIT)
            sample *= 2
        }
    }

    private fun fits(width: Long, height: Long, heapBytes: Long): Boolean {
        val pixels = width * height
        // Bitmap + ARGB pixels + luminance/inverted/binarizer working space, with headroom.
        val budget = minOf(96L * 1024 * 1024, heapBytes / 3)
        return pixels <= MAX_PIXELS && pixels * 12 + RESERVE_BYTES <= budget
    }

    private fun fitsPreview(width: Long, height: Long, heapBytes: Long): Boolean {
        val budget = minOf(64L * 1024 * 1024, heapBytes / 4)
        return width * height * 4 + PREVIEW_RESERVE_BYTES <= budget
    }
}
