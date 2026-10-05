package com.cangshuo.toolbox.feature.base64.data

import com.cangshuo.toolbox.feature.base64.domain.Base64Repository
import com.cangshuo.toolbox.feature.base64.domain.Base64Result
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Base64

class LocalBase64Repository : Base64Repository {

    override fun encode(text: String, isUrlSafe: Boolean): Base64Result {
        if (text.isEmpty()) return Base64Result.Empty
        val bytes = text.toByteArray(Charsets.UTF_8)
        val encoded = if (isUrlSafe) {
            Base64.getUrlEncoder().encodeToString(bytes)
        } else {
            Base64.getEncoder().encodeToString(bytes)
        }
        return Base64Result.Encoded(encoded)
    }

    override fun decode(text: String, isUrlSafe: Boolean): Base64Result {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Base64Result.Empty
        // Strip whitespace and newlines for resilience, matching the documented tolerant input.
        val sanitized = WHITESPACE.replace(trimmed, "")
        val bytes = decodeBase64(sanitized, isUrlSafe) ?: return Base64Result.InvalidInput()
        val inspection = inspectUtf8(bytes)
        return Base64Result.Decoded(
            text = inspection.text,
            hex = toHex(bytes),
            byteCount = bytes.size,
            invalidUtf8Count = inspection.invalidCount,
            firstInvalidByteOffset = inspection.firstInvalidOffset,
        )
    }

    private fun decodeBase64(text: String, isUrlSafe: Boolean): ByteArray? {
        val primaryDecoder = if (isUrlSafe) Base64.getUrlDecoder() else Base64.getDecoder()
        try {
            return primaryDecoder.decode(text)
        } catch (_: IllegalArgumentException) {
            // Fallback to the opposite alphabet, preserving the existing tolerant behaviour.
        }
        val fallbackDecoder = if (isUrlSafe) Base64.getDecoder() else Base64.getUrlDecoder()
        return try {
            fallbackDecoder.decode(text)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Validates every byte per RFC 3629 and reports the first invalid position for the UI. */
    private fun inspectUtf8(bytes: ByteArray): Utf8Inspection {
        var index = 0
        var invalidCount = 0
        var firstInvalid: Int? = null

        while (index < bytes.size) {
            val first = bytes[index].toInt() and 0xFF
            val width = when {
                first <= 0x7F -> 1
                first in 0xC2..0xDF -> 2
                first in 0xE0..0xEF -> 3
                first in 0xF0..0xF4 -> 4
                else -> 0
            }
            var valid = width != 0 && index + width <= bytes.size
            if (valid) {
                for (offset in 1 until width) {
                    val continuation = bytes[index + offset].toInt() and 0xFF
                    if (continuation !in 0x80..0xBF) {
                        valid = false
                        break
                    }
                }
            }
            if (valid) {
                val codePoint = when (width) {
                    1 -> first
                    2 -> ((first and 0x1F) shl 6) or (bytes[index + 1].toInt() and 0x3F)
                    3 -> ((first and 0x0F) shl 12) or
                        ((bytes[index + 1].toInt() and 0x3F) shl 6) or
                        (bytes[index + 2].toInt() and 0x3F)
                    else -> ((first and 0x07) shl 18) or
                        ((bytes[index + 1].toInt() and 0x3F) shl 12) or
                        ((bytes[index + 2].toInt() and 0x3F) shl 6) or
                        (bytes[index + 3].toInt() and 0x3F)
                }
                val minimum = when (width) {
                    2 -> 0x80
                    3 -> 0x800
                    4 -> 0x10000
                    else -> 0
                }
                if (codePoint < minimum || codePoint in 0xD800..0xDFFF || codePoint > 0x10FFFF) {
                    valid = false
                }
            }

            if (valid) {
                index += width
            } else {
                invalidCount++
                if (firstInvalid == null) firstInvalid = index
                index++
            }
        }

        if (invalidCount == 0) {
            val decoded = decodeStrictUtf8(bytes)
            if (decoded != null) return Utf8Inspection(decoded, 0, null)
        }
        // The byte scanner and the decoder agree on RFC 3629 input. If they ever disagree, report
        // the bytes as invalid instead of silently creating replacement characters.
        return Utf8Inspection(null, invalidCount.coerceAtLeast(1), firstInvalid ?: 0)
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun toHex(bytes: ByteArray): String {
        val output = CharArray(bytes.size * 2)
        bytes.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            output[index * 2] = HEX_DIGITS[value ushr 4]
            output[index * 2 + 1] = HEX_DIGITS[value and 0x0F]
        }
        return String(output)
    }

    private data class Utf8Inspection(
        val text: String?,
        val invalidCount: Int,
        val firstInvalidOffset: Int?,
    )

    private companion object {
        const val HEX_DIGITS = "0123456789ABCDEF"
        val WHITESPACE = "\\s+".toRegex()
    }
}
