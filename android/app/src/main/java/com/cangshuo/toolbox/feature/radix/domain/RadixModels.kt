package com.cangshuo.toolbox.feature.radix.domain

/** One radix row in the conversion result. */
data class RadixOutput(
    val radix: Int,
    val label: String,
    val value: String,
)

/** Detected or explicit base for the current input. */
data class RadixInput(
    val text: String,
    val radix: Int,
    val autoDetected: Boolean,
)

enum class RadixError {
    EMPTY,
    INVALID_CHAR,
    INVALID_RADIX,
    INPUT_TOO_LARGE,
}

object RadixLimits {
    const val MAX_INPUT_LENGTH = 10_000
}

sealed interface RadixResult {
    data class Success(
        val input: RadixInput,
        val normalized: String,
        val outputs: List<RadixOutput>,
        val bitLength: Int,
        val digitCount: Int,
        val groupCount: Int,
    ) : RadixResult

    data class Error(
        val reason: RadixError,
        /** 1-based character position for INVALID_CHAR; null otherwise. */
        val position: Int? = null,
    ) : RadixResult
}
