package com.cangshuo.toolbox.feature.calculator.domain

class CalculateExpressionUseCase(private val repository: CalculatorRepository) {
    operator fun invoke(expression: String): CalculationResult = repository.calculate(expression)
}
