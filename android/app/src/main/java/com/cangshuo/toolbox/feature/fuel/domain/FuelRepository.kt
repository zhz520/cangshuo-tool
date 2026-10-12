package com.cangshuo.toolbox.feature.fuel.domain

interface FuelRepository {
    /** Converts the fill-up window to L/100km, km/L and MPG; costs are optional. */
    fun calculate(input: FuelInput): FuelOutcome
}
