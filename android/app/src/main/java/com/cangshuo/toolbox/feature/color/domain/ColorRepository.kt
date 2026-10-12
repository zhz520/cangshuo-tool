package com.cangshuo.toolbox.feature.color.domain

interface ColorRepository {
    /** Parses HEX / rgb() / hsl() / hsv() input; values are range-checked before conversion. */
    fun parse(input: String): ColorParseResult

    /** Formats every supported representation of [color]. */
    fun describe(color: RgbColor): ColorDescription
}
