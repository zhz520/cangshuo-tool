package com.cangshuo.toolbox.feature.morse.domain

interface MorseRepository {
    /** Plain text to Morse; unsupported characters are reported, not replaced. */
    fun encode(text: String): MorseResult

    /** Morse to plain text; the first invalid symbol reports its 1-based index. */
    fun decode(code: String): MorseResult

    /** Builds the sounding timeline; null when the input has nothing playable. */
    fun timeline(text: String, direction: MorseDirection): List<MorseTone>?
}
