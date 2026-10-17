package com.cangshuo.toolbox.feature.holiday

import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.holiday.domain.BuildHolidayCountdownUseCase
import com.cangshuo.toolbox.feature.holiday.domain.HolidayFilter
import com.cangshuo.toolbox.feature.holiday.domain.HolidayId
import com.cangshuo.toolbox.feature.holiday.domain.HolidayRepository
import com.cangshuo.toolbox.feature.holiday.domain.LunarDateInfo
import com.cangshuo.toolbox.feature.holiday.ui.HolidayError
import com.cangshuo.toolbox.feature.holiday.ui.HolidayViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HolidayViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun before() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun after() {
        Dispatchers.resetMain()
    }

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
    }

    private class Repository(var fail: Boolean = false) : HolidayRepository {
        override fun nextOccurrences(holiday: HolidayId, fromDate: LocalDate, count: Int): List<LocalDate> {
            if (fail) error("boom")
            val first = when (holiday) {
                HolidayId.NEW_YEAR -> LocalDate.of(2027, 1, 1)
                HolidayId.SPRING_FESTIVAL -> LocalDate.of(2027, 2, 6)
                HolidayId.MID_AUTUMN_FESTIVAL -> LocalDate.of(2027, 9, 15)
                else -> fromDate.plusDays(200L + holiday.ordinal)
            }
            return List(count) { first.plusYears(it.toLong()) }
        }

        override fun lunarDate(date: LocalDate): LunarDateInfo = LunarDateInfo(1, 1, false)
    }

    private fun model(
        handle: SavedStateHandle = SavedStateHandle(),
        clock: MutableClock = MutableClock(Instant.parse("2026-10-17T04:00:00Z")),
        repository: Repository = Repository(),
    ) = HolidayViewModel(
        BuildHolidayCountdownUseCase(
            repository,
            clock,
        ) { ZoneId.of("Asia/Shanghai") },
        handle,
    )

    @Test
    fun defaultsSelectEarliestFestivalAndPersistIt() {
        val handle = SavedStateHandle()
        val viewModel = model(handle)
        val state = viewModel.state.value
        assertEquals(LocalDate.of(2026, 10, 17), state.today)
        assertEquals(HolidayFilter.ALL, state.filter)
        assertEquals(HolidayId.NEW_YEAR, state.selected)
        assertEquals(3, state.selectedUpcoming.size)
        assertEquals(state.items.size, HolidayId.entries.size)
        assertEquals("NEW_YEAR", handle.get<String>("holidaySelected"))
        assertEquals("ALL", handle.get<String>("holidayFilter"))
    }

    @Test
    fun savedFilterAndSelectionRestoreWithinTheFilter() {
        val viewModel = model(
            SavedStateHandle(
                mapOf(
                    "holidayFilter" to "TRADITIONAL",
                    "holidaySelected" to "MID_AUTUMN_FESTIVAL",
                ),
            ),
        )
        assertEquals(HolidayFilter.TRADITIONAL, viewModel.state.value.filter)
        assertEquals(HolidayId.MID_AUTUMN_FESTIVAL, viewModel.state.value.selected)

        val invalid = model(
            SavedStateHandle(
                mapOf(
                    "holidayFilter" to "TRADITIONAL",
                    "holidaySelected" to "CHRISTMAS",
                ),
            ),
        )
        assertEquals(HolidayId.SPRING_FESTIVAL, invalid.state.value.selected)
    }

    @Test
    fun filterAndSelectionStayConsistent() {
        val handle = SavedStateHandle()
        val viewModel = model(handle)
        viewModel.setFilter(HolidayFilter.TRADITIONAL)
        assertEquals(HolidayId.SPRING_FESTIVAL, viewModel.state.value.selected)
        assertTrue(viewModel.state.value.items.all { it.occurrence.id.group.name == "TRADITIONAL" })

        viewModel.select(HolidayId.MID_AUTUMN_FESTIVAL)
        assertEquals(HolidayId.MID_AUTUMN_FESTIVAL, viewModel.state.value.selected)
        assertEquals("MID_AUTUMN_FESTIVAL", handle.get<String>("holidaySelected"))

        viewModel.setFilter(HolidayFilter.GREGORIAN)
        assertNotNull(viewModel.state.value.selected)
        assertTrue(viewModel.state.value.items.all { it.occurrence.id.group.name == "GREGORIAN" })
    }

    @Test
    fun refreshedErrorCanRecover() {
        val repository = Repository(fail = true)
        val viewModel = model(repository = repository)
        assertEquals(HolidayError.CALCULATION_FAILED, viewModel.state.value.error)

        repository.fail = false
        viewModel.refresh()
        assertEquals(null, viewModel.state.value.error)
        assertTrue(viewModel.state.value.items.isNotEmpty())
    }

    @Test
    fun tickerUpdatesAlignedCountdownAndStopsInBackground() = runTest {
        val clock = MutableClock(Instant.parse("2026-10-17T04:00:00.999Z"))
        val viewModel = model(clock = clock)
        val before = viewModel.state.value.selectedUpcoming.first().timing.secondsUntil
        try {
            viewModel.start()
            runCurrent()

            clock.instant = Instant.parse("2026-10-17T04:00:01.001Z")
            advanceTimeBy(1)
            runCurrent()
            assertEquals(before - 1, viewModel.state.value.selectedUpcoming.first().timing.secondsUntil)

            viewModel.stop()
            clock.instant = Instant.parse("2026-10-17T04:00:02Z")
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(before - 1, viewModel.state.value.selectedUpcoming.first().timing.secondsUntil)
        } finally {
            viewModel.stop()
        }
    }

    @Test
    fun crossingMidnightRebuildsTheDataset() = runTest {
        val clock = MutableClock(Instant.parse("2026-10-17T15:59:59.500Z"))
        val viewModel = model(clock = clock)
        assertEquals(LocalDate.of(2026, 10, 17), viewModel.state.value.today)
        try {
            viewModel.start()
            runCurrent()

            clock.instant = Instant.parse("2026-10-17T16:00:00Z")
            advanceTimeBy(500)
            runCurrent()
            assertEquals(LocalDate.of(2026, 10, 18), viewModel.state.value.today)
        } finally {
            viewModel.stop()
        }
    }

    @Test
    fun todayFestivalShowsZeroDaysAndPositiveRemainingTime() {
        val clock = MutableClock(Instant.parse("2027-01-01T04:00:00Z"))
        val viewModel = model(clock = clock)
        val selected = viewModel.state.value.selectedUpcoming.first()
        assertEquals(HolidayId.NEW_YEAR, selected.occurrence.id)
        assertEquals(0L, selected.timing.daysUntil)
        assertTrue(selected.timing.isToday)
        assertTrue(selected.timing.secondsUntil > 0)
        assertFalse(viewModel.state.value.items.isEmpty())
    }
}
