package com.cangshuo.toolbox.feature.bmi.data

import com.cangshuo.toolbox.feature.bmi.domain.BmiBand
import com.cangshuo.toolbox.feature.bmi.domain.BmiError
import com.cangshuo.toolbox.feature.bmi.domain.BmiLimits
import com.cangshuo.toolbox.feature.bmi.domain.BmiOutcome
import com.cangshuo.toolbox.feature.bmi.domain.BmiRepository
import com.cangshuo.toolbox.feature.bmi.domain.BmiResult
import com.cangshuo.toolbox.feature.bmi.domain.BmiStandard

/**
 * Local BMI math. Cut-offs: WHO adult classification (thinness grades below
 * 18.5, overweight from 25.0, obesity classes from 30.0) and the Chinese
 * adult standard WS/T 428-2013 (overweight from 24.0, obesity from 28.0).
 * Healthy weight ranges display the conventional upper bounds 24.9 / 23.9.
 */
class LocalBmiRepository : BmiRepository {

    override fun calculate(heightCm: Double, weightKg: Double, standard: BmiStandard): BmiOutcome {
        if (!heightCm.isFinite() || !weightKg.isFinite()) return BmiOutcome.Failure(BmiError.INVALID)
        if (heightCm < BmiLimits.MIN_HEIGHT_CM || heightCm > BmiLimits.MAX_HEIGHT_CM ||
            weightKg < BmiLimits.MIN_WEIGHT_KG || weightKg > BmiLimits.MAX_WEIGHT_KG
        ) {
            return BmiOutcome.Failure(BmiError.OUT_OF_RANGE)
        }
        val meters = heightCm / 100.0
        val bmi = weightKg / (meters * meters)
        val healthyMaxBmi = when (standard) {
            BmiStandard.CHINA -> 23.9
            BmiStandard.WHO -> 24.9
        }
        return BmiOutcome.Success(
            BmiResult(
                standard = standard,
                bmi = bmi,
                band = bandOf(bmi, standard),
                healthyMinKg = 18.5 * meters * meters,
                healthyMaxKg = healthyMaxBmi * meters * meters,
            ),
        )
    }

    private fun bandOf(bmi: Double, standard: BmiStandard): BmiBand = when (standard) {
        BmiStandard.CHINA -> when {
            bmi < 18.5 -> BmiBand.UNDERWEIGHT
            bmi < 24.0 -> BmiBand.NORMAL
            bmi < 28.0 -> BmiBand.OVERWEIGHT
            else -> BmiBand.OBESE
        }

        BmiStandard.WHO -> when {
            bmi < 16.0 -> BmiBand.SEVERE_THINNESS
            bmi < 17.0 -> BmiBand.MODERATE_THINNESS
            bmi < 18.5 -> BmiBand.MILD_THINNESS
            bmi < 25.0 -> BmiBand.NORMAL
            bmi < 30.0 -> BmiBand.OVERWEIGHT
            bmi < 35.0 -> BmiBand.OBESE_I
            bmi < 40.0 -> BmiBand.OBESE_II
            else -> BmiBand.OBESE_III
        }
    }
}
