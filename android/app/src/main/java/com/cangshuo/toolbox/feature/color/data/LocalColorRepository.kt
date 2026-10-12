package com.cangshuo.toolbox.feature.color.data

import com.cangshuo.toolbox.feature.color.domain.ColorDescription
import com.cangshuo.toolbox.feature.color.domain.ColorError
import com.cangshuo.toolbox.feature.color.domain.ColorLimits
import com.cangshuo.toolbox.feature.color.domain.ColorParseResult
import com.cangshuo.toolbox.feature.color.domain.ColorRepository
import com.cangshuo.toolbox.feature.color.domain.HslColor
import com.cangshuo.toolbox.feature.color.domain.HsvColor
import com.cangshuo.toolbox.feature.color.domain.RgbColor
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Local colour maths. Formulas follow the classic sRGB conversions (hex/rgb/hsl/hsv)
 * with the rounding rules documented in docs/COLOR_CONVERTER.md; hex output is exact,
 * hues are displayed as whole degrees and percentages keep one decimal.
 */
class LocalColorRepository : ColorRepository {

    override fun parse(input: String): ColorParseResult {
        val text = input.trim()
        if (text.isEmpty()) return ColorParseResult.Failure(ColorError.INVALID_FORMAT)
        if (text.length > ColorLimits.MAX_INPUT_LENGTH) {
            return ColorParseResult.Failure(ColorError.INPUT_TOO_LONG)
        }
        val lower = text.lowercase(Locale.ROOT)
        if (lower.startsWith("#") || text.all { it.isHexDigit() }) return parseHex(text)
        if (lower.startsWith("rgb")) return parseRgb(functionBody(text, "rgb") ?: return invalid())
        if (lower.startsWith("hsl")) return parseHslOrHsv(text, "hsl")
        if (lower.startsWith("hsv")) return parseHslOrHsv(text, "hsv")
        if (text.all { it.isDigit() || it == ',' || it == ' ' || it == '\t' }) {
            return parseRgb(text)
        }
        return invalid()
    }

    override fun describe(color: RgbColor): ColorDescription = ColorDescription(
        color = color,
        hex = hex(color),
        rgb = rgb(color),
        hsv = hsv(color),
        hsl = hsl(color),
    )

    fun toHsl(color: RgbColor): HslColor {
        val r = color.red / 255.0
        val g = color.green / 255.0
        val b = color.blue / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val hue = when {
            delta == 0.0 -> 0.0
            max == r -> 60 * (((g - b) / delta) % 6)
            max == g -> 60 * (((b - r) / delta) + 2)
            else -> 60 * (((r - g) / delta) + 4)
        }
        val lightness = (max + min) / 2
        val saturation = when {
            delta == 0.0 -> 0.0
            lightness <= 0.5 -> delta / (max + min)
            else -> delta / (2 - max - min)
        }
        return HslColor(normalizeHue(hue), saturation * 100, lightness * 100)
    }

    fun toHsv(color: RgbColor): HsvColor {
        val r = color.red / 255.0
        val g = color.green / 255.0
        val b = color.blue / 255.0
        val value = maxOf(r, g, b)
        val delta = value - minOf(r, g, b)
        if (delta == 0.0) return HsvColor(0.0, 0.0, value * 100)
        val saturation = delta / value
        val rdif = (value - r) / 6 / delta + 0.5
        val gdif = (value - g) / 6 / delta + 0.5
        val bdif = (value - b) / 6 / delta + 0.5
        var hueFraction = when (value) {
            r -> bdif - gdif
            g -> 1.0 / 3 + rdif - bdif
            else -> 2.0 / 3 + gdif - rdif
        }
        if (hueFraction < 0) hueFraction += 1 else if (hueFraction > 1) hueFraction -= 1
        return HsvColor(normalizeHue(hueFraction * 360), saturation * 100, value * 100)
    }

    fun fromHsl(hsl: HslColor): RgbColor {
        val hue = normalizeHue(hsl.hue) / 360
        val saturation = (hsl.saturation / 100).coerceIn(0.0, 1.0)
        val lightness = (hsl.lightness / 100).coerceIn(0.0, 1.0)
        if (saturation == 0.0) {
            val grey = (lightness * 255).roundToInt().coerceIn(0, 255)
            return RgbColor(grey, grey, grey)
        }
        val q = if (lightness < 0.5) lightness * (1 + saturation) else lightness + saturation - lightness * saturation
        val p = 2 * lightness - q
        fun channel(offset: Double): Int {
            var t = hue + offset
            if (t < 0) t += 1
            if (t > 1) t -= 1
            val value = when {
                t < 1.0 / 6 -> p + (q - p) * 6 * t
                t < 1.0 / 2 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
            return (value * 255).roundToInt().coerceIn(0, 255)
        }
        return RgbColor(channel(1.0 / 3), channel(0.0), channel(-1.0 / 3))
    }

    fun fromHsv(hsv: HsvColor): RgbColor {
        val hue = normalizeHue(hsv.hue)
        val saturation = (hsv.saturation / 100).coerceIn(0.0, 1.0)
        val value = (hsv.value / 100).coerceIn(0.0, 1.0)
        val sector = (hue / 60).toInt().coerceIn(0, 5)
        val fraction = hue / 60 - sector
        val p = value * (1 - saturation)
        val q = value * (1 - saturation * fraction)
        val t = value * (1 - saturation * (1 - fraction))
        val (red, green, blue) = when (sector) {
            0 -> Triple(value, t, p)
            1 -> Triple(q, value, p)
            2 -> Triple(p, value, t)
            3 -> Triple(p, q, value)
            4 -> Triple(t, p, value)
            else -> Triple(value, p, q)
        }
        return RgbColor(
            (red * 255).roundToInt().coerceIn(0, 255),
            (green * 255).roundToInt().coerceIn(0, 255),
            (blue * 255).roundToInt().coerceIn(0, 255),
        )
    }

    fun hex(color: RgbColor): String =
        String.format(Locale.ROOT, "#%02X%02X%02X", color.red, color.green, color.blue)

    fun rgb(color: RgbColor): String = "rgb(${color.red}, ${color.green}, ${color.blue})"

    fun hsl(color: RgbColor): String {
        val hsl = toHsl(color)
        return "hsl(${Math.round(hsl.hue) % 360}, ${decimal(hsl.saturation)}%, ${decimal(hsl.lightness)}%)"
    }

    fun hsv(color: RgbColor): String {
        val hsv = toHsv(color)
        return "hsv(${Math.round(hsv.hue) % 360}, ${decimal(hsv.saturation)}%, ${decimal(hsv.value)}%)"
    }

    private fun parseHex(text: String): ColorParseResult {
        val digits = text.removePrefix("#")
        if (!digits.all { it.isHexDigit() }) return invalid()
        return when (digits.length) {
            3 -> ColorParseResult.Success(
                RgbColor(
                    ("" + digits[0]).repeat(2).toInt(16),
                    ("" + digits[1]).repeat(2).toInt(16),
                    ("" + digits[2]).repeat(2).toInt(16),
                ),
            )

            6 -> ColorParseResult.Success(
                RgbColor(
                    digits.substring(0, 2).toInt(16),
                    digits.substring(2, 4).toInt(16),
                    digits.substring(4, 6).toInt(16),
                ),
            )

            8 -> ColorParseResult.Failure(ColorError.UNSUPPORTED_ALPHA)
            else -> invalid()
        }
    }

    private fun parseRgb(body: String?): ColorParseResult {
        if (body == null) return invalid()
        val parts = body.split(',', ' ', '\t').filter { it.isNotBlank() }
        if (parts.size != 3) return invalid()
        val channels = parts.map { it.toIntOrNull() ?: return invalid() }
        if (channels.any { it < 0 || it > ColorLimits.MAX_CHANNEL }) {
            return ColorParseResult.Failure(ColorError.OUT_OF_RANGE)
        }
        return ColorParseResult.Success(RgbColor(channels[0], channels[1], channels[2]))
    }

    private fun parseHslOrHsv(text: String, name: String): ColorParseResult {
        val body = functionBody(text, name) ?: return invalid()
        val parts = body.split(',', ' ', '\t').filter { it.isNotBlank() }
        if (parts.size != 3) return invalid()
        val hue = parts[0].removeSuffix("%").toDoubleOrNull() ?: return invalid()
        if (!hue.isFinite()) return invalid()
        val first = parsePercent(parts[1]) ?: return invalid()
        val second = parsePercent(parts[2]) ?: return invalid()
        if (first < 0 || second < 0 || first > ColorLimits.MAX_PERCENT || second > ColorLimits.MAX_PERCENT) {
            return ColorParseResult.Failure(ColorError.OUT_OF_RANGE)
        }
        val color = if (name == "hsl") {
            fromHsl(HslColor(hue, first, second))
        } else {
            fromHsv(HsvColor(hue, first, second))
        }
        return ColorParseResult.Success(color)
    }

    private fun parsePercent(raw: String): Double? {
        val value = raw.removeSuffix("%").toDoubleOrNull() ?: return null
        if (!value.isFinite()) return null
        return value
    }

    private fun functionBody(text: String, name: String): String? {
        if (text.length <= name.length + 2) return null
        if (!text.regionMatches(0, name, 0, name.length, ignoreCase = true)) return null
        if (text[name.length] != '(' || !text.endsWith(")")) return null
        return text.substring(name.length + 1, text.length - 1)
    }

    private fun invalid(): ColorParseResult = ColorParseResult.Failure(ColorError.INVALID_FORMAT)

    private fun normalizeHue(hue: Double): Double {
        val wrapped = hue % 360
        return if (wrapped < 0) wrapped + 360 else wrapped
    }

    private fun decimal(value: Double): String =
        BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
