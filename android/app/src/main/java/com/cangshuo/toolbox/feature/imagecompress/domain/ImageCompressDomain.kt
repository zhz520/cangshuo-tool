package com.cangshuo.toolbox.feature.imagecompress.domain

import android.graphics.Bitmap
import android.net.Uri

/** Supported output formats for compressed images. */
enum class OutputImageFormat {
    MATCH_SOURCE,
    JPEG,
    PNG,
    WEBP,
}

/** Dimension resizing strategy. */
enum class ResizeMode {
    ORIGINAL,
    SCALE_PERCENT,
    MAX_DIMENSION,
}

enum class FlipMode { NONE, HORIZONTAL, VERTICAL, BOTH }

fun FlipMode.scaleFactors(): Pair<Float, Float> = when (this) {
    FlipMode.NONE -> 1f to 1f
    FlipMode.HORIZONTAL -> -1f to 1f
    FlipMode.VERTICAL -> 1f to -1f
    FlipMode.BOTH -> -1f to -1f
}

/** Compression parameters selected by the user. */
data class CompressParams(
    val quality: Int = 80,
    val resizeMode: ResizeMode = ResizeMode.MAX_DIMENSION,
    val scalePercent: Int = 80,
    val maxDimension: Int = 1920,
    val outputFormat: OutputImageFormat = OutputImageFormat.MATCH_SOURCE,
    val flipMode: FlipMode = FlipMode.NONE,
    val cornerRadiusPercent: Int = 0,
)

fun cornerRadiusPx(percent: Int, width: Int, height: Int): Float =
    minOf(width, height) * percent.coerceIn(0, 50) / 100f

/** Basic metadata describing dimensions, size, and type of an image. */
data class ImageMetadata(
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val mimeType: String,
    val fileName: String = "",
)

/** The result of a local image compression operation. */
data class CompressResult(
    val previewBitmap: Bitmap,
    val outputId: String,
    val originalMetadata: ImageMetadata,
    val compressedMetadata: ImageMetadata,
    val compressionRatio: Float?,
    val savedBytes: Long,
)

enum class ImageFailure {
    INVALID_IMAGE, UNSUPPORTED_FORMAT, TOO_LARGE, MEMORY_LIMIT, OUTPUT_TOO_LARGE,
    CACHE_FULL, LOAD_FAILED, COMPRESS_FAILED, SAVE_FAILED, SHARE_FAILED,
}

class ImageProcessingException(val reason: ImageFailure) : Exception(reason.name)

data class ImageExportRequest(val fileName: String, val mimeType: String)

data class ImageShareRequest(val uri: Uri, val mimeType: String)
