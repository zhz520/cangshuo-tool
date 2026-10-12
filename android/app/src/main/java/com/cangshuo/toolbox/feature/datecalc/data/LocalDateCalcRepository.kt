package com.cangshuo.toolbox.feature.datecalc.data

import com.cangshuo.toolbox.feature.datecalc.domain.DateCalcError
import com.cangshuo.toolbox.feature.datecalc.domain.DateCalcLimits
import com.cangshuo.toolbox.feature.datecalc.domain.DateCalcRepository
import com.cangshuo.toolbox.feature.datecalc.domain.DateDifference
import com.cangshuo.toolbox.feature.datecalc.domain.DateDirection
import com.cangshuo.toolbox.feature.datecalc.domain.DateShift
import com.cangshuo.toolbox.feature.datecalc.domain.DateShiftOutcome
import com.cangshuo.toolbox.feature.datecalc.domain.DateUnit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Calendar arithmetic on [LocalDate] values. Differences are normalized so a later first
 * argument still reports positive totals. Business days skip Saturdays and Sundays only
 * (public holidays and make-up workdays are out of scope) and count the calendar interval
 * [earlier, later), so a same-day or weekend-only span is zero.
 */
class LocalDateCalcRepository : DateCalcRepository {

    override fun difference(first: LocalDate, second: LocalDate): DateDifference {
        val earlier = minOf(first, second)
        val later = maxOf(first, second)
        val totalDays = ChronoUnit.DAYS.between(earlier, later)
        val breakdown = calendarBreakdown(earlier, later)
        val businessDays = businessDaysBetween(earlier, later)
        return DateDifference(
            totalDays = totalDays,
            weeks = totalDays / 7,
            remainderDays = totalDays % 7,
            years = breakdown[0],
            months = breakdown[1],
            days = breakdown[2],
            businessDays = businessDays,
            weekendDays = totalDays - businessDays,
        )
    }

    /**
     * Whole years/months plus the remaining days, using the same end-of-month clamping the
     * shift section applies (Temporal-style largest units): 2024-02-29 to 2025-02-28 is one
     * year and 2026-01-31 to 2026-02-28 is one month, while java.time.Period would report
     * 11 months 30 days and 28 days for the same spans.
     */
    private fun calendarBreakdown(earlier: LocalDate, later: LocalDate): LongArray {
        var months = ChronoUnit.MONTHS.between(earlier, later)
        while (!earlier.plusMonths(months + 1).isAfter(later)) months++
        if (earlier.plusMonths(months).isAfter(later)) months--
        val anchor = earlier.plusMonths(months)
        val days = ChronoUnit.DAYS.between(anchor, later)
        return longArrayOf(months / 12, months % 12, days)
    }

    override fun shift(base: LocalDate, amount: Long, unit: DateUnit, direction: DateDirection): DateShiftOutcome {
        val signed = if (direction == DateDirection.AFTER) amount else -amount
        val result = when (unit) {
            DateUnit.DAYS -> base.plusDays(signed)
            DateUnit.WEEKS -> base.plusWeeks(signed)
            DateUnit.MONTHS -> base.plusMonths(signed)
            DateUnit.YEARS -> base.plusYears(signed)
        }
        if (result.year !in DateCalcLimits.MIN_YEAR..DateCalcLimits.MAX_YEAR) {
            return DateShiftOutcome.Failure(DateCalcError.RESULT_OUT_OF_RANGE)
        }
        return DateShiftOutcome.Success(
            DateShift(base = base, result = result, signedDays = ChronoUnit.DAYS.between(base, result)),
        )
    }

    private fun businessDaysBetween(earlier: LocalDate, later: LocalDate): Long {
        val totalDays = ChronoUnit.DAYS.between(earlier, later)
        var result = (totalDays / 7) * 5
        var moving = earlier.plusDays((totalDays / 7) * 7)
        while (moving.isBefore(later)) {
            if (moving.dayOfWeek != DayOfWeek.SATURDAY && moving.dayOfWeek != DayOfWeek.SUNDAY) result++
            moving = moving.plusDays(1)
        }
        return result
    }
}
