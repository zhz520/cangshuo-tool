package com.cangshuo.toolbox.feature.holiday

import com.cangshuo.toolbox.feature.holiday.domain.BuildHolidayCountdownUseCase
import com.cangshuo.toolbox.feature.holiday.domain.HolidayDataset
import com.cangshuo.toolbox.feature.holiday.domain.HolidayId
import com.cangshuo.toolbox.feature.holiday.domain.HolidayLoadResult
import com.cangshuo.toolbox.feature.holiday.domain.HolidayOccurrence
import com.cangshuo.toolbox.feature.holiday.domain.HolidayRepository
import com.cangshuo.toolbox.feature.holiday.domain.LunarDateInfo
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HolidayUseCaseTest {
    private class Repository(
        private val dates: Map<HolidayId, List<LocalDate>> = emptyMap(),
        var fail: Boolean = false,
    ) : HolidayRepository {
        override fun nextOccurrences(holiday: HolidayId, fromDate: LocalDate, count: Int): List<LocalDate> {
            if (fail) error("boom")
            return dates[holiday] ?: List(count) { fromDate.plusDays(30L + holiday.ordinal + it * 365L) }
        }

        override fun lunarDate(date: LocalDate): LunarDateInfo = LunarDateInfo(1, 1, false)
    }

    private fun useCases(
        repository: HolidayRepository = Repository(),
        instant: Instant = Instant.parse("2026-10-17T04:00:00Z"),
        zone: ZoneId = ZoneId.of("Asia/Shanghai"),
    ) = BuildHolidayCountdownUseCase(repository, Clock.fixed(instant, ZoneId.of("UTC"))) { zone }

    @Test
    fun loadBuildsThreeOccurrencesForEveryFestival() {
        val result = useCases().load()
        assertTrue(result is HolidayLoadResult.Success)
        val dataset = (result as HolidayLoadResult.Success).dataset
        assertEquals(LocalDate.of(2026, 10, 17), dataset.today)
        assertEquals(ZoneId.of("Asia/Shanghai"), dataset.zoneId)
        assertEquals(HolidayId.entries.size, dataset.occurrences.size)
        assertTrue(dataset.occurrences.values.all { it.size == 3 })
    }

    @Test
    fun timingCountsToTargetMidnightAndEndsTodayAtNextMidnight() {
        val useCases = useCases(instant = Instant.parse("2026-12-31T14:00:00Z"))
        val dataset = HolidayDataset(
            today = LocalDate.of(2026, 12, 31),
            zoneId = ZoneId.of("Asia/Shanghai"),
            occurrences = emptyMap(),
        )
        val future = HolidayOccurrence(HolidayId.NEW_YEAR, LocalDate.of(2027, 1, 1), LunarDateInfo(1, 1, false))
        val futureTiming = useCases.timing(future, Instant.parse("2026-12-31T14:00:00Z"), dataset)
        assertEquals(1L, futureTiming.daysUntil)
        assertEquals(7_200L, futureTiming.secondsUntil)
        assertFalse(futureTiming.isToday)

        val todayDataset = dataset.copy(today = LocalDate.of(2027, 1, 1))
        val todayTiming = useCases.timing(
            future.copy(date = LocalDate.of(2027, 1, 1)),
            Instant.parse("2027-01-01T04:00:00Z"),
            todayDataset,
        )
        assertEquals(0L, todayTiming.daysUntil)
        assertEquals(43_200L, todayTiming.secondsUntil)
        assertTrue(todayTiming.isToday)
    }

    @Test
    fun timingUsesZoneRulesAcrossDaylightSavingChanges() {
        val useCases = useCases(
            instant = Instant.parse("2026-03-07T05:00:00Z"),
            zone = ZoneId.of("America/New_York"),
        )
        val dataset = HolidayDataset(
            today = LocalDate.of(2026, 3, 7),
            zoneId = ZoneId.of("America/New_York"),
            occurrences = emptyMap(),
        )
        val target = HolidayOccurrence(
            HolidayId.NEW_YEAR,
            LocalDate.of(2026, 3, 9),
            LunarDateInfo(1, 1, false),
        )
        val timing = useCases.timing(target, Instant.parse("2026-03-07T05:00:00Z"), dataset)
        assertEquals(2L, timing.daysUntil)
        assertEquals(47L * 3_600L, timing.secondsUntil)
    }

    @Test
    fun repositoryFailureIsExplicit() {
        val result = useCases(Repository(fail = true)).load()
        assertEquals(HolidayLoadResult.Failure, result)
    }
}
