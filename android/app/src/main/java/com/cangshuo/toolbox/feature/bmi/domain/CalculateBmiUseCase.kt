package com.cangshuo.toolbox.feature.bmi.domain

class CalculateBmiUseCase(private val repository: BmiRepository) {
    operator fun invoke(heightCm: Double, weightKg: Double, standard: BmiStandard): BmiOutcome =
        repository.calculate(heightCm, weightKg, standard)
}
