package com.cangshuo.toolbox.feature.radix.data

import com.cangshuo.toolbox.feature.radix.domain.RadixError
import com.cangshuo.toolbox.feature.radix.domain.RadixInput
import com.cangshuo.toolbox.feature.radix.domain.RadixOutput
import com.cangshuo.toolbox.feature.radix.domain.RadixRepository
import com.cangshuo.toolbox.feature.radix.domain.RadixResult
import java.math.BigInteger

/**
 * Arbitrary-precision radix conversion on top of [BigInteger].
 * Digits are 0-9 then a-z (case-insensitive); output case follows [uppercase].
 */
class LocalRadixRepository : RadixRepository {

    override fun convert(rawInput: String, explicitRadix: Int?, uppercase: Boolean): RadixResult {
        val text = rawInput.trim()
        if (text.isEmpty()) return RadixResult.Error(RadixError.EMPTY)

        val prefix = detectPrefix(text)
        // A prefix applies in auto mode, or when it matches the explicit base.
        val usePrefix = prefix != null && (explicitRadix == null || explicitRadix == prefix)
        val digits = if (usePrefix) text.substring(2) else text
        if (digits.isEmpty()) return RadixResult.Error(RadixError.EMPTY)

        val inRadix = explicitRadix ?: prefix ?: 10
        if (inRadix !in MIN_RADIX..MAX_RADIX) return RadixResult.Error(RadixError.INVALID_CHAR, null)

        var digitCount = 0
        for ((index, ch) in digits.withIndex()) {
            if (ch == ' ' || ch == '_' || ch == ',' || ch == '\'') continue
            val value = digitValue(ch) ?: return RadixResult.Error(RadixError.INVALID_CHAR, index + 1)
            if (value >= inRadix) return RadixResult.Error(RadixError.INVALID_CHAR, index + 1)
            digitCount++
        }
        if (digitCount == 0) return RadixResult.Error(RadixError.EMPTY)

        val cleaned = digits.filterNot { it == ' ' || it == '_' || it == ',' || it == '\'' }
        val number = runCatching { BigInteger(cleaned, inRadix) }.getOrNull()
            ?: return RadixResult.Error(RadixError.INVALID_CHAR, null)

        val upper = uppercase
        val outputs = OUTPUT_RADIXES.map { radix ->
            RadixOutput(
                radix = radix,
                label = labelFor(radix),
                value = render(number, radix, upper),
            )
        }
        return RadixResult.Success(
            input = RadixInput(text = text, radix = inRadix, autoDetected = explicitRadix == null),
            normalized = render(number, 10, upper),
            outputs = outputs,
            bitLength = number.bitLength(),
            digitCount = digitCount,
            groupCount = (number.bitLength() + 7) / 8,
        )
    }

    private fun detectPrefix(text: String): Int? = when {
        text.length >= 2 && text[0] == '0' && (text[1] == 'x' || text[1] == 'X') -> 16
        text.length >= 2 && text[0] == '0' && (text[1] == 'b' || text[1] == 'B') -> 2
        text.length >= 2 && text[0] == '0' && (text[1] == 'o' || text[1] == 'O') -> 8
        else -> null
    }

    private fun digitValue(ch: Char): Int? = when (ch) {
        in '0'..'9' -> ch - '0'
        in 'a'..'z' -> ch - 'a' + 10
        in 'A'..'Z' -> ch - 'A' + 10
        else -> null
    }

    private fun render(number: BigInteger, radix: Int, uppercase: Boolean): String {
        val rendered = number.toString(radix)
        return if (uppercase && radix > 10) rendered.uppercase() else rendered
    }

    private fun labelFor(radix: Int): String = when (radix) {
        2 -> "BIN"
        8 -> "OCT"
        10 -> "DEC"
        16 -> "HEX"
        36 -> "B36"
        else -> "B$radix"
    }

    companion object {
        const val MIN_RADIX = 2
        const val MAX_RADIX = 36
        val OUTPUT_RADIXES = listOf(2, 8, 10, 16, 36)
    }
}
