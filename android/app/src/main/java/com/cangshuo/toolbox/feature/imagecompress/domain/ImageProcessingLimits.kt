package com.cangshuo.toolbox.feature.imagecompress.domain

import kotlin.math.max
import kotlin.math.roundToInt

/** Resource limits apply before any full bitmap allocation. */
internal object ImageProcessingLimits {
    const val PREVIEW_EDGE = 1_024
    const val MAX_OUTPUT_EDGE = 8_192
    const val MAX_OUTPUT_PIXELS = 8_000_000L
    const val MAX_OUTPUT_BYTES = 32L * 1024 * 1024
    const val MAX_CACHE_BYTES = 256L * 1024 * 1024
    const val CACHE_LIFETIME_MS = 24L * 60 * 60 * 1000

    fun validateSource(width: Int, height: Int) {
        if (width <= 0 || height <= 0) throw ImageProcessingException(ImageFailure.INVALID_IMAGE)
        if (max(width, height) > 100_000 || width.toLong() * height > 500_000_000L) {
            throw ImageProcessingException(ImageFailure.TOO_LARGE)
        }
    }

    fun targetSize(width: Int, height: Int, params: CompressParams): Pair<Int, Int> {
        val factor = when (params.resizeMode) {
            ResizeMode.ORIGINAL -> 1.0
            ResizeMode.SCALE_PERCENT -> params.scalePercent.coerceIn(10, 100) / 100.0
            ResizeMode.MAX_DIMENSION ->
                (params.maxDimension.coerceIn(100, MAX_OUTPUT_EDGE).toDouble() / max(width, height)).coerceAtMost(1.0)
        }
        val target = Pair(max(1, (width * factor).roundToInt()), max(1, (height * factor).roundToInt()))
        if (max(target.first, target.second) > MAX_OUTPUT_EDGE ||
            target.first.toLong() * target.second > MAX_OUTPUT_PIXELS
        ) {
            throw ImageProcessingException(ImageFailure.TOO_LARGE)
        }
        return target
    }

    fun previewSample(width: Int, height: Int): Int {
        var sample = 1
        while (ceilDivide(width, sample) > PREVIEW_EDGE || ceilDivide(height, sample) > PREVIEW_EDGE) {
            sample *= 2
        }
        return sample
    }

    fun boundedPreviewSample(width: Int, height: Int, heapBytes: Long): Int {
        var sample = previewSample(width, height)
        while (true) {
            val previewWidth = ceilDivide(width, sample).toInt()
            val previewHeight = ceilDivide(height, sample).toInt()
            try {
                validateMemory(width, height, sample, previewWidth, previewHeight, heapBytes)
                return sample
            } catch (error: ImageProcessingException) {
                if (max(previewWidth, previewHeight) <= 1) throw error
                sample *= 2
            }
        }
    }

    fun targetSample(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (width.toLong() / (sample.toLong() * 2) >= targetWidth &&
            height.toLong() / (sample.toLong() * 2) >= targetHeight
        ) {
            sample *= 2
        }
        return sample
    }

    fun validateMemory(width: Int, height: Int, sample: Int, targetWidth: Int, targetHeight: Int, heapBytes: Long) {
        val decodedPixels = ceilDivide(width, sample) * ceilDivide(height, sample)
        val targetPixels = targetWidth.toLong() * targetHeight
        // Covers overlapping transform/scale/alpha-composite bitmaps and preview/codec headroom.
        val bitmapPixels = max(max(decodedPixels * 2, decodedPixels + targetPixels), targetPixels * 2)
        val estimatedBytes = bitmapPixels * 4 + 16L * 1024 * 1024
        val budget = minOf(96L * 1024 * 1024, heapBytes / 3)
        if (estimatedBytes > budget) throw ImageProcessingException(ImageFailure.MEMORY_LIMIT)
    }

    private fun ceilDivide(value: Int, divisor: Int): Long = (value.toLong() + divisor - 1) / divisor
}
