package com.cangshuo.toolbox.feature.mortgage.domain

class CalculateMortgageUseCase(private val repository: MortgageRepository) {
    operator fun invoke(input: MortgageInput): MortgageOutcome = repository.calculate(input)
}
