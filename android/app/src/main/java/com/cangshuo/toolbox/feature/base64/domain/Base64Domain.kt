package com.cangshuo.toolbox.feature.base64.domain

enum class Base64Mode {
    ENCODE,
    DECODE,
}

sealed interface Base64Result {
    /** Text encoded to Base64. */
    data class Encoded(val text: String) : Base64Result

    /**
     * Decoded bytes. [text] is null when the bytes are not valid UTF-8; the raw bytes are always
     * preserved in [hex] so the user can inspect or copy them losslessly.
     */
    data class Decoded(
        val text: String?,
        val hex: String,
        val byteCount: Int,
        val invalidUtf8Count: Int,
        val firstInvalidByteOffset: Int?,
    ) : Base64Result

    data object Empty : Base64Result
    data class InvalidInput(val message: String? = null) : Base64Result
}
