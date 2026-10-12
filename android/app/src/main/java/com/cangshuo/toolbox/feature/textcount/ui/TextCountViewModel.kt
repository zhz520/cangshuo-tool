package com.cangshuo.toolbox.feature.textcount.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.textcount.domain.AnalyzeTextUseCase
import com.cangshuo.toolbox.feature.textcount.domain.TextCountError
import com.cangshuo.toolbox.feature.textcount.domain.TextCountLimits
import com.cangshuo.toolbox.feature.textcount.domain.TextCountOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class TextCountViewModel(
    private val analyzeText: AnalyzeTextUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val text = savedStateHandle.getStateFlow(KEY_TEXT, "")
    private val rejected = MutableStateFlow(false)

    val uiState: StateFlow<TextCountUiState> = combine(text, rejected) { value, tooLong ->
        TextCountUiState(
            text = value,
            outcome = when {
                tooLong -> TextCountOutcome.Failure(TextCountError.INPUT_TOO_LONG)
                value.isBlank() -> null
                else -> TextCountOutcome.Success(analyzeText(value))
            },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        TextCountUiState(
            text = text.value,
            outcome = if (text.value.isBlank()) null else TextCountOutcome.Success(analyzeText(text.value)),
        ),
    )

    fun updateText(value: String) {
        val tooLong = value.length > TextCountLimits.MAX_INPUT_LENGTH
        rejected.value = tooLong
        if (!tooLong) savedStateHandle[KEY_TEXT] = value
    }

    fun clear() {
        rejected.value = false
        savedStateHandle[KEY_TEXT] = ""
    }

    companion object {
        private const val KEY_TEXT = "text"

        fun factory(analyzeText: AnalyzeTextUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    return TextCountViewModel(analyzeText, savedStateHandle) as T
                }
            }
    }
}
