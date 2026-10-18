package com.cangshuo.toolbox.feature.anniversary

import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryCategory
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryError
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryEvent
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryFilter
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryPolicy
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryRepeat
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversarySort
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnniversaryPolicyTest {
    private fun event(
        id: Long = 1,
        title: String = "Event $id",
        date: LocalDate = LocalDate.of(2020, 10, 18),
        repeat: AnniversaryRepeat = AnniversaryRepeat.YEARLY,
        category: AnniversaryCategory = AnniversaryCategory.OTHER,
        pinned: Boolean = false,
        created: Long = id,
    ) = AnniversaryEvent(
        id = id,
        title = title,
        notes = "",
        dateEpochDay = date.toEpochDay(),
        repeat = repeat,
        category = category,
        pinned = pinned,
        createdAt = created,
        updatedAt = created,
    )

    @Test fun titleNotesAndDateBudgetsAreValidated() {
        assertEquals(AnniversaryError.TITLE, AnniversaryPolicy.validateTitle("  "))
        assertNull(AnniversaryPolicy.validateTitle("\uD83D\uDE00".repeat(80)))
        assertEquals(AnniversaryError.TITLE, AnniversaryPolicy.validateTitle("\uD83D\uDE00".repeat(81)))
        assertNull(AnniversaryPolicy.validateNotes("x".repeat(500)))
        assertEquals(AnniversaryError.NOTES, AnniversaryPolicy.validateNotes("x".repeat(501)))
        assertEquals(AnniversaryError.DATE, AnniversaryPolicy.validateDate(LocalDate.of(1899, 12, 31).toEpochDay()))
        assertNull(AnniversaryPolicy.validateDate(LocalDate.of(1900, 1, 1).toEpochDay()))
        assertEquals(AnniversaryError.DATE, AnniversaryPolicy.validateDate(LocalDate.of(2101, 1, 1).toEpochDay()))
        assertEquals("Family day", AnniversaryPolicy.normalizeTitle("  Family \n day  "))
    }

    @Test fun leapDayClampsToFebruary28AndMonth31ClampsToMonthEnd() {
        val feb29 = LocalDate.of(2020, 2, 29)
        assertEquals(LocalDate.of(2025, 2, 28), AnniversaryPolicy.annualDate(feb29, 2025))
        assertEquals(LocalDate.of(2028, 2, 29), AnniversaryPolicy.annualDate(feb29, 2028))
        assertEquals(LocalDate.of(2025, 2, 28), AnniversaryPolicy.monthlyDate(LocalDate.of(2025, 1, 31), java.time.YearMonth.of(2025, 2)))
        assertEquals(LocalDate.of(2025, 4, 30), AnniversaryPolicy.monthlyDate(LocalDate.of(2025, 1, 31), java.time.YearMonth.of(2025, 4)))
    }

    @Test fun yearlyOccurrenceKeepsTodayAndRollsAfterMidnight() {
        val yearly = event(date = LocalDate.of(2020, 5, 10))
        val today = LocalDate.of(2026, 5, 10)
        assertEquals(today, AnniversaryPolicy.nextOccurrence(yearly, today.toEpochDay()))
        assertEquals(0L, AnniversaryPolicy.occurrence(yearly, today.toEpochDay()).daysUntil)
        assertEquals(
            LocalDate.of(2027, 5, 10),
            AnniversaryPolicy.nextOccurrence(yearly, today.plusDays(1).toEpochDay()),
        )
    }

    @Test fun monthlyOccurrenceHandlesFutureOriginalAndLastDay() {
        val monthly = event(date = LocalDate.of(2025, 1, 31), repeat = AnniversaryRepeat.MONTHLY)
        assertEquals(LocalDate.of(2025, 2, 28), AnniversaryPolicy.nextOccurrence(monthly, LocalDate.of(2025, 2, 1).toEpochDay()))
        assertEquals(LocalDate.of(2025, 2, 28), AnniversaryPolicy.nextOccurrence(monthly, LocalDate.of(2025, 2, 28).toEpochDay()))
        assertEquals(LocalDate.of(2025, 3, 31), AnniversaryPolicy.nextOccurrence(monthly, LocalDate.of(2025, 3, 1).toEpochDay()))
        val future = event(date = LocalDate.of(2026, 12, 31), repeat = AnniversaryRepeat.MONTHLY)
        assertEquals(LocalDate.of(2026, 12, 31), AnniversaryPolicy.nextOccurrence(future, LocalDate.of(2026, 10, 18).toEpochDay()))
    }

    @Test fun oneOffEventsBecomePastAndRecurringEventsDoNot() {
        val once = event(date = LocalDate.of(2020, 1, 1), repeat = AnniversaryRepeat.NONE)
        val yearly = event(date = LocalDate.of(2020, 1, 1), repeat = AnniversaryRepeat.YEARLY)
        val today = LocalDate.of(2026, 10, 18).toEpochDay()
        assertTrue(AnniversaryPolicy.isPast(once, today))
        assertFalse(AnniversaryPolicy.isPast(yearly, today))
        assertEquals(LocalDate.of(2027, 1, 1), AnniversaryPolicy.nextOccurrence(yearly, today))
    }

    @Test fun upcomingDatesAndElapsedYearsUseAnnualRule() {
        val yearly = event(date = LocalDate.of(2020, 10, 18))
        val today = LocalDate.of(2026, 10, 18)
        assertEquals(
            listOf(LocalDate.of(2026, 10, 18), LocalDate.of(2027, 10, 18), LocalDate.of(2028, 10, 18)),
            AnniversaryPolicy.upcomingDates(yearly, today.toEpochDay()),
        )
        val elapsed = AnniversaryPolicy.elapsedYears(yearly, today.toEpochDay())!!
        assertEquals(6, elapsed.years)
        assertEquals(0L, elapsed.daysSinceLatest)
        val nextDay = AnniversaryPolicy.elapsedYears(yearly, today.plusDays(1).toEpochDay())!!
        assertEquals(6, nextDay.years)
        assertEquals(1L, nextDay.daysSinceLatest)
    }

    @Test fun filtersSortsSearchAndStatsAreStable() {
        val today = LocalDate.of(2026, 10, 18)
        val pinned = event(1, "Pinned birthday", today, AnniversaryRepeat.YEARLY, AnniversaryCategory.BIRTHDAY, true, 10)
        val oneOffPast = event(2, "Past trip", LocalDate.of(2025, 1, 1), AnniversaryRepeat.NONE, created = 20)
        val tomorrow = event(3, "Tomorrow", today.plusDays(1), AnniversaryRepeat.NONE, created = 30)
        val sevenDays = event(4, "Week", today.plusDays(7), AnniversaryRepeat.NONE, created = 40)
        val events = listOf(oneOffPast, tomorrow, sevenDays, pinned)
        assertEquals(listOf(1L, 3L, 4L, 2L), AnniversaryPolicy.filterAndSort(events, AnniversaryFilter.ALL, AnniversarySort.PINNED, "", today.toEpochDay()).map { it.id })
        assertEquals(listOf(1L, 3L, 4L), AnniversaryPolicy.filterAndSort(events, AnniversaryFilter.UPCOMING, AnniversarySort.NEXT_DATE, "", today.toEpochDay()).map { it.id })
        assertEquals(listOf(1L), AnniversaryPolicy.filterAndSort(events, AnniversaryFilter.TODAY, AnniversarySort.PINNED, "birthday", today.toEpochDay()).map { it.id })
        assertEquals(listOf(2L), AnniversaryPolicy.filterAndSort(events, AnniversaryFilter.PAST, AnniversarySort.NAME, "", today.toEpochDay()).map { it.id })
        val stats = AnniversaryPolicy.stats(events, today.toEpochDay())
        assertEquals(4, stats.total)
        assertEquals(1, stats.today)
        assertEquals(2, stats.nextSevenDays)
        assertEquals(2, stats.nextThirtyDays)
    }

    @Test fun storageMappingFallsBackToSafeDefaults() {
        assertEquals(AnniversaryRepeat.NONE, AnniversaryRepeat.fromStorage(99))
        assertEquals(AnniversaryRepeat.MONTHLY, AnniversaryRepeat.fromStorage(1))
        assertEquals(AnniversaryCategory.OTHER, AnniversaryCategory.fromStorage(99))
        assertEquals(AnniversaryCategory.FAMILY, AnniversaryCategory.fromStorage(2))
    }
}
