package com.cangshuo.toolbox.feature.converter.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.converter.domain.ConversionNumberPolicy
import com.cangshuo.toolbox.feature.converter.domain.ConversionResult
import com.cangshuo.toolbox.feature.converter.domain.ConversionType
import com.cangshuo.toolbox.feature.converter.domain.ConvertUseCase
import com.cangshuo.toolbox.feature.converter.domain.UnitDef
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ConverterViewModel(
    private val useCase: ConvertUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val typeName = savedStateHandle.getStateFlow(KEY_TYPE, ConversionType.LENGTH.name)
    private val fromId = savedStateHandle.getStateFlow(KEY_FROM, "")
    private val toId = savedStateHandle.getStateFlow(KEY_TO, "")
    private val input = savedStateHandle.getStateFlow(KEY_INPUT, "")

    val uiState: StateFlow<ConverterUiState> = combine(
        typeName, fromId, toId, input,
    ) { tName, fId, tId, inp ->
        buildState(tName, fId, tId, inp)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        buildState(typeName.value, fromId.value, toId.value, input.value),
    )

    // ── User actions ────────────────────────────────────────────────────

    fun selectType(type: ConversionType) {
        savedStateHandle[KEY_TYPE] = type.name
        savedStateHandle[KEY_FROM] = ""
        savedStateHandle[KEY_TO] = ""
        savedStateHandle[KEY_INPUT] = ""
    }

    fun selectFromUnit(unit: UnitDef) {
        savedStateHandle[KEY_FROM] = unit.id
    }

    fun selectToUnit(unit: UnitDef) {
        savedStateHandle[KEY_TO] = unit.id
    }

    fun updateInput(text: String) {
        if (text.length <= ConversionNumberPolicy.MAX_INPUT_LENGTH) {
            savedStateHandle[KEY_INPUT] = text
        }
    }

    fun swap() {
        val type = resolveType(typeName.value)
        val (from, to) = resolveUnits(useCase.getUnits(type), fromId.value, toId.value)
        if (from == null || to == null) return
        savedStateHandle[KEY_FROM] = to.id
        savedStateHandle[KEY_TO] = from.id
    }

    // ── State builder ───────────────────────────────────────────────────

    private fun buildState(
        typeName: String,
        fromId: String,
        toId: String,
        input: String,
    ): ConverterUiState {
        val type = resolveType(typeName)
        val units = useCase.getUnits(type)
        val (from, to) = resolveUnits(units, fromId, toId)

        if (from == null || to == null) {
            return ConverterUiState(selectedType = type, units = units)
        }

        if (input.isBlank()) {
            return ConverterUiState(
                selectedType = type,
                units = units,
                fromUnit = from,
                toUnit = to,
                inputValue = input,
            )
        }

        val result = useCase.convert(input, from, to, type)
        return when (result) {
            ConversionResult.InvalidInput -> ConverterUiState(
                selectedType = type,
                units = units,
                fromUnit = from,
                toUnit = to,
                inputValue = input,
                isInputError = true,
            )
            is ConversionResult.Success -> ConverterUiState(
                selectedType = type,
                units = units,
                fromUnit = from,
                toUnit = to,
                inputValue = input,
                result = result.formattedValue,
            )
            ConversionResult.Error -> ConverterUiState(
                selectedType = type,
                units = units,
                fromUnit = from,
                toUnit = to,
                inputValue = input,
                isConversionError = true,
            )
        }
    }

    private fun resolveType(name: String): ConversionType =
        ConversionType.entries.firstOrNull { it.name == name } ?: ConversionType.LENGTH

    private fun resolveUnits(
        units: List<UnitDef>,
        fromId: String,
        toId: String,
    ): Pair<UnitDef?, UnitDef?> {
        val from = units.firstOrNull { it.id == fromId } ?: units.firstOrNull()
        val to = units.firstOrNull { it.id == toId } ?: units.getOrNull(1) ?: from
        return Pair(from, to)
    }

    companion object {
        private const val KEY_TYPE = "type"
        private const val KEY_FROM = "from_unit"
        private const val KEY_TO = "to_unit"
        private const val KEY_INPUT = "input"

        fun factory(useCase: ConvertUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    val handle = extras.createSavedStateHandle()
                    return ConverterViewModel(useCase, handle) as T
                }
            }
    }
}
