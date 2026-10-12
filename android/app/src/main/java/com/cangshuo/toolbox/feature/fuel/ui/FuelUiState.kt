package com.cangshuo.toolbox.feature.fuel.ui

import com.cangshuo.toolbox.feature.fuel.domain.DistanceUnit
import com.cangshuo.toolbox.feature.fuel.domain.FuelOutcome
import com.cangshuo.toolbox.feature.fuel.domain.FuelUnit

data class FuelUiState(
    val distanceText: String = "",
    val fuelText: String = "",
    val priceText: String = "",
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val fuelUnit: FuelUnit = FuelUnit.LITER,
    /** Null while the form is incomplete. */
    val outcome: FuelOutcome? = null,
)
