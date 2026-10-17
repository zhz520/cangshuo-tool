package com.cangshuo.toolbox.feature.imagebase64.domain

import android.graphics.Bitmap
import java.util.Base64
import kotlin.math.min

enum class ImageBase64Mode { ENCODE, DECODE }

data class ImageBase64Options(
    val dataUrl: Boolean = true,
    val urlSafe: Boolean = false,
    val lineWrap: Boolean = false,
)

data class ImageBase64Result(
    val preview: Bitmap,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val byteCount: Long,
    val rawBase64: String,
    val fileName: String = "",
)

enum class ImageBase64Error {
    INVALID_INPUT, UNSUPPORTED_FORMAT, MIME_MISMATCH, TOO_LARGE, MEMORY_LIMIT,
    LOAD_FAILED, PICKER_UNAVAILABLE, SAVE_FAILED, SHARE_FAILED,
}

class ImageBase64Exception(val reason: ImageBase64Error) : Exception(reason.name)

object ImageBase64Limits {
    const val MAX_BINARY_BYTES = 4 * 1024 * 1024
    const val MAX_INPUT_CHARS = 6_000_000
    const val MAX_PREVIEW_EDGE = 1_600
    const val MAX_SOURCE_EDGE = 100_000
    const val MAX_SOURCE_PIXELS = 500_000_000L
    const val PREVIEW_OUTPUT_PREFIX = 1_024
    const val LINE_WIDTH = 76
}

data class ParsedBase64Image(
    val bytes: ByteArray,
    val canonicalBase64: String,
    val detectedMime: String,
    val declaredMime: String?,
)

private fun normalizeMime(value: String): String? = when (value.substringBefore(';').trim().lowercase()) {
    "image/png" -> "image/png"
    "image/jpeg", "image/jpg" -> "image/jpeg"
    "image/webp" -> "image/webp"
    "image/gif" -> "image/gif"
    else -> null
}

fun detectImageMime(bytes: ByteArray): String? {
    if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() && bytes[4] == 0x0D.toByte() &&
        bytes[5] == 0x0A.toByte() && bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()) return "image/png"
    if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return "image/jpeg"
    if (bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x52, 0x49, 0x46, 0x46)) &&
        bytes.copyOfRange(8, 12).contentEquals(byteArrayOf(0x57, 0x45, 0x42, 0x50))) return "image/webp"
    if (bytes.size >= 6) {
        val header = bytes.copyOfRange(0, 6)
        if (header.contentEquals(byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x37, 0x61)) ||
            header.contentEquals(byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61))) return "image/gif"
    }
    return null
}

/** Parses a raw Base64/Data URL into bounded bytes; magic bytes decide the actual image format. */
fun parseImageBase64Input(input: String, checkActive: () -> Unit = {}): ParsedBase64Image {
    checkActive()
    if (input.length > ImageBase64Limits.MAX_INPUT_CHARS) throw ImageBase64Exception(ImageBase64Error.TOO_LARGE)
    val trimmed = input.trim()
    if (trimmed.isEmpty()) throw ImageBase64Exception(ImageBase64Error.INVALID_INPUT)
    var payloadStart = 0
    var declaredMime: String? = null
    if (trimmed.startsWith("data:", ignoreCase = true)) {
        val comma = trimmed.indexOf(',')
        if (comma < 0 || comma > 256) throw ImageBase64Exception(ImageBase64Error.INVALID_INPUT)
        val metadata = trimmed.substring(5, comma)
        if (!metadata.endsWith(";base64", ignoreCase = true)) throw ImageBase64Exception(ImageBase64Error.INVALID_INPUT)
        val mime = metadata.substring(0, metadata.length - 7).trim()
        if (mime.isNotBlank()) {
            if (!mime.startsWith("image/", ignoreCase = true)) throw ImageBase64Exception(ImageBase64Error.UNSUPPORTED_FORMAT)
            declaredMime = normalizeMime(mime) ?: throw ImageBase64Exception(ImageBase64Error.UNSUPPORTED_FORMAT)
        }
        payloadStart = comma + 1
    }

    val clean = StringBuilder((trimmed.length - payloadStart).coerceAtMost(ImageBase64Limits.MAX_INPUT_CHARS))
    for (index in payloadStart until trimmed.length) {
        if (index and 0xFFFF == 0) checkActive()
        val char = trimmed[index]
        when {
            char == ' ' || char == '\n' || char == '\r' || char == '\t' -> Unit
            char.isLetterOrDigit() || char == '+' || char == '/' || char == '-' || char == '_' || char == '=' -> clean.append(char)
            else -> throw ImageBase64Exception(ImageBase64Error.INVALID_INPUT)
        }
    }
    if (clean.isEmpty()) throw ImageBase64Exception(ImageBase64Error.INVALID_INPUT)
    val firstPadding = clean.indexOf('=')
    if (firstPadding >= 0) {
        if (clean.length - firstPadding > 2 || clean.substring(firstPadding).any { it != '=' })
            throw ImageBase64Exception(ImageBase64Error.INVALID_INPUT)
    }
    var normalized = clean.toString().replace('-', '+').replace('_', '/')
    normalized = normalized.trimEnd('=')
    if (normalized.length % 4 == 1) throw ImageBase64Exception(ImageBase64Error.INVALID_INPUT)
    val estimatedBytes = normalized.length.toLong() * 3 / 4
    if (estimatedBytes > ImageBase64Limits.MAX_BINARY_BYTES) throw ImageBase64Exception(ImageBase64Error.TOO_LARGE)
    while (normalized.length % 4 != 0) normalized += "="
    checkActive()
    val bytes = try {
        Base64.getDecoder().decode(normalized)
    } catch (_: IllegalArgumentException) {
        throw ImageBase64Exception(ImageBase64Error.INVALID_INPUT)
    }
    if (bytes.isEmpty()) throw ImageBase64Exception(ImageBase64Error.INVALID_INPUT)
    checkActive()
    if (bytes.size > ImageBase64Limits.MAX_BINARY_BYTES) throw ImageBase64Exception(ImageBase64Error.TOO_LARGE)
    val detected = detectImageMime(bytes) ?: throw ImageBase64Exception(ImageBase64Error.UNSUPPORTED_FORMAT)
    if (declaredMime != null && declaredMime != detected) throw ImageBase64Exception(ImageBase64Error.MIME_MISMATCH)
    return ParsedBase64Image(bytes, Base64.getEncoder().encodeToString(bytes), detected, declaredMime)
}

fun encodeImageBase64(bytes: ByteArray): String {
    if (bytes.isEmpty()) throw ImageBase64Exception(ImageBase64Error.INVALID_INPUT)
    if (bytes.size > ImageBase64Limits.MAX_BINARY_BYTES) throw ImageBase64Exception(ImageBase64Error.TOO_LARGE)
    return Base64.getEncoder().encodeToString(bytes)
}

fun renderImageBase64(raw: String, mimeType: String, options: ImageBase64Options): String {
    if (raw.isEmpty()) return ""
    if (options.dataUrl) {
        val prefix = "data:$mimeType;base64,"
        return StringBuilder(prefix.length + raw.length).append(prefix).append(raw).toString()
    }
    val payload = if (options.urlSafe) raw.replace('+', '-').replace('/', '_').trimEnd('=') else raw
    if (!options.lineWrap) return payload
    val breaks = if (payload.isEmpty()) 0 else (payload.length - 1) / ImageBase64Limits.LINE_WIDTH
    val output = StringBuilder(payload.length + breaks)
    var offset = 0
    while (offset < payload.length) {
        if (offset > 0) output.append('\n')
        val end = (offset + ImageBase64Limits.LINE_WIDTH).coerceAtMost(payload.length)
        output.append(payload, offset, end)
        offset = end
    }
    return output.toString()
}

fun imageExtension(mimeType: String): String = when (mimeType) {
    "image/png" -> "png"
    "image/jpeg" -> "jpg"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    else -> "img"
}

/** Power-of-two preview sampling with room for the source bytes, Base64 text and orientation copy. */
fun imageBase64PreviewSampleSize(
    width: Int,
    height: Int,
    heapBytes: Long,
    transformed: Boolean,
    reservedBytes: Long,
): Int {
    require(width > 0 && height > 0)
    if (width > ImageBase64Limits.MAX_SOURCE_EDGE || height > ImageBase64Limits.MAX_SOURCE_EDGE ||
        width.toLong() * height > ImageBase64Limits.MAX_SOURCE_PIXELS) throw ImageBase64Exception(ImageBase64Error.TOO_LARGE)
    val budget = minOf(64L * 1024 * 1024, heapBytes / 4)
    var sample = 1
    while (true) {
        val w = (width.toLong() + sample - 1) / sample
        val h = (height.toLong() + sample - 1) / sample
        // RGBA_F16 is possible even when ARGB_8888 is requested. Count the old preview too.
        val imageBytes = w * h * 8
        val copies = if (transformed) 3 else 2
        if (w <= ImageBase64Limits.MAX_PREVIEW_EDGE && h <= ImageBase64Limits.MAX_PREVIEW_EDGE &&
            imageBytes * copies + reservedBytes <= budget) return sample
        if (w == 1L && h == 1L) throw ImageBase64Exception(ImageBase64Error.MEMORY_LIMIT)
        sample *= 2
    }
}

interface ImageBase64Repository {
    suspend fun encode(uri: String): Result<ImageBase64Result>
    suspend fun decode(text: String): Result<ImageBase64Result>
    suspend fun saveText(uri: String, text: String): Result<Unit>
    suspend fun prepareShareText(text: String): Result<android.net.Uri>
}

class LoadImageBase64UseCase(private val repository: ImageBase64Repository) {
    suspend operator fun invoke(uri: String) = repository.encode(uri)
}

class DecodeImageBase64UseCase(private val repository: ImageBase64Repository) {
    suspend operator fun invoke(text: String) = repository.decode(text)
}

class SaveImageBase64TextUseCase(private val repository: ImageBase64Repository) {
    suspend operator fun invoke(uri: String, text: String) = repository.saveText(uri, text)
}

class PrepareImageBase64ShareUseCase(private val repository: ImageBase64Repository) {
    suspend operator fun invoke(text: String) = repository.prepareShareText(text)
}
