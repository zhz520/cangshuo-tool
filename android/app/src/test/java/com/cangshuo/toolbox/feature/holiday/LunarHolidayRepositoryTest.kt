package com.cangshuo.toolbox.feature.holiday

import com.cangshuo.toolbox.feature.holiday.data.LunarHolidayRepository
import com.cangshuo.toolbox.feature.holiday.domain.HolidayId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LunarHolidayRepositoryTest {
    private val repository = LunarHolidayRepository()

    @Test
    fun springFestivalUsesLunarYearAcrossGregorianYears() {
        assertEquals(
            listOf(
                LocalDate.of(2027, 2, 6),
                LocalDate.of(2028, 1, 26),
                LocalDate.of(2029, 2, 13),
            ),
            repository.nextOccurrences(HolidayId.SPRING_FESTIVAL, LocalDate.of(2026, 10, 17), 3),
        )
    }

    @Test
    fun midAutumnAndLabaIncludeNextGregorianYear() {
        assertEquals(
            listOf(
                LocalDate.of(2027, 9, 15),
                LocalDate.of(2028, 10, 3),
                LocalDate.of(2029, 9, 22),
            ),
            repository.nextOccurrences(HolidayId.MID_AUTUMN_FESTIVAL, LocalDate.of(2026, 9, 26), 3),
        )
        assertEquals(
            listOf(
                LocalDate.of(2027, 1, 15),
                LocalDate.of(2028, 1, 4),
                LocalDate.of(2029, 1, 22),
            ),
            repository.nextOccurrences(HolidayId.LABA_FESTIVAL, LocalDate.of(2026, 10, 17), 3),
        )
    }

    @Test
    fun qingmingComesFromTheSolarTermTable() {
        assertEquals(
            listOf(
                LocalDate.of(2027, 4, 5),
                LocalDate.of(2028, 4, 4),
                LocalDate.of(2029, 4, 4),
            ),
            repository.nextOccurrences(HolidayId.QINGMING, LocalDate.of(2026, 10, 17), 3),
        )
    }

    @Test
    fun todayIsKeptAsTheFirstOccurrence() {
        assertEquals(
            LocalDate.of(2026, 10, 18),
            repository.nextOccurrences(
                HolidayId.DOUBLE_NINTH,
                LocalDate.of(2026, 10, 18),
                3,
            ).first(),
        )
    }

    @Test
    fun solarFestivalsRollForwardWithoutNegativeCounts() {
        assertEquals(
            listOf(
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2028, 1, 1),
                LocalDate.of(2029, 1, 1),
            ),
            repository.nextOccurrences(HolidayId.NEW_YEAR, LocalDate.of(2026, 10, 17), 3),
        )
    }

    @Test
    fun leapMonthIsReportedWithoutRenamingTheNormalMonth() {
        val leap = repository.lunarDate(LocalDate.of(2028, 6, 23))
        assertEquals(5, leap.month)
        assertEquals(1, leap.day)
        assertTrue(leap.isLeap)

        val regular = repository.lunarDate(LocalDate.of(2028, 5, 24))
        assertEquals(5, regular.month)
        assertEquals(1, regular.day)
        assertFalse(regular.isLeap)
    }
}
