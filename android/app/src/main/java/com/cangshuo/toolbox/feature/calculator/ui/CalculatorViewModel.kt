package com.cangshuo.toolbox.feature.calculator.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.calculator.domain.CalculateExpressionUseCase
import com.cangshuo.toolbox.feature.calculator.domain.CalculationError
import com.cangshuo.toolbox.feature.calculator.domain.CalculationResult
import com.cangshuo.toolbox.feature.calculator.domain.MAX_EXPRESSION_LENGTH
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val EXPRESSION_KEY = "calculator.expression"
private const val RESULT_KEY = "calculator.result"

class CalculatorViewModel(
    private val calculateExpression: CalculateExpressionUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val restoredExpression = savedStateHandle.get<String>(EXPRESSION_KEY).orEmpty().take(MAX_EXPRESSION_LENGTH)
    private val mutableState = MutableStateFlow(
        CalculatorUiState(expression = restoredExpression, result = savedStateHandle[RESULT_KEY]),
    )
    val uiState = mutableState.asStateFlow()

    fun editExpression(expression: String, selectionStart: Int, selectionEnd: Int) {
        if (expression.length > MAX_EXPRESSION_LENGTH) {
            mutableState.value = mutableState.value.copy(result = null, error = CalculationError.INPUT_TOO_LONG)
            save()
            return
        }
        val previous = mutableState.value
        mutableState.value = previous.copy(
            expression = expression,
            selectionStart = selectionStart.coerceIn(0, expression.length),
            selectionEnd = selectionEnd.coerceIn(0, expression.length),
            result = if (expression == previous.expression) previous.result else null,
            error = if (expression == previous.expression) previous.error else null,
        )
        save()
    }

    fun insert(token: String) {
        val state = mutableState.value
        val start = minOf(state.selectionStart, state.selectionEnd)
        val end = maxOf(state.selectionStart, state.selectionEnd)
        val expression = state.expression.replaceRange(start, end, token)
        editExpression(expression, start + token.length, start + token.length)
    }

    fun delete() {
        val state = mutableState.value
        val start = minOf(state.selectionStart, state.selectionEnd)
        val end = maxOf(state.selectionStart, state.selectionEnd)
        if (start != end) {
            editExpression(state.expression.removeRange(start, end), start, start)
        } else if (start > 0) {
            editExpression(state.expression.removeRange(start - 1, start), start - 1, start - 1)
        }
    }

    fun clear() {
        mutableState.value = CalculatorUiState()
        save()
    }

    fun calculate() {
        val result = try {
            calculateExpression(mutableState.value.expression)
        } catch (_: Exception) {
            CalculationResult.Failure(CalculationError.CALCULATION_FAILED)
        }
        mutableState.value = when (result) {
            is CalculationResult.Success -> mutableState.value.copy(result = result.value, error = null)
            is CalculationResult.Failure -> mutableState.value.copy(result = null, error = result.error)
        }
        save()
    }

    fun useResult() {
        val result = mutableState.value.result ?: return
        mutableState.value = CalculatorUiState(expression = result)
        save()
    }

    private fun save() {
        savedStateHandle[EXPRESSION_KEY] = mutableState.value.expression
        savedStateHandle[RESULT_KEY] = mutableState.value.result
    }

    companion object {
        fun factory(calculate: CalculateExpressionUseCase): ViewModelProvider.Factory = viewModelFactory {
            initializer { CalculatorViewModel(calculate, createSavedStateHandle()) }
        }
    }
}
