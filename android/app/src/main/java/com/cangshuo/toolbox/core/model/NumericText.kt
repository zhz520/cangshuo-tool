package com.cangshuo.toolbox.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

// Plain decimal input: optional sign, digits and at most one dot. No grouping
// separators, exponents or locale-specific punctuation, so the semantics of a
// value never depend on the device locale.
private val PlainDecimalPattern = Regex("^[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)$")

fun parseDecimalText(text: String, maxLength: Int = 24): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > maxLength || !PlainDecimalPattern.matches(trimmed)) return null
    return trimmed.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** Money-safe variant of [parseDecimalText] returning a [BigDecimal]. */
fun parseDecimalAmount(text: String, maxLength: Int = 24): BigDecimal? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > maxLength || !PlainDecimalPattern.matches(trimmed)) return null
    return try {
        BigDecimal(trimmed)
    } catch (_: NumberFormatException) {
        null
    }
}

/** Rounds half-up and optionally groups thousands for display. */
fun formatAmount(value: BigDecimal, decimals: Int = 2, grouping: Boolean = true): String {
    val fraction = if (decimals > 0) "." + "0".repeat(decimals) else ""
    val pattern = (if (grouping) "#,##0" else "0") + fraction
    val format = DecimalFormat(pattern)
    format.isGroupingUsed = grouping
    format.roundingMode = RoundingMode.HALF_UP
    return format.format(value)
}

fun formatAmount(value: Double, decimals: Int = 2, grouping: Boolean = true): String =
    formatAmount(BigDecimal.valueOf(value), decimals, grouping)
