package com.cangshuo.toolbox.feature.calculator.ui

import androidx.annotation.StringRes
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.feature.calculator.domain.CalculationError

@StringRes
fun CalculationError.labelResource(): Int = when (this) {
    CalculationError.EMPTY_EXPRESSION -> R.string.calculator_error_empty
    CalculationError.INVALID_EXPRESSION -> R.string.calculator_error_invalid
    CalculationError.DIVISION_BY_ZERO -> R.string.calculator_error_zero
    CalculationError.INPUT_TOO_LONG -> R.string.calculator_error_length
    CalculationError.TOO_COMPLEX -> R.string.calculator_error_complex
    CalculationError.OUT_OF_RANGE -> R.string.calculator_error_range
    CalculationError.CALCULATION_FAILED -> R.string.calculator_error_failed
}

internal enum class CalculatorKey(@param:StringRes val label: Int, val token: String? = null) {
    CLEAR(R.string.calculator_clear),
    LEFT_PARENTHESIS(R.string.calculator_key_left, "("),
    RIGHT_PARENTHESIS(R.string.calculator_key_right, ")"),
    DELETE(R.string.calculator_delete),
    SEVEN(R.string.calculator_key_seven, "7"),
    EIGHT(R.string.calculator_key_eight, "8"),
    NINE(R.string.calculator_key_nine, "9"),
    DIVIDE(R.string.calculator_key_divide, "÷"),
    FOUR(R.string.calculator_key_four, "4"),
    FIVE(R.string.calculator_key_five, "5"),
    SIX(R.string.calculator_key_six, "6"),
    MULTIPLY(R.string.calculator_key_multiply, "×"),
    ONE(R.string.calculator_key_one, "1"),
    TWO(R.string.calculator_key_two, "2"),
    THREE(R.string.calculator_key_three, "3"),
    SUBTRACT(R.string.calculator_key_subtract, "−"),
    ZERO(R.string.calculator_key_zero, "0"),
    DECIMAL(R.string.calculator_key_decimal, "."),
    PERCENT(R.string.calculator_key_percent, "%"),
    ADD(R.string.calculator_key_add, "+"),
}
