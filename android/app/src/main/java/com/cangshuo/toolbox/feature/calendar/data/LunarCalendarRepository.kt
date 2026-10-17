package com.cangshuo.toolbox.feature.calendar.data

import com.cangshuo.toolbox.feature.calendar.domain.CalendarDayDetails
import com.cangshuo.toolbox.feature.calendar.domain.CalendarDaySummary
import com.cangshuo.toolbox.feature.calendar.domain.CalendarMonth
import com.cangshuo.toolbox.feature.calendar.domain.LunarCalendarRepository
import com.cangshuo.toolbox.feature.calendar.domain.LunarCalendarUseCases
import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import java.time.LocalDate
import java.time.YearMonth

class LocalLunarCalendarRepository : LunarCalendarRepository {
    override fun month(month: YearMonth): CalendarMonth {
        val first = month.atDay(1)
        val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
        val days = List(LunarCalendarUseCases.GRID_DAYS) { offset ->
            val date = gridStart.plusDays(offset.toLong())
            val lunar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth).lunar
            val term = lunar.jieQi.takeIf(String::isNotBlank)
            val label = when {
                term != null -> term
                lunar.day == 1 -> lunar.monthInChinese + "月"
                else -> lunar.dayInChinese
            }
            CalendarDaySummary(
                date = date,
                inCurrentMonth = YearMonth.from(date) == month,
                lunarLabel = label,
                solarTerm = term,
                festivals = festivals(lunar),
            )
        }
        return CalendarMonth(month, days)
    }

    override fun details(date: LocalDate): CalendarDayDetails {
        val solar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth)
        val lunar = solar.lunar
        return CalendarDayDetails(
            date = date,
            lunarYear = lunar.year,
            lunarMonthChinese = lunar.monthInChinese,
            lunarDayChinese = lunar.dayInChinese,
            isLeapMonth = lunar.month < 0,
            ganZhiYear = lunar.yearInGanZhi,
            ganZhiMonth = lunar.monthInGanZhi,
            ganZhiDay = lunar.dayInGanZhi,
            zodiac = lunar.yearShengXiao,
            solarTerm = lunar.jieQi.takeIf(String::isNotBlank),
            festivals = festivals(lunar),
            suitable = lunar.dayYi.orEmpty().distinct().take(MAX_ALMANAC_ITEMS),
            avoid = lunar.dayJi.orEmpty().distinct().take(MAX_ALMANAC_ITEMS),
        )
    }

    private fun festivals(lunar: Lunar): List<String> =
        (lunar.solar.festivals.orEmpty() + lunar.festivals.orEmpty()).distinct()

    companion object {
        private const val MAX_ALMANAC_ITEMS = 12
    }
}
