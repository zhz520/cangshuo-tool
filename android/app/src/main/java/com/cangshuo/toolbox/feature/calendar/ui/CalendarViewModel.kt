package com.cangshuo.toolbox.feature.calendar.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.calendar.domain.LunarCalendarUseCases
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

class CalendarViewModel(
    private val useCases: LunarCalendarUseCases,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val today = useCases.today()
    private var visibleMonth = parseMonth(savedStateHandle.get<String>(KEY_MONTH))
        ?: YearMonth.from(today)
    private var selectedDate = parseDate(savedStateHandle.get<String>(KEY_DATE))
        ?.takeIf { YearMonth.from(it) == visibleMonth }
        ?: minDateInMonth(visibleMonth, today.dayOfMonth)
    private val mutable = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = mutable.asStateFlow()

    init {
        rebuild()
    }

    fun selectDate(date: LocalDate) {
        if (date.year !in LunarCalendarUseCases.MIN_YEAR..LunarCalendarUseCases.MAX_YEAR) return
        selectedDate = date
        visibleMonth = YearMonth.from(date)
        persist()
        rebuild()
    }

    fun previousMonth() {
        if (visibleMonth.year <= LunarCalendarUseCases.MIN_YEAR && visibleMonth.monthValue == 1) return
        moveMonth(visibleMonth.minusMonths(1))
    }

    fun nextMonth() {
        if (visibleMonth.year >= LunarCalendarUseCases.MAX_YEAR && visibleMonth.monthValue == 12) return
        moveMonth(visibleMonth.plusMonths(1))
    }

    fun goToday() {
        visibleMonth = YearMonth.from(today)
        selectedDate = today
        persist()
        rebuild()
    }

    fun refresh() {
        rebuild()
    }

    private fun moveMonth(target: YearMonth) {
        visibleMonth = target
        selectedDate = target.atDay(min(selectedDate.dayOfMonth, target.lengthOfMonth()))
        persist()
        rebuild()
    }

    private fun rebuild() {
        if (visibleMonth.year !in LunarCalendarUseCases.MIN_YEAR..LunarCalendarUseCases.MAX_YEAR) {
            mutable.value = CalendarUiState(error = CalendarError.CALCULATION_FAILED)
            return
        }
        try {
            val month = useCases.month(visibleMonth)
            val details = useCases.details(selectedDate)
            persist()
            mutable.value = CalendarUiState(
                today = today,
                visibleMonth = visibleMonth,
                selectedDate = selectedDate,
                month = month,
                details = details,
                error = null,
            )
        } catch (_: Exception) {
            mutable.value = CalendarUiState(
                today = today,
                visibleMonth = visibleMonth,
                selectedDate = selectedDate,
                error = CalendarError.CALCULATION_FAILED,
            )
        }
    }

    private fun persist() {
        savedStateHandle[KEY_MONTH] = visibleMonth.toString()
        savedStateHandle[KEY_DATE] = selectedDate.toString()
    }

    private fun parseMonth(value: String?): YearMonth? = try {
        value?.let(YearMonth::parse)?.takeIf { it.year in LunarCalendarUseCases.MIN_YEAR..LunarCalendarUseCases.MAX_YEAR }
    } catch (_: DateTimeParseException) {
        null
    }

    private fun parseDate(value: String?): LocalDate? = try {
        value?.let(LocalDate::parse)?.takeIf { it.year in LunarCalendarUseCases.MIN_YEAR..LunarCalendarUseCases.MAX_YEAR }
    } catch (_: DateTimeParseException) {
        null
    }

    private fun minDateInMonth(month: YearMonth, day: Int): LocalDate =
        month.atDay(day.coerceIn(1, month.lengthOfMonth()))

    companion object {
        private const val KEY_MONTH = "calendarMonth"
        private const val KEY_DATE = "calendarDate"

        fun factory(useCases: LunarCalendarUseCases): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    CalendarViewModel(useCases, extras.createSavedStateHandle()) as T
            }
    }
}
