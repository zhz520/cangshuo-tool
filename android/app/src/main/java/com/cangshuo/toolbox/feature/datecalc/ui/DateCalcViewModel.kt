package com.cangshuo.toolbox.feature.datecalc.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.datecalc.domain.CalculateDateUseCase
import com.cangshuo.toolbox.feature.datecalc.domain.DateCalcError
import com.cangshuo.toolbox.feature.datecalc.domain.DateCalcLimits
import com.cangshuo.toolbox.feature.datecalc.domain.DateDiffOutcome
import com.cangshuo.toolbox.feature.datecalc.domain.DateDirection
import com.cangshuo.toolbox.feature.datecalc.domain.DateShiftOutcome
import com.cangshuo.toolbox.feature.datecalc.domain.DateUnit
import java.time.DateTimeException
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class DateCalcViewModel(
    private val calculate: CalculateDateUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val firstText = savedStateHandle.getStateFlow(KEY_FIRST, "")
    private val secondText = savedStateHandle.getStateFlow(KEY_SECOND, "")
    private val baseText = savedStateHandle.getStateFlow(KEY_BASE, "")
    private val amountText = savedStateHandle.getStateFlow(KEY_AMOUNT, "")
    private val unitName = savedStateHandle.getStateFlow(KEY_UNIT, DateUnit.DAYS.name)
    private val directionName = savedStateHandle.getStateFlow(KEY_DIRECTION, DateDirection.AFTER.name)
    private val diffRejected = MutableStateFlow(false)
    private val shiftRejected = MutableStateFlow(false)

    private data class Inputs(
        val first: String,
        val second: String,
        val base: String,
        val amount: String,
        val unitName: String,
    )

    private data class Flags(
        val directionName: String,
        val diffRejected: Boolean,
        val shiftRejected: Boolean,
    )

    private val inputs = combine(firstText, secondText, baseText, amountText, unitName) {
            first, second, base, amount, unit ->
        Inputs(first, second, base, amount, unit)
    }

    private val flags = combine(directionName, diffRejected, shiftRejected) {
            direction, diffReject, shiftReject ->
        Flags(direction, diffReject, shiftReject)
    }

    val uiState: StateFlow<DateCalcUiState> = combine(inputs, flags) { input, flag ->
        buildState(input, flag)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        buildState(
            Inputs(firstText.value, secondText.value, baseText.value, amountText.value, unitName.value),
            Flags(directionName.value, diffRejected.value, shiftRejected.value),
        ),
    )

    fun updateFirst(text: String) =
        updateText(KEY_FIRST, text, DateCalcLimits.MAX_DATE_LENGTH) { diffRejected.value = it }

    fun updateSecond(text: String) =
        updateText(KEY_SECOND, text, DateCalcLimits.MAX_DATE_LENGTH) { diffRejected.value = it }

    fun updateBase(text: String) =
        updateText(KEY_BASE, text, DateCalcLimits.MAX_DATE_LENGTH) { shiftRejected.value = it }

    fun updateAmount(text: String) =
        updateText(KEY_AMOUNT, text, DateCalcLimits.MAX_AMOUNT_LENGTH) { shiftRejected.value = it }

    fun fillFirstToday() {
        diffRejected.value = false
        savedStateHandle[KEY_FIRST] = today().toString()
    }

    fun fillSecondToday() {
        diffRejected.value = false
        savedStateHandle[KEY_SECOND] = today().toString()
    }

    fun fillBaseToday() {
        shiftRejected.value = false
        savedStateHandle[KEY_BASE] = today().toString()
    }

    fun selectUnit(unit: DateUnit) {
        savedStateHandle[KEY_UNIT] = unit.name
    }

    fun selectDirection(direction: DateDirection) {
        savedStateHandle[KEY_DIRECTION] = direction.name
    }

    fun clear() {
        diffRejected.value = false
        shiftRejected.value = false
        savedStateHandle[KEY_FIRST] = ""
        savedStateHandle[KEY_SECOND] = ""
        savedStateHandle[KEY_BASE] = ""
        savedStateHandle[KEY_AMOUNT] = ""
    }

    private fun updateText(key: String, text: String, maxLength: Int, setRejected: (Boolean) -> Unit) {
        val tooLong = text.length > maxLength
        setRejected(tooLong)
        if (!tooLong) savedStateHandle[key] = text
    }

    private fun buildState(input: Inputs, flags: Flags): DateCalcUiState {
        val unit = DateUnit.entries.firstOrNull { it.name == input.unitName } ?: DateUnit.DAYS
        val direction = DateDirection.entries.firstOrNull { it.name == flags.directionName } ?: DateDirection.AFTER
        return DateCalcUiState(
            firstText = input.first,
            secondText = input.second,
            baseText = input.base,
            amountText = input.amount,
            unit = unit,
            direction = direction,
            diffOutcome = if (flags.diffRejected) {
                DateDiffOutcome.Failure(DateCalcError.INPUT_TOO_LONG)
            } else {
                evaluateDifference(input.first, input.second)
            },
            shiftOutcome = if (flags.shiftRejected) {
                DateShiftOutcome.Failure(DateCalcError.INPUT_TOO_LONG)
            } else {
                evaluateShift(input.base, input.amount, unit, direction)
            },
        )
    }

    private fun evaluateDifference(first: String, second: String): DateDiffOutcome? {
        if (first.isBlank() || second.isBlank()) return null
        val firstDate = parseDate(first) ?: return DateDiffOutcome.Failure(DateCalcError.INVALID_DATE)
        val secondDate = parseDate(second) ?: return DateDiffOutcome.Failure(DateCalcError.INVALID_DATE)
        if (!isSupportedYear(firstDate) || !isSupportedYear(secondDate)) {
            return DateDiffOutcome.Failure(DateCalcError.OUT_OF_RANGE)
        }
        return DateDiffOutcome.Success(calculate.difference(firstDate, secondDate))
    }

    private fun evaluateShift(
        base: String,
        amount: String,
        unit: DateUnit,
        direction: DateDirection,
    ): DateShiftOutcome? {
        if (base.isBlank() || amount.isBlank()) return null
        val baseDate = parseDate(base) ?: return DateShiftOutcome.Failure(DateCalcError.INVALID_DATE)
        if (!isSupportedYear(baseDate)) return DateShiftOutcome.Failure(DateCalcError.OUT_OF_RANGE)
        val digits = amount.trim()
        if (digits.isEmpty() || digits.length > DateCalcLimits.MAX_AMOUNT_LENGTH || digits.any { !it.isDigit() }) {
            return DateShiftOutcome.Failure(DateCalcError.INVALID_AMOUNT)
        }
        val value = digits.toLongOrNull() ?: return DateShiftOutcome.Failure(DateCalcError.INVALID_AMOUNT)
        if (value > DateCalcLimits.MAX_AMOUNT) {
            return DateShiftOutcome.Failure(DateCalcError.AMOUNT_OUT_OF_RANGE)
        }
        return calculate.shift(baseDate, value, unit, direction)
    }

    private fun parseDate(text: String): LocalDate? {
        val match = DatePattern.matchEntire(text.trim()) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        return try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun isSupportedYear(date: LocalDate): Boolean =
        date.year in DateCalcLimits.MIN_YEAR..DateCalcLimits.MAX_YEAR

    companion object {
        private val DatePattern = Regex("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})$")
        private const val KEY_FIRST = "first"
        private const val KEY_SECOND = "second"
        private const val KEY_BASE = "base"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_UNIT = "unit"
        private const val KEY_DIRECTION = "direction"

        fun factory(calculate: CalculateDateUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    return DateCalcViewModel(calculate, savedStateHandle) as T
                }
            }
    }
}
