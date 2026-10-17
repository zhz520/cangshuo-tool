package com.cangshuo.toolbox.feature.holiday.data

import com.cangshuo.toolbox.feature.holiday.domain.HolidayId
import com.cangshuo.toolbox.feature.holiday.domain.HolidayKind
import com.cangshuo.toolbox.feature.holiday.domain.HolidayRepository
import com.cangshuo.toolbox.feature.holiday.domain.LunarDateInfo
import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import java.time.LocalDate

class LunarHolidayRepository : HolidayRepository {
    override fun nextOccurrences(holiday: HolidayId, fromDate: LocalDate, count: Int): List<LocalDate> {
        require(count > 0)
        val result = LinkedHashSet<LocalDate>()
        val firstYear = fromDate.year - 1
        var year = firstYear
        while (result.size < count && year <= fromDate.year + 8) {
            val candidate = when (holiday.kind) {
                HolidayKind.SOLAR -> LocalDate.of(year, holiday.month, holiday.day)
                HolidayKind.LUNAR -> Lunar.fromYmd(year, holiday.month, holiday.day).solar.toLocalDate()
                HolidayKind.SOLAR_TERM -> solarTermDate(year, holiday.solarTerm ?: error("Missing solar term"))
            }
            if (!candidate.isBefore(fromDate)) result.add(candidate)
            year++
        }
        return result.take(count)
    }

    override fun lunarDate(date: LocalDate): LunarDateInfo {
        val lunar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth).lunar
        return LunarDateInfo(
            month = kotlin.math.abs(lunar.month),
            day = lunar.day,
            isLeap = lunar.month < 0,
        )
    }

    private fun solarTermDate(year: Int, term: String): LocalDate {
        val date = Lunar.fromYmd(year, 1, 1).jieQiTable[term]
            ?: error("Solar term unavailable")
        return date.toLocalDate()
    }

    private fun Solar.toLocalDate(): LocalDate = LocalDate.of(year, month, day)
}
