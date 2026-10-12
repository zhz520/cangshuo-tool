package com.cangshuo.toolbox.feature.datecalc

import com.cangshuo.toolbox.feature.datecalc.data.LocalDateCalcRepository
import com.cangshuo.toolbox.feature.datecalc.domain.CalculateDateUseCase
import com.cangshuo.toolbox.feature.datecalc.domain.DateCalcError
import com.cangshuo.toolbox.feature.datecalc.domain.DateDirection
import com.cangshuo.toolbox.feature.datecalc.domain.DateShiftOutcome
import com.cangshuo.toolbox.feature.datecalc.domain.DateUnit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DateCalcTest {
    private val calculate = CalculateDateUseCase(LocalDateCalcRepository())

    @Test
    fun differenceIsOrderIndependentAndReportsNormalizedTotals() {
        val forward = calculate.difference(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        val backward = calculate.difference(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1))
        assertEquals(30, forward.totalDays)
        assertEquals(30, backward.totalDays)
        assertEquals(4, forward.weeks)
        assertEquals(2, forward.remainderDays)
        assertEquals(0, forward.years)
        assertEquals(0, forward.months)
        assertEquals(30, forward.days)
    }

    @Test
    fun calendarBreakdownClampsMonthEndsLikeTheShiftSection() {
        val acrossMonth = calculate.difference(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 1))
        assertEquals(29, acrossMonth.totalDays)
        assertEquals(1, acrossMonth.months)
        assertEquals(1, acrossMonth.days)
        val leapYear = calculate.difference(LocalDate.of(2024, 2, 29), LocalDate.of(2025, 2, 28))
        assertEquals(1, leapYear.years)
        assertEquals(0, leapYear.months)
        assertEquals(0, leapYear.days)
        val clampedMonth = calculate.difference(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28))
        assertEquals(1, clampedMonth.months)
        assertEquals(0, clampedMonth.days)
        val monthsAndDays = calculate.difference(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31))
        assertEquals(1, monthsAndDays.months)
        assertEquals(3, monthsAndDays.days)
    }

    @Test
    fun businessDaysSkipWeekendsAndExcludeTheEndDate() {
        val sameDay = calculate.difference(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12))
        assertEquals(0, sameDay.businessDays)
        val mondayToFriday = calculate.difference(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 16))
        assertEquals(4, mondayToFriday.businessDays)
        val saturdayToMonday = calculate.difference(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 19))
        assertEquals(0, saturdayToMonday.businessDays)
        assertEquals(2, saturdayToMonday.weekendDays)
        val mondayToNextMonday = calculate.difference(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19))
        assertEquals(5, mondayToNextMonday.businessDays)
        val fullYear = calculate.difference(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1))
        assertEquals(365, fullYear.totalDays)
        assertEquals(261, fullYear.businessDays)
        assertEquals(104, fullYear.weekendDays)
    }

    @Test
    fun shiftClampsMonthEndAndLeapDay() {
        assertEquals(
            LocalDate.of(2026, 2, 28),
            success(LocalDate.of(2026, 1, 31), 1, DateUnit.MONTHS, DateDirection.AFTER).result,
        )
        assertEquals(
            LocalDate.of(2024, 2, 29),
            success(LocalDate.of(2024, 1, 31), 1, DateUnit.MONTHS, DateDirection.AFTER).result,
        )
        assertEquals(
            LocalDate.of(2026, 2, 28),
            success(LocalDate.of(2026, 3, 31), 1, DateUnit.MONTHS, DateDirection.BEFORE).result,
        )
        assertEquals(
            LocalDate.of(2025, 2, 28),
            success(LocalDate.of(2024, 2, 29), 1, DateUnit.YEARS, DateDirection.AFTER).result,
        )
    }

    @Test
    fun shiftMovesByDaysAndWeeksInBothDirections() {
        val base = LocalDate.of(2026, 1, 5)
        assertEquals(
            LocalDate.of(2025, 12, 26),
            success(base, 10, DateUnit.DAYS, DateDirection.BEFORE).result,
        )
        assertEquals(
            LocalDate.of(2026, 1, 12),
            success(base, 1, DateUnit.WEEKS, DateDirection.AFTER).result,
        )
        assertEquals(base, success(base, 0, DateUnit.DAYS, DateDirection.AFTER).result)
        assertEquals(11L, success(base, 11, DateUnit.DAYS, DateDirection.AFTER).signedDays)
    }

    @Test
    fun shiftGuardsTheSupportedYearRange() {
        assertEquals(
            DateCalcError.RESULT_OUT_OF_RANGE,
            (calculate.shift(LocalDate.of(2999, 12, 31), 1, DateUnit.DAYS, DateDirection.AFTER)
                as DateShiftOutcome.Failure).error,
        )
        assertEquals(
            DateCalcError.RESULT_OUT_OF_RANGE,
            (calculate.shift(LocalDate.of(1900, 1, 1), 1, DateUnit.DAYS, DateDirection.BEFORE)
                as DateShiftOutcome.Failure).error,
        )
    }

    private fun success(base: LocalDate, amount: Long, unit: DateUnit, direction: DateDirection) =
        (calculate.shift(base, amount, unit, direction) as DateShiftOutcome.Success).result
}
