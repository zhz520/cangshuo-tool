package com.cangshuo.toolbox.feature.fuel.domain

enum class DistanceUnit { KM, MILE }

enum class FuelUnit { LITER, US_GALLON, UK_GALLON }

data class FuelInput(
    val distance: Double,
    val distanceUnit: DistanceUnit,
    val fuel: Double,
    val fuelUnit: FuelUnit,
    /** Optional fuel price per litre in yuan. */
    val pricePerLiter: Double?,
)

data class FuelResult(
    val distanceKm: Double,
    val fuelLiters: Double,
    val litersPer100Km: Double,
    val kmPerLiter: Double,
    val mpgUs: Double,
    val mpgUk: Double,
    val totalCost: Double?,
    val costPerKm: Double?,
    val costPer100Km: Double?,
)

enum class FuelError { INVALID, RANGE, INPUT_TOO_LONG }

object FuelLimits {
    const val MAX_INPUT_LENGTH = 24
    const val MIN_DISTANCE_KM = 0.1
    const val MAX_DISTANCE_KM = 100000.0
    const val MIN_FUEL_LITERS = 0.1
    const val MAX_FUEL_LITERS = 10000.0
    const val MAX_PRICE_PER_LITER = 100.0
    const val KM_PER_MILE = 1.609344
    const val LITERS_PER_US_GALLON = 3.785411784
    const val LITERS_PER_UK_GALLON = 4.54609
    const val MPG_US_FACTOR = 235.214583
    const val MPG_UK_FACTOR = 282.480936
}

sealed interface FuelOutcome {
    data class Success(val result: FuelResult) : FuelOutcome
    data class Failure(val error: FuelError) : FuelOutcome
}
