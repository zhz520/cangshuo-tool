package com.cangshuo.toolbox.feature.compound.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.core.model.parseDecimalAmount
import com.cangshuo.toolbox.feature.compound.domain.CalculateCompoundUseCase
import com.cangshuo.toolbox.feature.compound.domain.CompoundError
import com.cangshuo.toolbox.feature.compound.domain.CompoundInput
import com.cangshuo.toolbox.feature.compound.domain.CompoundLimits
import com.cangshuo.toolbox.feature.compound.domain.CompoundOutcome
import com.cangshuo.toolbox.feature.compound.domain.CompoundPeriod
import java.math.BigDecimal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class CompoundViewModel(
    private val calculateCompound: CalculateCompoundUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val principalText = savedStateHandle.getStateFlow(KEY_PRINCIPAL, "")
    private val contributionText = savedStateHandle.getStateFlow(KEY_CONTRIBUTION, "")
    private val rateText = savedStateHandle.getStateFlow(KEY_RATE, "")
    private val yearsText = savedStateHandle.getStateFlow(KEY_YEARS, "")
    private val periodName = savedStateHandle.getStateFlow(KEY_PERIOD, CompoundPeriod.MONTHLY.name)
    private val rejectedInput = MutableStateFlow(false)

    val uiState: StateFlow<CompoundUiState> = combine(
        principalText, contributionText, rateText, yearsText, periodName,
    ) { principal, contribution, rate, years, period ->
        buildState(principal, contribution, rate, years, resolvePeriod(period))
    }.combine(rejectedInput) { state, rejected ->
        if (rejected) state.copy(outcome = CompoundOutcome.Failure(CompoundError.INPUT_TOO_LONG)) else state
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        buildState(
            principalText.value, contributionText.value, rateText.value, yearsText.value,
            resolvePeriod(periodName.value),
        ),
    )

    fun updatePrincipal(text: String) = update(KEY_PRINCIPAL, text)

    fun updateContribution(text: String) = update(KEY_CONTRIBUTION, text)

    fun updateRate(text: String) = update(KEY_RATE, text)

    fun updateYears(text: String) = update(KEY_YEARS, text)

    fun selectPeriod(period: CompoundPeriod) {
        savedStateHandle[KEY_PERIOD] = period.name
    }

    fun clear() {
        rejectedInput.value = false
        savedStateHandle[KEY_PRINCIPAL] = ""
        savedStateHandle[KEY_CONTRIBUTION] = ""
        savedStateHandle[KEY_RATE] = ""
        savedStateHandle[KEY_YEARS] = ""
    }

    private fun update(key: String, text: String) {
        rejectedInput.value = text.length > CompoundLimits.MAX_INPUT_LENGTH
        if (!rejectedInput.value) savedStateHandle[key] = text
    }

    private fun buildState(
        principal: String,
        contribution: String,
        rate: String,
        years: String,
        period: CompoundPeriod,
    ): CompoundUiState {
        val base = CompoundUiState(
            principalText = principal,
            contributionText = contribution,
            rateText = rate,
            yearsText = years,
            period = period,
        )
        val principalValue = if (principal.isBlank()) BigDecimal.ZERO else parseDecimalAmount(principal)
        val contributionValue = if (contribution.isBlank()) BigDecimal.ZERO else parseDecimalAmount(contribution)
        val rateValue = if (rate.isBlank()) null else parseDecimalAmount(rate)
        val yearsValue = if (years.isBlank()) null else parseDecimalAmount(years)
        val yearsInvalid = years.isNotBlank() && (yearsValue == null || yearsValue.stripTrailingZeros().scale() > 0)
        if (principalValue == null || contributionValue == null ||
            (rate.isNotBlank() && rateValue == null) || yearsInvalid
        ) {
            return base.copy(outcome = CompoundOutcome.Failure(CompoundError.INVALID))
        }
        if (principal.isBlank() && contribution.isBlank() && rate.isBlank() && years.isBlank()) return base
        if (rateValue == null || yearsValue == null) return base
        if (yearsValue < BigDecimal(CompoundLimits.MIN_YEARS) || yearsValue > BigDecimal(CompoundLimits.MAX_YEARS)) {
            return base.copy(outcome = CompoundOutcome.Failure(CompoundError.RANGE))
        }
        val input = CompoundInput(
            principal = principalValue,
            contributionPerPeriod = contributionValue,
            annualRatePercent = rateValue,
            years = yearsValue.intValueExact(),
            period = period,
        )
        return base.copy(outcome = calculateCompound(input))
    }

    private fun resolvePeriod(name: String): CompoundPeriod =
        CompoundPeriod.entries.firstOrNull { it.name == name } ?: CompoundPeriod.MONTHLY

    companion object {
        private const val KEY_PRINCIPAL = "principal"
        private const val KEY_CONTRIBUTION = "contribution"
        private const val KEY_RATE = "rate"
        private const val KEY_YEARS = "years"
        private const val KEY_PERIOD = "period"

        fun factory(calculateCompound: CalculateCompoundUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    return CompoundViewModel(calculateCompound, savedStateHandle) as T
                }
            }
    }
}
