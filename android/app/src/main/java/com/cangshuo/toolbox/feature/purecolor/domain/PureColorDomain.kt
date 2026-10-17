package com.cangshuo.toolbox.feature.purecolor.domain

import android.net.Uri
import kotlin.math.min

data class PureColorParams(val argb: Int, val width: Int, val height: Int)

enum class PureColorError { INVALID_COLOR, INVALID_DIMENSION, TOO_LARGE, MEMORY_LIMIT, SAVE_FAILED, SHARE_FAILED }

class PureColorException(val reason: PureColorError) : Exception(reason.name)

object PureColorLimits {
    const val MAX_EDGE = 4_096
    const val MAX_PIXELS = 8_000_000L
    const val MAX_FILE_BYTES = 32L * 1024 * 1024
}

fun parseHexColor(input: String): Int? {
    val value = input.trim().removePrefix("#")
    if (value.length !in setOf(3, 6, 8) || value.any { it.digitToIntOrNull(16) == null }) return null
    val expanded = if (value.length == 3) value.map { "$it$it" }.joinToString("") else value
    val rgb = expanded.take(6)
    val alpha = if (expanded.length == 8) expanded.substring(6) else "FF"
    return (alpha + rgb).toLong(16).toInt()
}

fun argbHex(argb: Int): String = if (argb ushr 24 == 0xFF) {
    String.format(java.util.Locale.ROOT, "#%02X%02X%02X", (argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
} else {
    String.format(java.util.Locale.ROOT, "#%02X%02X%02X%02X", (argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, argb ushr 24)
}

fun validatePureColor(params: PureColorParams, heapBytes: Long) {
    if (params.width !in 1..PureColorLimits.MAX_EDGE || params.height !in 1..PureColorLimits.MAX_EDGE) {
        throw PureColorException(PureColorError.INVALID_DIMENSION)
    }
    if (params.width.toLong() * params.height > PureColorLimits.MAX_PIXELS) {
        throw PureColorException(PureColorError.TOO_LARGE)
    }
    val bytes = params.width.toLong() * params.height * 4 + 8L * 1024 * 1024
    if (bytes > minOf(96L * 1024 * 1024, heapBytes / 3)) {
        throw PureColorException(PureColorError.MEMORY_LIMIT)
    }
}

fun pureColorFileName(params: PureColorParams): String {
    val hex = argbHex(params.argb).removePrefix("#")
    return "pure-color-#$hex-${params.width}x${params.height}.png"
}

interface PureColorRepository {
    suspend fun save(params: PureColorParams, destination: Uri? = null): Result<Uri>
    suspend fun prepareShare(params: PureColorParams): Result<Uri>
}

class SavePureColorUseCase(private val repository: PureColorRepository) {
    suspend operator fun invoke(params: PureColorParams, destination: Uri? = null) = repository.save(params, destination)
}

class SharePureColorUseCase(private val repository: PureColorRepository) {
    suspend operator fun invoke(params: PureColorParams) = repository.prepareShare(params)
}
