package com.cangshuo.toolbox.feature.calendar

import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.calendar.data.LocalLunarCalendarRepository
import com.cangshuo.toolbox.feature.calendar.domain.CalendarDayDetails
import com.cangshuo.toolbox.feature.calendar.domain.CalendarMonth
import com.cangshuo.toolbox.feature.calendar.domain.LunarCalendarRepository
import com.cangshuo.toolbox.feature.calendar.domain.LunarCalendarUseCases
import com.cangshuo.toolbox.feature.calendar.ui.CalendarError
import com.cangshuo.toolbox.feature.calendar.ui.CalendarViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarViewModelTest {
    private val today = LocalDate.of(2026, 10, 18)
    private val clock = Clock.fixed(
        Instant.parse("2026-10-18T04:00:00Z"),
        ZoneId.of("UTC"),
    )

    private fun useCases(repository: LunarCalendarRepository = LocalLunarCalendarRepository()) =
        LunarCalendarUseCases(repository, clock) { ZoneId.of("Asia/Shanghai") }

    private fun model(
        handle: SavedStateHandle = SavedStateHandle(),
        repository: LunarCalendarRepository = LocalLunarCalendarRepository(),
    ) = CalendarViewModel(useCases(repository), handle)

    @Test
    fun defaultsToTodayAndShowsItsDetails() {
        val handle = SavedStateHandle()
        val viewModel = model(handle)
        val state = viewModel.state.value
        assertEquals(today, state.today)
        assertEquals(YearMonth.of(2026, 10), state.visibleMonth)
        assertEquals(today, state.selectedDate)
        assertEquals(42, state.month?.days?.size)
        assertNotNull(state.details)
        assertEquals("2026-10-18", handle.get<String>("calendarDate"))
        assertEquals("2026-10", handle.get<String>("calendarMonth"))
    }

    @Test
    fun selectingAdjacentMonthDateMovesTheVisibleMonth() {
        val handle = SavedStateHandle()
        val viewModel = model(handle)
        viewModel.selectDate(LocalDate.of(2026, 11, 11))
        assertEquals(YearMonth.of(2026, 11), viewModel.state.value.visibleMonth)
        assertEquals(LocalDate.of(2026, 11, 11), viewModel.state.value.selectedDate)
        assertEquals("2026-11-11", handle.get<String>("calendarDate"))
    }

    @Test
    fun monthNavigationClampsTheSelectedDay() {
        val viewModel = model(SavedStateHandle(mapOf(
            "calendarMonth" to "2027-01",
            "calendarDate" to "2027-01-31",
        )))
        viewModel.nextMonth()
        assertEquals(YearMonth.of(2027, 2), viewModel.state.value.visibleMonth)
        assertEquals(LocalDate.of(2027, 2, 28), viewModel.state.value.selectedDate)

        viewModel.selectDate(LocalDate.of(2027, 3, 31))
        viewModel.previousMonth()
        assertEquals(YearMonth.of(2027, 2), viewModel.state.value.visibleMonth)
        assertEquals(LocalDate.of(2027, 2, 28), viewModel.state.value.selectedDate)
    }

    @Test
    fun todayAndSavedStateRestoreTheSelection() {
        val handle = SavedStateHandle()
        val viewModel = model(handle)
        viewModel.selectDate(LocalDate.of(2027, 1, 15))
        viewModel.goToday()
        assertEquals(today, viewModel.state.value.selectedDate)
        assertEquals(YearMonth.from(today), viewModel.state.value.visibleMonth)

        val restored = model(SavedStateHandle(mapOf(
            "calendarMonth" to "2028-06",
            "calendarDate" to "2028-06-23",
        )))
        assertEquals(LocalDate.of(2028, 6, 23), restored.state.value.selectedDate)
        assertTrue(restored.state.value.details?.isLeapMonth == true)
    }

    @Test
    fun invalidSavedValuesFallBackToToday() {
        val viewModel = model(SavedStateHandle(mapOf(
            "calendarMonth" to "1899-12",
            "calendarDate" to "bad-date",
        )))
        assertEquals(YearMonth.from(today), viewModel.state.value.visibleMonth)
        assertEquals(today, viewModel.state.value.selectedDate)
    }

    @Test
    fun supportedMonthRangeIsClampedAtBothEnds() {
        val lower = model(SavedStateHandle(mapOf(
            "calendarMonth" to "1900-01",
            "calendarDate" to "1900-01-31",
        )))
        lower.previousMonth()
        assertEquals(YearMonth.of(1900, 1), lower.state.value.visibleMonth)

        val upper = model(SavedStateHandle(mapOf(
            "calendarMonth" to "2100-12",
            "calendarDate" to "2100-12-31",
        )))
        upper.nextMonth()
        assertEquals(YearMonth.of(2100, 12), upper.state.value.visibleMonth)
    }

    @Test
    fun repositoryFailureShowsRetryableError() {
        val repository = object : LunarCalendarRepository {
            override fun month(month: YearMonth): CalendarMonth = error("boom")
            override fun details(date: LocalDate): CalendarDayDetails = error("boom")
        }
        val viewModel = model(repository = repository)
        assertEquals(CalendarError.CALCULATION_FAILED, viewModel.state.value.error)
        viewModel.refresh()
        assertEquals(CalendarError.CALCULATION_FAILED, viewModel.state.value.error)
    }
}
