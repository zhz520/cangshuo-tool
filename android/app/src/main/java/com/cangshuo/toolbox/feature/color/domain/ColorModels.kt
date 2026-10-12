package com.cangshuo.toolbox.feature.color.domain

/** sRGB colour with 8-bit channels. */
data class RgbColor(val red: Int, val green: Int, val blue: Int)

/** HSL with hue in [0, 360), saturation and lightness in [0, 100]. */
data class HslColor(val hue: Double, val saturation: Double, val lightness: Double)

/** HSV with hue in [0, 360), saturation and value in [0, 100]. */
data class HsvColor(val hue: Double, val saturation: Double, val value: Double)

/** Every representation of one parsed colour, ready to display or copy. */
data class ColorDescription(
    val color: RgbColor,
    val hex: String,
    val rgb: String,
    val hsl: String,
    val hsv: String,
)

enum class ColorError { INVALID_FORMAT, OUT_OF_RANGE, UNSUPPORTED_ALPHA, INPUT_TOO_LONG }

object ColorLimits {
    const val MAX_CHANNEL = 255
    const val MAX_PERCENT = 100.0
    const val MAX_INPUT_LENGTH = 48
}

sealed interface ColorParseResult {
    data class Success(val color: RgbColor) : ColorParseResult

    data class Failure(val error: ColorError) : ColorParseResult
}
