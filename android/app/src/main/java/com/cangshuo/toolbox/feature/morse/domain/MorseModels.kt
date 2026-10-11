package com.cangshuo.toolbox.feature.morse.domain

enum class MorseDirection { ENCODE, DECODE }

enum class MorseError { EMPTY, INVALID_SYMBOL }

/** One audio event in a Morse timeline; duration is a multiple of the Morse unit. */
sealed interface MorseTone {
    /** Duration in Morse units. */
    val units: Int

    /** A sounding dot (1 unit) or dash (3 units). */
    data class Sound(override val units: Int) : MorseTone

    /** A gap: 1 unit inside a letter, 3 units between letters, 7 units between words. */
    data class Gap(override val units: Int) : MorseTone
}

sealed interface MorseResult {
    data class Success(
        val output: String,
        /** Code points that have no Morse representation (encode direction only). */
        val unsupported: List<String>,
        val symbolCount: Int,
    ) : MorseResult

    data class Error(
        val reason: MorseError,
        /** 1-based symbol position for INVALID_SYMBOL; null otherwise. */
        val position: Int? = null,
    ) : MorseResult
}
