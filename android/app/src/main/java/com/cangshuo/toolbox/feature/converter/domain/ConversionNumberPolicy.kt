package com.cangshuo.toolbox.feature.converter.domain

import java.math.BigDecimal

/** Bounds decimal work before arithmetic or expansion into a plain string. */
internal object ConversionNumberPolicy {
    const val MAX_INPUT_LENGTH = 64
    private const val MAX_PRECISION = 64
    private const val MAX_ABSOLUTE_SCALE = 1_000
    private const val MAX_ABSOLUTE_EXPONENT = 1_000L
    private const val MAX_PLAIN_RESULT_LENGTH = 128L

    fun isSupported(value: BigDecimal): Boolean {
        if (value.precision() > MAX_PRECISION ||
            value.scale() !in -MAX_ABSOLUTE_SCALE..MAX_ABSOLUTE_SCALE
        ) {
            return false
        }
        if (value.signum() == 0) return true
        val exponent = value.precision().toLong() - value.scale().toLong() - 1L
        return exponent in -MAX_ABSOLUTE_EXPONENT..MAX_ABSOLUTE_EXPONENT
    }

    fun format(value: BigDecimal): String {
        require(isSupported(value))
        val normalized = if (value.signum() == 0) BigDecimal.ZERO else value.stripTrailingZeros()
        val precision = normalized.precision().toLong()
        val scale = normalized.scale().toLong()
        val signLength = if (normalized.signum() < 0) 1L else 0L
        val plainLength = signLength + when {
            scale <= 0L -> precision - scale
            precision > scale -> precision + 1L
            else -> scale + 2L
        }

        // Never allocate an expanded string merely to find out that it is too long.
        return if (plainLength <= MAX_PLAIN_RESULT_LENGTH) {
            normalized.toPlainString()
        } else {
            normalized.toString()
        }
    }
}
