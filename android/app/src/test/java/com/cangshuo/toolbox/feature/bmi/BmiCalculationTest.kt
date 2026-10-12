package com.cangshuo.toolbox.feature.bmi

import com.cangshuo.toolbox.feature.bmi.data.LocalBmiRepository
import com.cangshuo.toolbox.feature.bmi.domain.BmiBand
import com.cangshuo.toolbox.feature.bmi.domain.BmiError
import com.cangshuo.toolbox.feature.bmi.domain.BmiLimits
import com.cangshuo.toolbox.feature.bmi.domain.BmiOutcome
import com.cangshuo.toolbox.feature.bmi.domain.BmiStandard
import com.cangshuo.toolbox.feature.bmi.domain.CalculateBmiUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

class BmiCalculationTest {
    private val calculate = CalculateBmiUseCase(LocalBmiRepository())

    private fun success(heightCm: Double, weightKg: Double, standard: BmiStandard) =
        (calculate(heightCm, weightKg, standard) as BmiOutcome.Success).result

    @Test
    fun chinaStandardUsesWsT428Cutoffs() {
        assertEquals(BmiBand.NORMAL, success(170.0, 65.0, BmiStandard.CHINA).band)
        assertEquals(BmiBand.OVERWEIGHT, success(170.0, 72.0, BmiStandard.CHINA).band)
        assertEquals(BmiBand.OBESE, success(170.0, 82.0, BmiStandard.CHINA).band)
        assertEquals(BmiBand.UNDERWEIGHT, success(170.0, 52.0, BmiStandard.CHINA).band)
    }

    @Test
    fun whoStandardExposesThinnessGradesAndObesityClasses() {
        assertEquals(BmiBand.SEVERE_THINNESS, success(170.0, 45.9, BmiStandard.WHO).band)
        assertEquals(BmiBand.MODERATE_THINNESS, success(170.0, 47.7, BmiStandard.WHO).band)
        assertEquals(BmiBand.MILD_THINNESS, success(170.0, 50.5, BmiStandard.WHO).band)
        assertEquals(BmiBand.OVERWEIGHT, success(170.0, 78.0, BmiStandard.WHO).band)
        assertEquals(BmiBand.OBESE_I, success(170.0, 89.5, BmiStandard.WHO).band)
        assertEquals(BmiBand.OBESE_II, success(170.0, 107.0, BmiStandard.WHO).band)
        assertEquals(BmiBand.OBESE_III, success(170.0, 118.5, BmiStandard.WHO).band)
    }

    @Test
    fun healthyWeightRangeUsesTheConventionalUpperBound() {
        val china = success(170.0, 65.0, BmiStandard.CHINA)
        assertEquals(53.5, china.healthyMinKg, 0.05)
        assertEquals(69.1, china.healthyMaxKg, 0.05)
        val who = success(170.0, 65.0, BmiStandard.WHO)
        assertEquals(72.0, who.healthyMaxKg, 0.05)
    }

    @Test
    fun imperialFactorsMatchTheExactDefinitions() {
        val ukHeightCm = 67.0 * 2.54
        val ukWeightKg = 143.0 * 0.45359237
        val definition = success(ukHeightCm, ukWeightKg, BmiStandard.WHO).bmi
        val toolConstants = success(
            67.0 * BmiLimits.CM_PER_INCH,
            143.0 * BmiLimits.KG_PER_POUND,
            BmiStandard.WHO,
        ).bmi
        assertEquals(definition, toolConstants, 1e-12)
    }

    @Test
    fun outOfRangeInputsAreRejected() {
        assertEquals(BmiError.OUT_OF_RANGE, (calculate(49.0, 65.0, BmiStandard.WHO) as BmiOutcome.Failure).error)
        assertEquals(BmiError.OUT_OF_RANGE, (calculate(170.0, 600.0, BmiStandard.WHO) as BmiOutcome.Failure).error)
        assertEquals(BmiError.INVALID, (calculate(Double.NaN, 65.0, BmiStandard.WHO) as BmiOutcome.Failure).error)
    }
}
