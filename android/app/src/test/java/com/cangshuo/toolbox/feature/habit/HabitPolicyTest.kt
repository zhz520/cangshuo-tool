package com.cangshuo.toolbox.feature.habit

import com.cangshuo.toolbox.feature.habit.domain.Habit
import com.cangshuo.toolbox.feature.habit.domain.HabitError
import com.cangshuo.toolbox.feature.habit.domain.HabitFilter
import com.cangshuo.toolbox.feature.habit.domain.HabitPolicy
import com.cangshuo.toolbox.feature.habit.domain.HabitSort
import com.cangshuo.toolbox.feature.habit.domain.HabitWeekday
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitPolicyTest {
    private val monday = LocalDate.of(2026, 10, 5).toEpochDay()
    private val monWedFri = HabitWeekday.MONDAY.bit or HabitWeekday.WEDNESDAY.bit or HabitWeekday.FRIDAY.bit

    private fun day(offset: Long) = monday + offset

    private fun habit(
        id: Long = 1,
        name: String = "Habit $id",
        mask: Int = monWedFri,
        createdDay: Long = monday,
        archived: Boolean = false,
        archivedDay: Long? = if (archived) day(13) else null,
        created: Long = id,
        checked: Set<Long> = emptySet(),
    ) = Habit(
        id = id,
        name = name,
        description = "",
        weekdaysMask = mask,
        archived = archived,
        archivedDay = archivedDay,
        position = id.toInt(),
        createdDay = createdDay,
        createdAt = created,
        updatedAt = created,
        checkedDates = checked,
    )

    @Test fun nameDescriptionAndWeekdayBudgetsAreValidatedByCodePoint() {
        assertEquals(HabitError.NAME, HabitPolicy.validateName("   "))
        assertNull(HabitPolicy.validateName("\uD83D\uDE00".repeat(80)))
        assertEquals(HabitError.NAME, HabitPolicy.validateName("\uD83D\uDE00".repeat(81)))
        assertNull(HabitPolicy.validateDescription("x".repeat(500)))
        assertEquals(HabitError.DESCRIPTION, HabitPolicy.validateDescription("x".repeat(501)))
        assertEquals(HabitError.WEEKDAYS, HabitPolicy.validateWeekdays(0))
        assertEquals(HabitError.WEEKDAYS, HabitPolicy.validateWeekdays(128))
        assertNull(HabitPolicy.validateWeekdays(HabitPolicy.ALL_DAYS_MASK))
        assertEquals("Read book", HabitPolicy.normalizeName("  Read \n book  "))
    }

    @Test fun weekdayMaskUsesMondayAsBitZeroAndDecemberDatesAcrossYearsAreStable() {
        assertTrue(HabitPolicy.isScheduled(HabitWeekday.MONDAY.bit, LocalDate.of(2026, 10, 5).toEpochDay()))
        assertTrue(HabitPolicy.isScheduled(HabitWeekday.SUNDAY.bit, LocalDate.of(2027, 1, 3).toEpochDay()))
        assertFalse(HabitPolicy.isScheduled(HabitWeekday.MONDAY.bit, LocalDate.of(2026, 10, 6).toEpochDay()))
    }

    @Test fun currentAndBestStreaksIgnoreUnscheduledBonusDaysAndCountScheduledDays() {
        val checked = setOf(day(0), day(2), day(4), day(7), day(9), day(11), day(8), day(10))
        val habit = habit(checked = checked)
        val today = day(14)
        assertEquals(6, HabitPolicy.currentStreak(habit, today))
        assertEquals(6, HabitPolicy.bestStreak(habit))
        assertTrue(HabitPolicy.isDueToday(habit, today))

        val completedToday = habit.copy(checkedDates = checked + today)
        assertEquals(7, HabitPolicy.currentStreak(completedToday, today))
        assertFalse(HabitPolicy.isDueToday(completedToday, today))
    }

    @Test fun missedScheduledDayBreaksStreakAndHistoryContainsEmptyScheduledDays() {
        val checked = setOf(day(0), day(2), day(4), day(9), day(11))
        val habit = habit(checked = checked)
        val today = day(14)
        assertEquals(2, HabitPolicy.currentStreak(habit, today))
        assertEquals(3, HabitPolicy.bestStreak(habit))
        val history = HabitPolicy.history(habit, today)
        assertEquals(28, history.size)
        assertEquals(today, history.last().epochDay)
        assertTrue(history.first { it.epochDay == day(9) }.completed)
        assertTrue(history.first { it.epochDay == day(14) }.scheduled)
    }

    @Test fun weeklyProgressAndCompletionRateUseScheduledDaysOnly() {
        val today = day(14)
        val habit = habit(checked = setOf(day(14), day(7), day(9)))
        val week = HabitPolicy.weekProgress(habit, today)
        assertEquals(1, week.completed)
        assertEquals(1, week.scheduled)

        val createdSaturday = habit(createdDay = day(12), mask = monWedFri or HabitWeekday.SATURDAY.bit)
        assertEquals(1, HabitPolicy.weekProgress(createdSaturday, today).scheduled)

        val daily = habit(mask = HabitPolicy.ALL_DAYS_MASK, checked = setOf(day(0), day(1), day(2), day(3)))
        assertEquals(57, HabitPolicy.completionRate(daily, day(6), days = 7))
    }

    @Test fun searchFiltersAndSortsProduceStableActiveOrdering() {
        val today = day(14)
        val dueOld = habit(1, "Read", checked = setOf(day(0), day(2), day(4), day(7), day(9), day(11)), created = 1)
        val dueNew = habit(2, "Walk", checked = setOf(day(14)), created = 2)
        val notDue = habit(3, "Water plants", mask = HabitWeekday.TUESDAY.bit, created = 3)
        val archived = habit(4, "Archived", archived = true, created = 4)
        val habits = listOf(dueOld, dueNew, notDue, archived)
        assertEquals(listOf(1L, 2L, 3L, 4L), HabitPolicy.filterAndSort(habits, HabitFilter.ALL, HabitSort.STREAK, "", today).map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 4L), HabitPolicy.filterAndSort(habits, HabitFilter.ALL, HabitSort.TODAY_FIRST, "", today).map { it.id })
        assertEquals(listOf(2L, 1L), HabitPolicy.filterAndSort(habits, HabitFilter.TODAY, HabitSort.NEWEST, "", today).map { it.id })
        assertEquals(listOf(4L), HabitPolicy.filterAndSort(habits, HabitFilter.ARCHIVED, HabitSort.NAME, "arch", today).map { it.id })
    }

    @Test fun overallStatisticsAndHistoricalToggleBoundsAreConsistent() {
        val today = day(14)
        val active = habit(1, checked = setOf(day(7), day(9), day(11), today))
        val due = habit(2, name = "Due", created = 2)
        val archived = habit(3, archived = true, checked = setOf(day(0)), created = 3)
        val stats = HabitPolicy.stats(listOf(active, due, archived), today)
        assertEquals(2, stats.active)
        assertEquals(1, stats.dueToday)
        assertEquals(1, stats.completedToday)
        assertEquals(5, stats.totalCheckIns)
        assertEquals(4, stats.bestCurrentStreak)
        assertTrue(HabitPolicy.canToggleDate(due, today, today))
        assertFalse(HabitPolicy.canToggleDate(due, today + 1, today))
        assertFalse(HabitPolicy.canToggleDate(archived, today, today))
    }

    @Test fun archivedHabitStatisticsStopAtArchiveDay() {
        val archived = habit(
            mask = HabitPolicy.ALL_DAYS_MASK,
            archived = true,
            archivedDay = day(2),
            checked = setOf(day(0), day(1), day(2)),
        )
        assertEquals(3, HabitPolicy.currentStreak(archived, day(6)))
        assertEquals(100, HabitPolicy.completionRate(archived, day(6)))
        assertEquals(day(2), HabitPolicy.history(archived, day(6)).last().epochDay)
        assertEquals(3, HabitPolicy.weekProgress(archived, day(6)).completed)
    }
}
