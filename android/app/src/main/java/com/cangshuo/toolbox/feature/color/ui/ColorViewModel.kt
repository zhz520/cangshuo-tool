package com.cangshuo.toolbox.feature.color.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.color.domain.ColorError
import com.cangshuo.toolbox.feature.color.domain.ColorLimits
import com.cangshuo.toolbox.feature.color.domain.ColorParseResult
import com.cangshuo.toolbox.feature.color.domain.ConvertColorUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ColorViewModel(
    private val convert: ConvertColorUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val inputText = savedStateHandle.getStateFlow(KEY_INPUT, "")
    private val rejected = MutableStateFlow(false)

    val uiState: StateFlow<ColorUiState> = combine(inputText, rejected) { input, tooLong ->
        buildState(input, tooLong)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        buildState(inputText.value, rejected.value),
    )

    fun updateInput(text: String) {
        val tooLong = text.length > ColorLimits.MAX_INPUT_LENGTH
        rejected.value = tooLong
        if (!tooLong) savedStateHandle[KEY_INPUT] = text
    }

    fun clear() {
        rejected.value = false
        savedStateHandle[KEY_INPUT] = ""
    }

    private fun buildState(input: String, tooLong: Boolean): ColorUiState = ColorUiState(
        inputText = input,
        outcome = when {
            tooLong -> ColorOutcome.Failure(ColorError.INPUT_TOO_LONG)
            input.isBlank() -> null
            else -> when (val parsed = convert.parse(input)) {
                is ColorParseResult.Success -> ColorOutcome.Success(convert.describe(parsed.color))
                is ColorParseResult.Failure -> ColorOutcome.Failure(parsed.error)
            }
        },
    )

    companion object {
        private const val KEY_INPUT = "input"

        fun factory(convert: ConvertColorUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    return ColorViewModel(convert, savedStateHandle) as T
                }
            }
    }
}
