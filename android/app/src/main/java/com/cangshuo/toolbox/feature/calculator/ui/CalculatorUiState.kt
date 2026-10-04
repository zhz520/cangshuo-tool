package com.cangshuo.toolbox.feature.calculator.ui

import com.cangshuo.toolbox.feature.calculator.domain.CalculationError

data class CalculatorUiState(
    val expression: String = "",
    val selectionStart: Int = expression.length,
    val selectionEnd: Int = expression.length,
    val result: String? = null,
    val error: CalculationError? = null,
)
