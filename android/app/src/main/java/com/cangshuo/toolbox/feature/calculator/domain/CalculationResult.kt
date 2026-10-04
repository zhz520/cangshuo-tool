package com.cangshuo.toolbox.feature.calculator.domain

const val MAX_EXPRESSION_LENGTH = 256

enum class CalculationError {
    EMPTY_EXPRESSION,
    INVALID_EXPRESSION,
    DIVISION_BY_ZERO,
    INPUT_TOO_LONG,
    TOO_COMPLEX,
    OUT_OF_RANGE,
    CALCULATION_FAILED,
}

sealed interface CalculationResult {
    data class Success(val value: String) : CalculationResult
    data class Failure(val error: CalculationError) : CalculationResult
}
