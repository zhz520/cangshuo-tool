package com.cangshuo.toolbox.feature.holiday.ui

import com.cangshuo.toolbox.feature.holiday.domain.HolidayCountdownItem
import com.cangshuo.toolbox.feature.holiday.domain.HolidayFilter
import com.cangshuo.toolbox.feature.holiday.domain.HolidayId
import java.time.LocalDate

enum class HolidayError { CALCULATION_FAILED }

data class HolidayUiState(
    val today: LocalDate? = null,
    val filter: HolidayFilter = HolidayFilter.ALL,
    val selected: HolidayId? = null,
    val selectedUpcoming: List<HolidayCountdownItem> = emptyList(),
    val items: List<HolidayCountdownItem> = emptyList(),
    val error: HolidayError? = null,
)
