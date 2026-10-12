package com.cangshuo.toolbox.feature.fuel.data

import com.cangshuo.toolbox.feature.fuel.domain.DistanceUnit
import com.cangshuo.toolbox.feature.fuel.domain.FuelError
import com.cangshuo.toolbox.feature.fuel.domain.FuelInput
import com.cangshuo.toolbox.feature.fuel.domain.FuelLimits
import com.cangshuo.toolbox.feature.fuel.domain.FuelOutcome
import com.cangshuo.toolbox.feature.fuel.domain.FuelRepository
import com.cangshuo.toolbox.feature.fuel.domain.FuelResult
import com.cangshuo.toolbox.feature.fuel.domain.FuelUnit

/**
 * Fill-up window math: distance and refuelled volume are normalised to km and
 * litres first, then every display unit derives from that pair. Exact
 * definitions are used: 1 mile = 1.609344 km, 1 US gallon = 3.785411784 L,
 * 1 UK gallon = 4.54609 L.
 */
class LocalFuelRepository : FuelRepository {

    override fun calculate(input: FuelInput): FuelOutcome {
        val distanceKm = input.distance * when (input.distanceUnit) {
            DistanceUnit.KM -> 1.0
            DistanceUnit.MILE -> FuelLimits.KM_PER_MILE
        }
        val fuelLiters = input.fuel * when (input.fuelUnit) {
            FuelUnit.LITER -> 1.0
            FuelUnit.US_GALLON -> FuelLimits.LITERS_PER_US_GALLON
            FuelUnit.UK_GALLON -> FuelLimits.LITERS_PER_UK_GALLON
        }
        if (!distanceKm.isFinite() || !fuelLiters.isFinite() || distanceKm <= 0.0 || fuelLiters <= 0.0) {
            return FuelOutcome.Failure(FuelError.INVALID)
        }
        if (distanceKm < FuelLimits.MIN_DISTANCE_KM || distanceKm > FuelLimits.MAX_DISTANCE_KM ||
            fuelLiters < FuelLimits.MIN_FUEL_LITERS || fuelLiters > FuelLimits.MAX_FUEL_LITERS
        ) {
            return FuelOutcome.Failure(FuelError.RANGE)
        }
        val price = input.pricePerLiter
        if (price != null && (!price.isFinite() || price < 0.0 || price > FuelLimits.MAX_PRICE_PER_LITER)) {
            return FuelOutcome.Failure(FuelError.RANGE)
        }

        val litersPer100Km = fuelLiters / distanceKm * 100.0
        val kmPerLiter = distanceKm / fuelLiters
        val totalCost = price?.let { it * fuelLiters }
        val costPerKm = price?.let { it * fuelLiters / distanceKm }
        val costPer100Km = price?.let { it * fuelLiters / distanceKm * 100.0 }
        return FuelOutcome.Success(
            FuelResult(
                distanceKm = distanceKm,
                fuelLiters = fuelLiters,
                litersPer100Km = litersPer100Km,
                kmPerLiter = kmPerLiter,
                mpgUs = FuelLimits.MPG_US_FACTOR / litersPer100Km,
                mpgUk = FuelLimits.MPG_UK_FACTOR / litersPer100Km,
                totalCost = totalCost,
                costPerKm = costPerKm,
                costPer100Km = costPer100Km,
            ),
        )
    }
}
