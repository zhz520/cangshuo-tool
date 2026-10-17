package com.cangshuo.toolbox.feature.calendar.ui

import com.cangshuo.toolbox.feature.calendar.domain.CalendarDayDetails
import com.cangshuo.toolbox.feature.calendar.domain.CalendarMonth
import java.time.LocalDate
import java.time.YearMonth

enum class CalendarError { CALCULATION_FAILED }

data class CalendarUiState(
    val today: LocalDate? = null,
    val visibleMonth: YearMonth? = null,
    val selectedDate: LocalDate? = null,
    val month: CalendarMonth? = null,
    val details: CalendarDayDetails? = null,
    val error: CalendarError? = null,
)
