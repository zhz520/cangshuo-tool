package com.cangshuo.toolbox.feature.fuel

import com.cangshuo.toolbox.feature.fuel.data.LocalFuelRepository
import com.cangshuo.toolbox.feature.fuel.domain.CalculateFuelUseCase
import com.cangshuo.toolbox.feature.fuel.domain.DistanceUnit
import com.cangshuo.toolbox.feature.fuel.domain.FuelError
import com.cangshuo.toolbox.feature.fuel.domain.FuelInput
import com.cangshuo.toolbox.feature.fuel.domain.FuelLimits
import com.cangshuo.toolbox.feature.fuel.domain.FuelOutcome
import com.cangshuo.toolbox.feature.fuel.domain.FuelUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class FuelCalculationTest {
    private val calculate = CalculateFuelUseCase(LocalFuelRepository())

    private fun success(input: FuelInput) = (calculate(input) as FuelOutcome.Success).result

    private fun metric(distance: Double, fuel: Double, price: Double? = null) =
        success(FuelInput(distance, DistanceUnit.KM, fuel, FuelUnit.LITER, price))

    @Test
    fun metricWindowConvertsToEveryDisplayUnit() {
        val result = metric(520.0, 38.0)
        assertEquals(7.31, result.litersPer100Km, 0.01)
        assertEquals(13.68, result.kmPerLiter, 0.01)
        assertEquals(32.19, result.mpgUs, 0.01)
        assertEquals(38.65, result.mpgUk, 0.01)
    }

    @Test
    fun imperialInputMatchesTheMetricEquivalent() {
        val imperial = success(
            FuelInput(100.0, DistanceUnit.MILE, 5.0, FuelUnit.US_GALLON, null),
        )
        val metric = metric(100.0 * FuelLimits.KM_PER_MILE, 5.0 * FuelLimits.LITERS_PER_US_GALLON)
        assertEquals(metric.litersPer100Km, imperial.litersPer100Km, 1e-9)
        val uk = success(FuelInput(100.0, DistanceUnit.MILE, 5.0, FuelUnit.UK_GALLON, null))
        val ukExpected = 5.0 * FuelLimits.LITERS_PER_UK_GALLON / (100.0 * FuelLimits.KM_PER_MILE) * 100.0
        assertEquals(ukExpected, uk.litersPer100Km, 1e-9)
    }

    @Test
    fun priceProducesCostPerDistanceEstimates() {
        val result = metric(520.0, 38.0, 7.85)
        assertEquals(298.3, result.totalCost!!, 0.01)
        assertEquals(0.574, result.costPerKm!!, 0.001)
        assertEquals(57.37, result.costPer100Km!!, 0.01)
        assertEquals(null, metric(520.0, 38.0).totalCost)
    }

    @Test
    fun invalidInputsAreRejected() {
        assertEquals(
            FuelError.INVALID,
            (calculate(FuelInput(0.0, DistanceUnit.KM, 10.0, FuelUnit.LITER, null)) as FuelOutcome.Failure).error,
        )
        assertEquals(
            FuelError.RANGE,
            (calculate(FuelInput(200000.0, DistanceUnit.KM, 10.0, FuelUnit.LITER, null)) as FuelOutcome.Failure).error,
        )
        assertEquals(
            FuelError.RANGE,
            (calculate(FuelInput(500.0, DistanceUnit.KM, 40.0, FuelUnit.LITER, -1.0)) as FuelOutcome.Failure).error,
        )
    }
}
