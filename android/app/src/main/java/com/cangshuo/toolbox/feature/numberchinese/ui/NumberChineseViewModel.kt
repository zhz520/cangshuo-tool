package com.cangshuo.toolbox.feature.numberchinese.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.numberchinese.domain.ChineseNumberStyle
import com.cangshuo.toolbox.feature.numberchinese.domain.ConvertNumberUseCase
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberChineseError
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberChineseLimits
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberChineseOutcome
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class NumberChineseViewModel(
    private val convertNumber: ConvertNumberUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val input = savedStateHandle.getStateFlow(KEY_INPUT, "")
    private val styleName = savedStateHandle.getStateFlow(KEY_STYLE, ChineseNumberStyle.LOWER.name)
    private val directionName = savedStateHandle.getStateFlow(KEY_DIRECTION, NumberDirection.AUTO.name)
    private val colloquialTen = savedStateHandle.getStateFlow(KEY_COLLOQUIAL, true)
    private val rejected = MutableStateFlow(false)

    val uiState: StateFlow<NumberChineseUiState> = combine(
        input, styleName, directionName, colloquialTen, rejected,
    ) { text, style, direction, colloquial, tooLong ->
        evaluate(text, style, direction, colloquial, tooLong)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        NumberChineseUiState(input = input.value, colloquialTen = colloquialTen.value),
    )

    fun updateInput(value: String) {
        val tooLong = value.length > NumberChineseLimits.MAX_INPUT_LENGTH
        rejected.value = tooLong
        if (!tooLong) savedStateHandle[KEY_INPUT] = value
    }

    fun selectStyle(style: ChineseNumberStyle) { savedStateHandle[KEY_STYLE] = style.name }

    fun selectDirection(direction: NumberDirection) { savedStateHandle[KEY_DIRECTION] = direction.name }

    fun toggleColloquialTen(value: Boolean) { savedStateHandle[KEY_COLLOQUIAL] = value }

    fun clear() {
        rejected.value = false
        savedStateHandle[KEY_INPUT] = ""
    }

    private fun evaluate(
        text: String,
        styleValue: String,
        directionValue: String,
        colloquial: Boolean,
        tooLong: Boolean,
    ): NumberChineseUiState {
        val style = ChineseNumberStyle.entries.firstOrNull { it.name == styleValue } ?: ChineseNumberStyle.LOWER
        val direction = NumberDirection.entries.firstOrNull { it.name == directionValue } ?: NumberDirection.AUTO
        val base = NumberChineseUiState(
            input = text,
            style = style,
            direction = direction,
            colloquialTen = colloquial,
        )
        if (tooLong) return base.copy(outcome = NumberChineseOutcome.Failure(NumberChineseError.TOO_LONG))
        if (text.isBlank()) return base
        return base.copy(outcome = convertNumber(text, style, direction, colloquial))
    }

    companion object {
        private const val KEY_INPUT = "input"
        private const val KEY_STYLE = "style"
        private const val KEY_DIRECTION = "direction"
        private const val KEY_COLLOQUIAL = "colloquial"

        fun factory(convertNumber: ConvertNumberUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    return NumberChineseViewModel(convertNumber, savedStateHandle) as T
                }
            }
    }
}
