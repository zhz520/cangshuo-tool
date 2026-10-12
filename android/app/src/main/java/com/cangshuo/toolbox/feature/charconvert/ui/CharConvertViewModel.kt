package com.cangshuo.toolbox.feature.charconvert.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.charconvert.domain.CaseMode
import com.cangshuo.toolbox.feature.charconvert.domain.CharConvertError
import com.cangshuo.toolbox.feature.charconvert.domain.CharConvertLimits
import com.cangshuo.toolbox.feature.charconvert.domain.CharConvertOptions
import com.cangshuo.toolbox.feature.charconvert.domain.CharConvertOutcome
import com.cangshuo.toolbox.feature.charconvert.domain.TransformCharsUseCase
import com.cangshuo.toolbox.feature.charconvert.domain.WidthMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class CharConvertViewModel(
    private val transformChars: TransformCharsUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val input = savedStateHandle.getStateFlow(KEY_INPUT, "")
    private val widthName = savedStateHandle.getStateFlow(KEY_WIDTH, WidthMode.TO_HALF.name)
    private val caseName = savedStateHandle.getStateFlow(KEY_CASE, CaseMode.NONE.name)
    private val collapse = savedStateHandle.getStateFlow(KEY_COLLAPSE, false)
    private val zeroWidth = savedStateHandle.getStateFlow(KEY_ZERO, true)
    private val newlines = savedStateHandle.getStateFlow(KEY_NEWLINES, true)
    private val rejected = MutableStateFlow(false)

    private data class Flags(
        val collapse: Boolean,
        val zeroWidth: Boolean,
        val newlines: Boolean,
        val rejected: Boolean,
    )

    private val selections = combine(widthName, caseName) { width, case -> width to case }
    private val flags = combine(collapse, zeroWidth, newlines, rejected) { collapseValue, zero, newline, tooLong ->
        Flags(collapseValue, zero, newline, tooLong)
    }

    val uiState: StateFlow<CharConvertUiState> = combine(input, selections, flags) { text, selection, flag ->
        evaluate(text, selection.first, selection.second, flag)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        CharConvertUiState(input = input.value),
    )

    fun updateInput(value: String) {
        val tooLong = value.length > CharConvertLimits.MAX_INPUT_LENGTH
        rejected.value = tooLong
        if (!tooLong) savedStateHandle[KEY_INPUT] = value
    }

    fun selectWidth(width: WidthMode) { savedStateHandle[KEY_WIDTH] = width.name }

    fun selectCase(case: CaseMode) { savedStateHandle[KEY_CASE] = case.name }

    fun toggleCollapse(value: Boolean) { savedStateHandle[KEY_COLLAPSE] = value }

    fun toggleZeroWidth(value: Boolean) { savedStateHandle[KEY_ZERO] = value }

    fun toggleNewlines(value: Boolean) { savedStateHandle[KEY_NEWLINES] = value }

    fun clear() {
        rejected.value = false
        savedStateHandle[KEY_INPUT] = ""
    }

    private fun evaluate(text: String, widthValue: String, caseValue: String, flag: Flags): CharConvertUiState {
        val width = WidthMode.entries.firstOrNull { it.name == widthValue } ?: WidthMode.TO_HALF
        val case = CaseMode.entries.firstOrNull { it.name == caseValue } ?: CaseMode.NONE
        val base = CharConvertUiState(
            input = text,
            width = width,
            case = case,
            collapseWhitespace = flag.collapse,
            removeZeroWidth = flag.zeroWidth,
            unifyNewlines = flag.newlines,
        )
        if (flag.rejected) return base.copy(outcome = CharConvertOutcome.Failure(CharConvertError.INPUT_TOO_LONG))
        if (text.isEmpty()) return base
        val options = CharConvertOptions(width, case, flag.collapse, flag.zeroWidth, flag.newlines)
        return base.copy(outcome = CharConvertOutcome.Success(transformChars(text, options)))
    }

    companion object {
        private const val KEY_INPUT = "input"
        private const val KEY_WIDTH = "width"
        private const val KEY_CASE = "case"
        private const val KEY_COLLAPSE = "collapse"
        private const val KEY_ZERO = "zero"
        private const val KEY_NEWLINES = "newlines"

        fun factory(transformChars: TransformCharsUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    return CharConvertViewModel(transformChars, savedStateHandle) as T
                }
            }
    }
}
