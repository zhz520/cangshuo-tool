package com.cangshuo.toolbox.feature.period

import com.cangshuo.toolbox.feature.period.domain.PeriodCycle
import com.cangshuo.toolbox.feature.period.domain.PeriodCycleInput
import com.cangshuo.toolbox.feature.period.domain.PeriodError
import com.cangshuo.toolbox.feature.period.domain.PeriodFlow
import com.cangshuo.toolbox.feature.period.domain.PeriodPolicy
import com.cangshuo.toolbox.feature.period.domain.PeriodSettings
import com.cangshuo.toolbox.feature.period.domain.PeriodValidation
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodPolicyTest {
    private val today = LocalDate.of(2026, 4, 10).toEpochDay()

    private fun day(month: Int, day: Int) = LocalDate.of(2026, month, day).toEpochDay()

    private fun cycle(
        id: Long,
        start: Long,
        end: Long?,
        flow: PeriodFlow = PeriodFlow.MEDIUM,
    ) = PeriodCycle(
        id = id,
        startEpochDay = start,
        endEpochDay = end,
        flow = flow,
        symptoms = emptySet(),
        mood = null,
        notes = "",
        createdAt = id,
        updatedAt = id,
    )

    @Test fun historyDrivesCycleEstimatesAndCalendarMarks() {
        val cycles = listOf(
            cycle(1, day(1, 1), day(1, 5)),
            cycle(2, day(1, 29), day(2, 2)),
            cycle(3, day(2, 26), day(3, 2)),
            cycle(4, day(3, 26), day(3, 30)),
        )
        val insight = PeriodPolicy.insights(cycles, PeriodSettings(), today)
        assertEquals(28, insight.averageCycleDays)
        assertEquals(5, insight.averagePeriodDays)
        assertEquals(3, insight.cycleSamples)
        assertEquals(4, insight.periodSamples)
        assertEquals(16, insight.currentCycleDay)
        assertEquals(day(4, 23), insight.nextStartEpochDay)
        assertEquals(day(4, 27), insight.predictedEndEpochDay)
        assertEquals(day(4, 9), insight.ovulationEpochDay)
        assertEquals(day(4, 4), insight.fertileStartEpochDay)
        assertEquals(day(4, 10), insight.fertileEndEpochDay)
        assertEquals(0, insight.lateDays)

        val days = PeriodPolicy.calendarMonth(
            YearMonth.of(2026, 4), cycles, insight, day(4, 10), today,
        )
        assertEquals(42, days.size)
        assertEquals(LocalDate.of(2026, 3, 30).toEpochDay(), days.first().epochDay)
        val selected = days.first { it.epochDay == day(4, 10) }
        assertTrue(selected.fertileWindow)
        assertTrue(selected.today)
        assertTrue(selected.selected)
        assertTrue(days.first { it.epochDay == day(4, 23) }.predictedPeriod)
        assertTrue(days.first { it.epochDay == day(4, 27) }.predictedPeriod)
        assertFalse(days.first { it.epochDay == day(4, 28) }.predictedPeriod)
    }

    @Test fun sparseHistoryUsesSettingsAndOngoingRecordStaysOpenEnded() {
        val current = cycle(1, day(1, 1), null)
        val insight = PeriodPolicy.insights(
            listOf(current),
            PeriodSettings(cycleDays = 30, periodDays = 3),
            day(1, 4),
        )
        assertEquals(30, insight.averageCycleDays)
        assertEquals(3, insight.averagePeriodDays)
        assertEquals(0, insight.cycleSamples)
        assertEquals(0, insight.periodSamples)
        assertEquals(day(1, 31), insight.nextStartEpochDay)
        assertEquals(day(2, 2), insight.predictedEndEpochDay)
        val days = PeriodPolicy.calendarMonth(
            YearMonth.of(2026, 1), listOf(current), insight, null, day(1, 4),
        )
        val recorded = days.first { it.epochDay == day(1, 4) }
        assertEquals(1L, recorded.cycleId)
        assertTrue(recorded.ongoing)
        assertTrue(days.first { it.epochDay == day(1, 31) }.predictedPeriod)
    }

    @Test fun invalidDatesOverlapAndNotesAreRejected() {
        val existing = cycle(9, day(3, 1), day(3, 5))
        val all = listOf(existing)
        fun validate(start: Long, end: Long?, notes: String = "") = PeriodPolicy.validate(
            null,
            PeriodCycleInput(start, end, PeriodFlow.MEDIUM, emptySet(), null, notes),
            all,
            100L,
            today,
        )
        assertEquals(PeriodError.START_DATE, (validate(today + 1, null) as PeriodValidation.Invalid).error)
        assertEquals(PeriodError.END_DATE, (validate(day(3, 6), day(3, 5)) as PeriodValidation.Invalid).error)
        assertEquals(PeriodError.END_DATE, (validate(day(3, 6), today + 1) as PeriodValidation.Invalid).error)
        assertEquals(PeriodError.END_DATE, (validate(day(3, 6), day(4, 10)) as PeriodValidation.Invalid).error)
        assertEquals(PeriodError.OVERLAP, (validate(day(3, 5), day(3, 8)) as PeriodValidation.Invalid).error)
        assertEquals(PeriodError.NOTES, (validate(day(3, 6), null, "x".repeat(501)) as PeriodValidation.Invalid).error)

        val unchanged = PeriodPolicy.validate(
            existing,
            PeriodCycleInput(existing.startEpochDay, existing.endEpochDay, PeriodFlow.HEAVY, emptySet(), null, "ok"),
            all,
            200L,
            today,
        ) as PeriodValidation.Valid
        assertEquals(9L, unchanged.cycle.id)
        assertEquals(9L, unchanged.cycle.createdAt)
        assertEquals(200L, unchanged.cycle.updatedAt)
    }

    @Test fun settingsBoundsMonthClampAndLateDaysAreFinite() {
        assertFalse(PeriodPolicy.validSettings(PeriodSettings(cycleDays = 14, periodDays = 5)))
        assertFalse(PeriodPolicy.validSettings(PeriodSettings(cycleDays = 28, periodDays = 15)))
        assertTrue(PeriodPolicy.validSettings(PeriodSettings(cycleDays = 15, periodDays = 1)))
        assertEquals(PeriodPolicy.MIN_MONTH, PeriodPolicy.clampMonth(YearMonth.of(1800, 1)))
        assertEquals(PeriodPolicy.MAX_MONTH, PeriodPolicy.clampMonth(YearMonth.of(2200, 1)))

        val insight = PeriodPolicy.insights(
            listOf(cycle(1, day(1, 1), day(1, 5))),
            PeriodSettings(25, 5),
            day(2, 10),
        )
        assertEquals(day(1, 26), insight.nextStartEpochDay)
        assertTrue(insight.lateDays > 0)
        assertNull(PeriodPolicy.insights(emptyList(), PeriodSettings(), today).nextStartEpochDay)
    }

    @Test fun leapYearMonthGridIsAlwaysSixWeeksAndCrossesYearBoundaries() {
        val leapToday = LocalDate.of(2024, 2, 29).toEpochDay()
        val days = PeriodPolicy.calendarMonth(
            YearMonth.of(2024, 2),
            emptyList(),
            PeriodPolicy.insights(emptyList(), PeriodSettings(), leapToday),
            leapToday,
            leapToday,
        )
        assertEquals(42, days.size)
        assertEquals(LocalDate.of(2024, 1, 29).toEpochDay(), days.first().epochDay)
        assertEquals(LocalDate.of(2024, 3, 10).toEpochDay(), days.last().epochDay)
        assertTrue(days.first { it.epochDay == leapToday }.today)
    }
}
