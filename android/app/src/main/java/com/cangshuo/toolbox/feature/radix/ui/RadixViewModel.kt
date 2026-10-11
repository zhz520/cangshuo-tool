package com.cangshuo.toolbox.feature.radix.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.radix.domain.ConvertRadixUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class RadixViewModel(
    private val convertRadix: ConvertRadixUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val input = savedStateHandle.getStateFlow(KEY_INPUT, "")
    private val radix = savedStateHandle.getStateFlow(KEY_RADIX, AUTO)
    private val customMode = savedStateHandle.getStateFlow(KEY_CUSTOM_MODE, false)
    private val customRadix = savedStateHandle.getStateFlow(KEY_CUSTOM_RADIX, DEFAULT_CUSTOM)
    private val uppercase = savedStateHandle.getStateFlow(KEY_UPPERCASE, false)

    val uiState: StateFlow<RadixUiState> = combine(
        input, radix, customMode, customRadix, uppercase,
    ) { inText, selRadix, isCustom, custom, isUpper ->
        val explicit = if (isCustom) custom else selRadix
        RadixUiState(
            inputText = inText,
            selectedRadix = selRadix,
            customMode = isCustom,
            customRadix = custom,
            uppercase = isUpper,
            result = if (inText.isBlank()) null else convertRadix(inText, explicit, isUpper),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        RadixUiState(),
    )

    fun updateInput(text: String) {
        if (text.length <= MAX_INPUT_LENGTH) {
            savedStateHandle[KEY_INPUT] = text
        }
    }

    fun selectRadix(value: Int?) {
        savedStateHandle[KEY_CUSTOM_MODE] = false
        savedStateHandle[KEY_RADIX] = value ?: AUTO
    }

    fun enableCustomMode() {
        savedStateHandle[KEY_CUSTOM_MODE] = true
    }

    fun setCustomRadix(value: Int) {
        savedStateHandle[KEY_CUSTOM_RADIX] = value.coerceIn(MIN_RADIX, MAX_RADIX)
    }

    fun setUppercase(isUpper: Boolean) {
        savedStateHandle[KEY_UPPERCASE] = isUpper
    }

    fun clear() {
        savedStateHandle[KEY_INPUT] = ""
    }

    companion object {
        private const val KEY_INPUT = "input"
        private const val KEY_RADIX = "radix"
        private const val KEY_CUSTOM_MODE = "customMode"
        private const val KEY_CUSTOM_RADIX = "customRadix"
        private const val KEY_UPPERCASE = "uppercase"
        private const val AUTO = -1
        private const val DEFAULT_CUSTOM = 12
        private const val MIN_RADIX = 2
        private const val MAX_RADIX = 36
        private const val MAX_INPUT_LENGTH = 10_000

        fun factory(convertRadix: ConvertRadixUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    return RadixViewModel(convertRadix, savedStateHandle) as T
                }
            }
    }
}
