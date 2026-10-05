package com.cangshuo.toolbox.feature.hash.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.hash.domain.ComputeHashesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HashViewModel(
    private val computeHashesUseCase: ComputeHashesUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val input = savedStateHandle.getStateFlow(KEY_INPUT, "")
    private val uppercase = savedStateHandle.getStateFlow(KEY_UPPERCASE, false)

    val uiState: StateFlow<HashUiState> = combine(
        input,
        uppercase,
    ) { inText, isUpper ->
        val results = computeHashesUseCase(inText, isUpper)
        HashUiState(
            inputText = inText,
            uppercase = isUpper,
            hashes = results,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        HashUiState(),
    )

    fun updateInput(text: String) {
        if (text.length <= MAX_INPUT_LENGTH) {
            savedStateHandle[KEY_INPUT] = text
        }
    }

    fun setUppercase(isUpper: Boolean) {
        savedStateHandle[KEY_UPPERCASE] = isUpper
    }

    fun clear() {
        savedStateHandle[KEY_INPUT] = ""
    }

    companion object {
        private const val KEY_INPUT = "input"
        private const val KEY_UPPERCASE = "uppercase"
        private const val MAX_INPUT_LENGTH = 100_000

        fun factory(
            computeHashesUseCase: ComputeHashesUseCase,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val savedStateHandle = extras.createSavedStateHandle()
                return HashViewModel(computeHashesUseCase, savedStateHandle) as T
            }
        }
    }
}
