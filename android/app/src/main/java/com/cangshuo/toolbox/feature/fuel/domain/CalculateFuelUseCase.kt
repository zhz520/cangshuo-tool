package com.cangshuo.toolbox.feature.fuel.domain

class CalculateFuelUseCase(private val repository: FuelRepository) {
    operator fun invoke(input: FuelInput): FuelOutcome = repository.calculate(input)
}
