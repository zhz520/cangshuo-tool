package com.cangshuo.toolbox.feature.bmi.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.core.model.parseDecimalText
import com.cangshuo.toolbox.feature.bmi.domain.BmiError
import com.cangshuo.toolbox.feature.bmi.domain.BmiLimits
import com.cangshuo.toolbox.feature.bmi.domain.BmiOutcome
import com.cangshuo.toolbox.feature.bmi.domain.BmiStandard
import com.cangshuo.toolbox.feature.bmi.domain.CalculateBmiUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class BmiViewModel(
    private val calculateBmi: CalculateBmiUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val heightText = savedStateHandle.getStateFlow(KEY_HEIGHT, "")
    private val weightText = savedStateHandle.getStateFlow(KEY_WEIGHT, "")
    private val imperial = savedStateHandle.getStateFlow(KEY_IMPERIAL, false)
    private val standardName = savedStateHandle.getStateFlow(KEY_STANDARD, BmiStandard.CHINA.name)
    private val rejectedInput = MutableStateFlow(false)

    val uiState: StateFlow<BmiUiState> = combine(
        heightText, weightText, imperial, standardName,
    ) { height, weight, isImperial, standard ->
        buildState(height, weight, isImperial, resolveStandard(standard))
    }.combine(rejectedInput) { state, rejected ->
        if (rejected) state.copy(outcome = BmiOutcome.Failure(BmiError.INPUT_TOO_LONG)) else state
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        buildState(heightText.value, weightText.value, imperial.value, resolveStandard(standardName.value)),
    )

    fun updateHeight(text: String) {
        update(KEY_HEIGHT, text)
    }

    fun updateWeight(text: String) {
        update(KEY_WEIGHT, text)
    }

    fun selectUnits(isImperial: Boolean) {
        savedStateHandle[KEY_IMPERIAL] = isImperial
    }

    fun selectStandard(standard: BmiStandard) {
        savedStateHandle[KEY_STANDARD] = standard.name
    }

    fun clear() {
        rejectedInput.value = false
        savedStateHandle[KEY_HEIGHT] = ""
        savedStateHandle[KEY_WEIGHT] = ""
    }

    private fun update(key: String, text: String) {
        rejectedInput.value = text.length > BmiLimits.MAX_INPUT_LENGTH
        if (!rejectedInput.value) savedStateHandle[key] = text
    }

    private fun buildState(height: String, weight: String, isImperial: Boolean, standard: BmiStandard): BmiUiState {
        val base = BmiUiState(heightText = height, weightText = weight, imperial = isImperial, standard = standard)
        val heightValue = if (height.isBlank()) null else parseDecimalText(height, BmiLimits.MAX_INPUT_LENGTH)
        val weightValue = if (weight.isBlank()) null else parseDecimalText(weight, BmiLimits.MAX_INPUT_LENGTH)
        if ((height.isNotBlank() && (heightValue == null || heightValue <= 0.0)) ||
            (weight.isNotBlank() && (weightValue == null || weightValue <= 0.0))
        ) {
            return base.copy(outcome = BmiOutcome.Failure(BmiError.INVALID))
        }
        if (heightValue == null || weightValue == null) return base
        val heightCm = if (isImperial) heightValue * BmiLimits.CM_PER_INCH else heightValue
        val weightKg = if (isImperial) weightValue * BmiLimits.KG_PER_POUND else weightValue
        return base.copy(outcome = calculateBmi(heightCm, weightKg, standard))
    }

    private fun resolveStandard(name: String): BmiStandard =
        BmiStandard.entries.firstOrNull { it.name == name } ?: BmiStandard.CHINA

    companion object {
        private const val KEY_HEIGHT = "height"
        private const val KEY_WEIGHT = "weight"
        private const val KEY_IMPERIAL = "imperial"
        private const val KEY_STANDARD = "standard"

        fun factory(calculateBmi: CalculateBmiUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    return BmiViewModel(calculateBmi, savedStateHandle) as T
                }
            }
    }
}
