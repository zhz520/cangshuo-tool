package com.cangshuo.toolbox.feature.calendar.domain

import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CalendarDaySummary(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val lunarLabel: String,
    val solarTerm: String?,
    val festivals: List<String>,
)

data class CalendarMonth(
    val month: YearMonth,
    val days: List<CalendarDaySummary>,
)

data class CalendarDayDetails(
    val date: LocalDate,
    val lunarYear: Int,
    val lunarMonthChinese: String,
    val lunarDayChinese: String,
    val isLeapMonth: Boolean,
    val ganZhiYear: String,
    val ganZhiMonth: String,
    val ganZhiDay: String,
    val zodiac: String,
    val solarTerm: String?,
    val festivals: List<String>,
    val suitable: List<String>,
    val avoid: List<String>,
)

interface LunarCalendarRepository {
    fun month(month: YearMonth): CalendarMonth
    fun details(date: LocalDate): CalendarDayDetails
}

class LunarCalendarUseCases(
    private val repository: LunarCalendarRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneProvider: () -> ZoneId = { ZoneId.of("Asia/Shanghai") },
) {
    fun today(): LocalDate = clock.instant().atZone(zoneProvider()).toLocalDate()

    fun month(month: YearMonth): CalendarMonth {
        require(month.year in MIN_YEAR..MAX_YEAR) { "Month outside supported range" }
        return repository.month(month)
    }

    fun details(date: LocalDate): CalendarDayDetails {
        require(date.year in MIN_YEAR..MAX_YEAR) { "Date outside supported range" }
        return repository.details(date)
    }

    companion object {
        const val MIN_YEAR = 1900
        const val MAX_YEAR = 2100
        const val GRID_DAYS = 42
    }
}
