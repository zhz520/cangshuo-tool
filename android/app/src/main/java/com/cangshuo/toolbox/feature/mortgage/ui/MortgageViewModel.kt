package com.cangshuo.toolbox.feature.mortgage.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.core.model.parseDecimalAmount
import com.cangshuo.toolbox.feature.mortgage.domain.CalculateMortgageUseCase
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageError
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageInput
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageLimits
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageMethod
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageOutcome
import java.math.BigDecimal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class MortgageViewModel(
    private val calculateMortgage: CalculateMortgageUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val amountText = savedStateHandle.getStateFlow(KEY_AMOUNT, "")
    private val rateText = savedStateHandle.getStateFlow(KEY_RATE, "")
    private val yearsText = savedStateHandle.getStateFlow(KEY_YEARS, "")
    private val methodName = savedStateHandle.getStateFlow(KEY_METHOD, MortgageMethod.EQUAL_INSTALLMENT.name)
    private val showAllRows = savedStateHandle.getStateFlow(KEY_SHOW_ALL, false)
    private val rejectedInput = MutableStateFlow(false)

    val uiState: StateFlow<MortgageUiState> = combine(
        amountText, rateText, yearsText, methodName, showAllRows,
    ) { amount, rate, years, method, showAll ->
        buildState(amount, rate, years, resolveMethod(method), showAll)
    }.combine(rejectedInput) { state, rejected ->
        if (rejected) state.copy(outcome = MortgageOutcome.Failure(MortgageError.INPUT_TOO_LONG)) else state
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        buildState(
            amountText.value, rateText.value, yearsText.value,
            resolveMethod(methodName.value), showAllRows.value,
        ),
    )

    fun updateAmount(text: String) = update(KEY_AMOUNT, text)

    fun updateRate(text: String) = update(KEY_RATE, text)

    fun updateYears(text: String) = update(KEY_YEARS, text)

    fun selectMethod(method: MortgageMethod) {
        savedStateHandle[KEY_METHOD] = method.name
    }

    fun setShowAllRows(showAll: Boolean) {
        savedStateHandle[KEY_SHOW_ALL] = showAll
    }

    fun clear() {
        rejectedInput.value = false
        savedStateHandle[KEY_AMOUNT] = ""
        savedStateHandle[KEY_RATE] = ""
        savedStateHandle[KEY_YEARS] = ""
    }

    private fun update(key: String, text: String) {
        rejectedInput.value = text.length > MortgageLimits.MAX_INPUT_LENGTH
        if (!rejectedInput.value) savedStateHandle[key] = text
    }

    private fun buildState(
        amount: String,
        rate: String,
        years: String,
        method: MortgageMethod,
        showAll: Boolean,
    ): MortgageUiState {
        val base = MortgageUiState(
            amountText = amount,
            rateText = rate,
            yearsText = years,
            method = method,
            showAllRows = showAll,
        )
        val amountWan = if (amount.isBlank()) null else parseDecimalAmount(amount)
        val rateValue = if (rate.isBlank()) null else parseDecimalAmount(rate)
        val yearsValue = if (years.isBlank()) null else parseDecimalAmount(years)
        val amountInvalid = amount.isNotBlank() && (amountWan == null || amountWan.signum() <= 0)
        val rateInvalid = rate.isNotBlank() && (rateValue == null || rateValue.signum() < 0)
        val yearsInvalid = years.isNotBlank() && (yearsValue == null || yearsValue.stripTrailingZeros().scale() > 0)
        if (amountInvalid || rateInvalid || yearsInvalid) {
            return base.copy(outcome = MortgageOutcome.Failure(MortgageError.INVALID))
        }
        if (amountWan == null || rateValue == null || yearsValue == null) return base
        if (yearsValue < BigDecimal(MortgageLimits.MIN_YEARS) || yearsValue > BigDecimal(MortgageLimits.MAX_YEARS)) {
            return base.copy(outcome = MortgageOutcome.Failure(MortgageError.RANGE))
        }
        val input = MortgageInput(
            amountYuan = amountWan.multiply(BigDecimal(10000)),
            annualRatePercent = rateValue,
            years = yearsValue.intValueExact(),
            method = method,
        )
        return base.copy(outcome = calculateMortgage(input))
    }

    private fun resolveMethod(name: String): MortgageMethod =
        MortgageMethod.entries.firstOrNull { it.name == name } ?: MortgageMethod.EQUAL_INSTALLMENT

    companion object {
        private const val KEY_AMOUNT = "amount"
        private const val KEY_RATE = "rate"
        private const val KEY_YEARS = "years"
        private const val KEY_METHOD = "method"
        private const val KEY_SHOW_ALL = "showAll"

        fun factory(calculateMortgage: CalculateMortgageUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    return MortgageViewModel(calculateMortgage, savedStateHandle) as T
                }
            }
    }
}
