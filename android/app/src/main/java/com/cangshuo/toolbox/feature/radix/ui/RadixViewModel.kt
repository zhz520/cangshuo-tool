package com.cangshuo.toolbox.feature.radix.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.radix.domain.ConvertRadixUseCase
import com.cangshuo.toolbox.feature.radix.domain.RadixError
import com.cangshuo.toolbox.feature.radix.domain.RadixLimits
import com.cangshuo.toolbox.feature.radix.domain.RadixResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class RadixViewModel(
    private val convertRadix: ConvertRadixUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val input = savedStateHandle.getStateFlow(KEY_INPUT, "")
    private val radix = savedStateHandle.getStateFlow(KEY_RADIX, AUTO)
    private val customMode = savedStateHandle.getStateFlow(KEY_CUSTOM_MODE, false)
    private val customRadix = savedStateHandle.getStateFlow(KEY_CUSTOM_RADIX, DEFAULT_CUSTOM)
    private val uppercase = savedStateHandle.getStateFlow(KEY_UPPERCASE, false)
    private val rejectedInput = savedStateHandle.getStateFlow(KEY_REJECTED_INPUT, false)

    val uiState: StateFlow<RadixUiState> = combine(
        input, radix, customMode, customRadix, uppercase,
    ) { inText, selRadix, isCustom, custom, isUpper ->
        RadixUiState(
            inputText = inText,
            selectedRadix = selRadix.takeUnless { it == AUTO },
            customMode = isCustom,
            customRadix = custom.coerceIn(MIN_RADIX, MAX_RADIX),
            uppercase = isUpper,
        )
    }.combine(rejectedInput) { state, rejected -> state to rejected }
        .transformLatest { (state, rejected) ->
            if (rejected) {
                emit(state.copy(result = RadixResult.Error(RadixError.INPUT_TOO_LARGE)))
            } else if (state.inputText.isBlank()) {
                emit(state)
            } else {
                emit(state.copy(isProcessing = true))
                val explicit = if (state.customMode) state.customRadix else state.selectedRadix
                // Latest input owns the result; cancelled native work is bounded and serialized.
                val result = withContext(dispatcher) { convertRadix(state.inputText, explicit, state.uppercase) }
                emit(state.copy(result = result))
            }
        }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        RadixUiState(),
    )

    fun updateInput(text: String) {
        savedStateHandle[KEY_REJECTED_INPUT] = text.length > RadixLimits.MAX_INPUT_LENGTH
        if (text.length <= RadixLimits.MAX_INPUT_LENGTH) savedStateHandle[KEY_INPUT] = text
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
        savedStateHandle[KEY_REJECTED_INPUT] = false
        savedStateHandle[KEY_INPUT] = ""
    }

    companion object {
        private const val KEY_INPUT = "input"
        private const val KEY_RADIX = "radix"
        private const val KEY_CUSTOM_MODE = "customMode"
        private const val KEY_CUSTOM_RADIX = "customRadix"
        private const val KEY_UPPERCASE = "uppercase"
        private const val KEY_REJECTED_INPUT = "rejectedInput"
        private const val AUTO = -1
        private const val DEFAULT_CUSTOM = 12
        private const val MIN_RADIX = 2
        private const val MAX_RADIX = 36

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
