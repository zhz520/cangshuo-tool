package com.cangshuo.toolbox.feature.base64.ui

import com.cangshuo.toolbox.feature.base64.domain.Base64Mode

/** Decoded byte metadata for the Hex view, present only after a successful Base64 decode. */
data class Base64DecodeInfo(
    val hex: String,
    val byteCount: Int,
    val invalidUtf8Count: Int,
    val firstInvalidByteOffset: Int?,
) {
    val hasUtf8Error: Boolean get() = invalidUtf8Count > 0
}

data class Base64UiState(
    val mode: Base64Mode = Base64Mode.ENCODE,
    val inputText: String = "",
    val outputText: String = "",
    val isUrlSafe: Boolean = false,
    val isError: Boolean = false,
    val decodeInfo: Base64DecodeInfo? = null,
)
