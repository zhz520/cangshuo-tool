package com.cangshuo.toolbox.feature.calendar

import com.cangshuo.toolbox.feature.calendar.data.LocalLunarCalendarRepository
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LunarCalendarRepositoryTest {
    private val repository = LocalLunarCalendarRepository()

    @Test
    fun monthGridIsMondayFirstAndAlwaysHasSixWeeks() {
        val month = repository.month(YearMonth.of(2026, 10))
        assertEquals(42, month.days.size)
        assertEquals(LocalDate.of(2026, 9, 28), month.days.first().date)
        assertEquals(LocalDate.of(2026, 11, 8), month.days.last().date)
        assertEquals(31, month.days.count { it.inCurrentMonth })
        assertFalse(month.days.first().inCurrentMonth)
        assertTrue(month.days.first { it.date == LocalDate.of(2026, 10, 1) }.inCurrentMonth)
    }

    @Test
    fun monthCellsCarryLunarFestivalsAndSolarTerms() {
        val month = repository.month(YearMonth.of(2026, 10))
        val doubleNinth = month.days.first { it.date == LocalDate.of(2026, 10, 18) }
        assertEquals("初九", doubleNinth.lunarLabel)
        assertTrue("重阳节" in doubleNinth.festivals)
        assertFalse(doubleNinth.festivals.isEmpty())

        val qingming = repository.month(YearMonth.of(2026, 4))
            .days.first { it.date == LocalDate.of(2026, 4, 5) }
        assertEquals("清明", qingming.lunarLabel)
        assertEquals("清明", qingming.solarTerm)
    }

    @Test
    fun lunarMonthFirstAndSpringFestivalLabels() {
        val spring = repository.month(YearMonth.of(2026, 2))
            .days.first { it.date == LocalDate.of(2026, 2, 17) }
        assertEquals("正月", spring.lunarLabel)
        assertTrue("春节" in spring.festivals)
    }

    @Test
    fun detailsIncludeGanzhiZodiacFestivalsAndAlmanac() {
        val details = repository.details(LocalDate.of(2026, 10, 18))
        assertEquals(2026, details.lunarYear)
        assertEquals("九", details.lunarMonthChinese)
        assertEquals("初九", details.lunarDayChinese)
        assertFalse(details.isLeapMonth)
        assertEquals("马", details.zodiac)
        assertTrue("重阳节" in details.festivals)
        assertTrue(details.ganZhiYear.isNotBlank())
        assertTrue(details.ganZhiMonth.isNotBlank())
        assertTrue(details.ganZhiDay.isNotBlank())
        assertTrue(details.suitable.isNotEmpty())
        assertTrue(details.avoid.isNotEmpty())
    }

    @Test
    fun leapMonthIsExposedFromTheCalendarLibrary() {
        val leap = repository.details(LocalDate.of(2028, 6, 23))
        assertTrue(leap.isLeapMonth)
        assertEquals("闰五", leap.lunarMonthChinese)
        assertEquals("初一", leap.lunarDayChinese)
    }
}
