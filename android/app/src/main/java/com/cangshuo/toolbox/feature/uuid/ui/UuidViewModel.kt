package com.cangshuo.toolbox.feature.uuid.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.uuid.domain.GenerateUuidUseCase
import com.cangshuo.toolbox.feature.uuid.domain.UuidConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class UuidViewModel(
    private val generateUuidUseCase: GenerateUuidUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val count = savedStateHandle.getStateFlow(KEY_COUNT, 1)
    private val isUppercase = savedStateHandle.getStateFlow(KEY_UPPERCASE, false)
    private val hasHyphens = savedStateHandle.getStateFlow(KEY_HYPHENS, true)
    private val isBraced = savedStateHandle.getStateFlow(KEY_BRACED, false)

    // Raw canonical UUID strings (lowercase with hyphens); presentation is applied by the UseCase.
    private val rawUuids = MutableStateFlow(generateUuidUseCase.generateRaw(count.value))

    val uiState: StateFlow<UuidUiState> = combine(
        count,
        isUppercase,
        hasHyphens,
        isBraced,
        rawUuids,
    ) { cnt, upper, hyphens, braced, raws ->
        val config = configOf(cnt, upper, hyphens, braced)
        val formattedList = raws.map { raw -> generateUuidUseCase.format(raw, config) }
        UuidUiState(
            count = cnt,
            isUppercase = upper,
            hasHyphens = hyphens,
            isBraced = braced,
            generatedUuids = formattedList,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        initialUiState(),
    )

    private fun configOf(
        count: Int,
        isUppercase: Boolean,
        hasHyphens: Boolean,
        isBraced: Boolean,
    ): UuidConfig = UuidConfig(
        count = count.coerceIn(MIN_COUNT, MAX_COUNT),
        isUppercase = isUppercase,
        hasHyphens = hasHyphens,
        isBraced = isBraced,
    )

    private fun initialUiState(): UuidUiState {
        val config = configOf(count.value, isUppercase.value, hasHyphens.value, isBraced.value)
        return UuidUiState(
            count = config.count,
            isUppercase = config.isUppercase,
            hasHyphens = config.hasHyphens,
            isBraced = config.isBraced,
            generatedUuids = rawUuids.value.map { raw -> generateUuidUseCase.format(raw, config) },
        )
    }

    fun setCount(newCount: Int) {
        val clamped = newCount.coerceIn(MIN_COUNT, MAX_COUNT)
        savedStateHandle[KEY_COUNT] = clamped
        rawUuids.value = generateUuidUseCase.generateRaw(clamped)
    }

    fun setUppercase(enabled: Boolean) {
        savedStateHandle[KEY_UPPERCASE] = enabled
    }

    fun setHyphens(enabled: Boolean) {
        savedStateHandle[KEY_HYPHENS] = enabled
    }

    fun setBraced(enabled: Boolean) {
        savedStateHandle[KEY_BRACED] = enabled
    }

    fun regenerate() {
        rawUuids.value = generateUuidUseCase.generateRaw(count.value)
    }

    companion object {
        private const val KEY_COUNT = "count"
        private const val KEY_UPPERCASE = "is_uppercase"
        private const val KEY_HYPHENS = "has_hyphens"
        private const val KEY_BRACED = "is_braced"
        private const val MIN_COUNT = 1
        private const val MAX_COUNT = 100

        fun factory(generateUuidUseCase: GenerateUuidUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    val handle = extras.createSavedStateHandle()
                    return UuidViewModel(generateUuidUseCase, handle) as T
                }
            }
    }
}
