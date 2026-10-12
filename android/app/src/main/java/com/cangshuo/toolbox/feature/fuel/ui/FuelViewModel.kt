package com.cangshuo.toolbox.feature.fuel.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.core.model.parseDecimalText
import com.cangshuo.toolbox.feature.fuel.domain.CalculateFuelUseCase
import com.cangshuo.toolbox.feature.fuel.domain.DistanceUnit
import com.cangshuo.toolbox.feature.fuel.domain.FuelError
import com.cangshuo.toolbox.feature.fuel.domain.FuelInput
import com.cangshuo.toolbox.feature.fuel.domain.FuelLimits
import com.cangshuo.toolbox.feature.fuel.domain.FuelOutcome
import com.cangshuo.toolbox.feature.fuel.domain.FuelUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class FuelViewModel(
    private val calculateFuel: CalculateFuelUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val distanceText = savedStateHandle.getStateFlow(KEY_DISTANCE, "")
    private val fuelText = savedStateHandle.getStateFlow(KEY_FUEL, "")
    private val priceText = savedStateHandle.getStateFlow(KEY_PRICE, "")
    private val distanceUnitName = savedStateHandle.getStateFlow(KEY_DISTANCE_UNIT, DistanceUnit.KM.name)
    private val fuelUnitName = savedStateHandle.getStateFlow(KEY_FUEL_UNIT, FuelUnit.LITER.name)
    private val rejectedInput = MutableStateFlow(false)

    val uiState: StateFlow<FuelUiState> = combine(
        distanceText, fuelText, priceText, distanceUnitName, fuelUnitName,
    ) { distance, fuel, price, distanceUnit, fuelUnit ->
        buildState(distance, fuel, price, resolveDistanceUnit(distanceUnit), resolveFuelUnit(fuelUnit))
    }.combine(rejectedInput) { state, rejected ->
        if (rejected) state.copy(outcome = FuelOutcome.Failure(FuelError.INPUT_TOO_LONG)) else state
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        buildState(
            distanceText.value, fuelText.value, priceText.value,
            resolveDistanceUnit(distanceUnitName.value), resolveFuelUnit(fuelUnitName.value),
        ),
    )

    fun updateDistance(text: String) = update(KEY_DISTANCE, text)

    fun updateFuel(text: String) = update(KEY_FUEL, text)

    fun updatePrice(text: String) = update(KEY_PRICE, text)

    fun selectDistanceUnit(unit: DistanceUnit) {
        savedStateHandle[KEY_DISTANCE_UNIT] = unit.name
    }

    fun selectFuelUnit(unit: FuelUnit) {
        savedStateHandle[KEY_FUEL_UNIT] = unit.name
    }

    fun clear() {
        rejectedInput.value = false
        savedStateHandle[KEY_DISTANCE] = ""
        savedStateHandle[KEY_FUEL] = ""
        savedStateHandle[KEY_PRICE] = ""
    }

    private fun update(key: String, text: String) {
        rejectedInput.value = text.length > FuelLimits.MAX_INPUT_LENGTH
        if (!rejectedInput.value) savedStateHandle[key] = text
    }

    private fun buildState(
        distance: String,
        fuel: String,
        price: String,
        distanceUnit: DistanceUnit,
        fuelUnit: FuelUnit,
    ): FuelUiState {
        val base = FuelUiState(
            distanceText = distance,
            fuelText = fuel,
            priceText = price,
            distanceUnit = distanceUnit,
            fuelUnit = fuelUnit,
        )
        val distanceValue = if (distance.isBlank()) null else parseDecimalText(distance)
        val fuelValue = if (fuel.isBlank()) null else parseDecimalText(fuel)
        val priceValue = if (price.isBlank()) null else parseDecimalText(price)
        val distanceInvalid = distance.isNotBlank() && (distanceValue == null || distanceValue <= 0.0)
        val fuelInvalid = fuel.isNotBlank() && (fuelValue == null || fuelValue <= 0.0)
        val priceInvalid = price.isNotBlank() && (priceValue == null || priceValue < 0.0)
        if (distanceInvalid || fuelInvalid || priceInvalid) {
            return base.copy(outcome = FuelOutcome.Failure(FuelError.INVALID))
        }
        if (distanceValue == null || fuelValue == null) return base
        val input = FuelInput(
            distance = distanceValue,
            distanceUnit = distanceUnit,
            fuel = fuelValue,
            fuelUnit = fuelUnit,
            pricePerLiter = priceValue,
        )
        return base.copy(outcome = calculateFuel(input))
    }

    private fun resolveDistanceUnit(name: String): DistanceUnit =
        DistanceUnit.entries.firstOrNull { it.name == name } ?: DistanceUnit.KM

    private fun resolveFuelUnit(name: String): FuelUnit =
        FuelUnit.entries.firstOrNull { it.name == name } ?: FuelUnit.LITER

    companion object {
        private const val KEY_DISTANCE = "distance"
        private const val KEY_FUEL = "fuel"
        private const val KEY_PRICE = "price"
        private const val KEY_DISTANCE_UNIT = "distanceUnit"
        private const val KEY_FUEL_UNIT = "fuelUnit"

        fun factory(calculateFuel: CalculateFuelUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    return FuelViewModel(calculateFuel, savedStateHandle) as T
                }
            }
    }
}
