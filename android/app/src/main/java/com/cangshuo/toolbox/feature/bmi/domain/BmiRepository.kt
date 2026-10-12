package com.cangshuo.toolbox.feature.bmi.domain

interface BmiRepository {
    /** Calculates BMI for adult cut-offs; inputs are already converted to cm and kg. */
    fun calculate(heightCm: Double, weightKg: Double, standard: BmiStandard): BmiOutcome
}
