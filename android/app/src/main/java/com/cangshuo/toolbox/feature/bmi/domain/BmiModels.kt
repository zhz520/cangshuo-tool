package com.cangshuo.toolbox.feature.bmi.domain

/** Adult BMI classification standards offered by the tool. */
enum class BmiStandard { CHINA, WHO }

/** Weight band keys; the UI maps each to a localized label. */
enum class BmiBand {
    SEVERE_THINNESS,
    MODERATE_THINNESS,
    MILD_THINNESS,
    UNDERWEIGHT,
    NORMAL,
    OVERWEIGHT,
    OBESE,
    OBESE_I,
    OBESE_II,
    OBESE_III,
}

data class BmiResult(
    val standard: BmiStandard,
    val bmi: Double,
    val band: BmiBand,
    val healthyMinKg: Double,
    val healthyMaxKg: Double,
)

enum class BmiError { INVALID, OUT_OF_RANGE, INPUT_TOO_LONG }

object BmiLimits {
    const val MIN_HEIGHT_CM = 50.0
    const val MAX_HEIGHT_CM = 260.0
    const val MIN_WEIGHT_KG = 10.0
    const val MAX_WEIGHT_KG = 500.0
    const val CM_PER_INCH = 2.54
    const val KG_PER_POUND = 0.45359237
    const val MAX_INPUT_LENGTH = 12
}

sealed interface BmiOutcome {
    data class Success(val result: BmiResult) : BmiOutcome
    data class Failure(val error: BmiError) : BmiOutcome
}
