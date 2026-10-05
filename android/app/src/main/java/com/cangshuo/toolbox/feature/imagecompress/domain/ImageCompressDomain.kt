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

/** Compression parameters selected by the user. */
data class CompressParams(
    val quality: Int = 80,
    val resizeMode: ResizeMode = ResizeMode.MAX_DIMENSION,
    val scalePercent: Int = 80,
    val maxDimension: Int = 1920,
    val outputFormat: OutputImageFormat = OutputImageFormat.MATCH_SOURCE,
)

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
