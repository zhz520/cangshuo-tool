package com.cangshuo.toolbox.feature.calculator.domain

interface CalculatorRepository {
    /** Bounded, local computation only. No network, files or user history. */
    fun calculate(expression: String): CalculationResult
}
