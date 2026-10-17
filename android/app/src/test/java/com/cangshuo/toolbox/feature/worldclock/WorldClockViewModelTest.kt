package com.cangshuo.toolbox.feature.worldclock

import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockHourFormat
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockRepository
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockUseCases
import com.cangshuo.toolbox.feature.worldclock.ui.WorldClockError
import com.cangshuo.toolbox.feature.worldclock.ui.WorldClockViewModel
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorldClockViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun before() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun after() {
        Dispatchers.resetMain()
    }

    private class MutableRepository(
        var instant: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        val system: String = "Asia/Shanghai",
    ) : WorldClockRepository {
        override fun now(): Instant = instant
        override fun systemZoneId(): String = system
        override fun availableZoneIds(): Set<String> = ZoneId.getAvailableZoneIds()
    }

    private fun model(
        handle: SavedStateHandle = SavedStateHandle(),
        repository: MutableRepository = MutableRepository(),
    ) = WorldClockViewModel(
        useCases = WorldClockUseCases(repository),
        savedStateHandle = handle,
        localeProvider = { Locale.US },
    )

    @Test
    fun firstLaunchProvidesUsefulDefaultsWithoutDuplicatingSystemZone() {
        val viewModel = model()
        val state = viewModel.state.value
        assertEquals("Asia/Shanghai", state.systemZoneId)
        assertEquals("08:00:00", state.systemReading?.time)
        assertEquals(listOf("Europe/London", "America/New_York"), state.addedZoneIds)
        assertEquals(2, state.readings.size)
        assertTrue(state.searchResults.any { it.id == "Asia/Tokyo" })
        assertNull(state.error)
    }

    @Test
    fun savedConfigurationRestoresZonesFormatAndQuery() {
        val viewModel = model(
            SavedStateHandle(
                mapOf(
                    "worldClockZones" to arrayListOf("Asia/Tokyo", "UTC", "Asia/Tokyo"),
                    "worldClockFormat" to "HOUR_12",
                    "worldClockQuery" to "tok",
                ),
            ),
        )
        val state = viewModel.state.value
        assertEquals(listOf("Asia/Tokyo", "UTC"), state.addedZoneIds)
        assertEquals(WorldClockHourFormat.HOUR_12, state.hourFormat)
        assertEquals("tok", state.query)
        assertTrue(state.readings.first { it.zoneId == "Asia/Tokyo" }.time.endsWith("AM"))
        assertTrue(state.searchResults.any { it.id == "Asia/Tokyo" })
    }

    @Test
    fun addRemoveAndInvalidZoneStatesAreExplicit() = runTest {
        val viewModel = model()
        viewModel.addZone("Asia/Tokyo")
        runCurrent()
        assertTrue("Asia/Tokyo" in viewModel.state.value.addedZoneIds)
        assertNull(viewModel.state.value.error)

        viewModel.addZone("Asia/Tokyo")
        runCurrent()
        assertEquals(WorldClockError.DUPLICATE_ZONE, viewModel.state.value.error)

        viewModel.dismissError()
        runCurrent()
        assertNull(viewModel.state.value.error)

        viewModel.addZone("Mars/Olympus")
        runCurrent()
        assertEquals(WorldClockError.INVALID_ZONE, viewModel.state.value.error)

        viewModel.removeZone("Asia/Tokyo")
        runCurrent()
        assertFalse("Asia/Tokyo" in viewModel.state.value.addedZoneIds)
        assertEquals(2, viewModel.state.value.readings.size)
    }

    @Test
    fun zoneLimitIsEnforcedAcrossSavedAndNewZones() = runTest {
        val extras = ZoneId.getAvailableZoneIds()
            .filterNot { it == "Asia/Shanghai" }
            .sorted()
            .take(23)
        val handle = SavedStateHandle(mapOf("worldClockZones" to ArrayList(extras)))
        val viewModel = model(handle)
        assertEquals(23, viewModel.state.value.addedZoneIds.size)

        val another = ZoneId.getAvailableZoneIds()
            .filterNot { it == "Asia/Shanghai" || it in extras }
            .sorted()
            .first()
        viewModel.addZone(another)
        runCurrent()
        assertEquals(WorldClockError.ZONE_LIMIT, viewModel.state.value.error)
        assertEquals(23, viewModel.state.value.addedZoneIds.size)
    }

    @Test
    fun queryLengthIsBoundedAndSearchErrorsCanBeDismissed() = runTest {
        val viewModel = model()
        viewModel.updateQuery("x".repeat(81))
        runCurrent()
        assertEquals(WorldClockError.QUERY_TOO_LONG, viewModel.state.value.error)
        assertEquals("", viewModel.state.value.query)

        viewModel.dismissError()
        viewModel.updateQuery("纽约")
        runCurrent()
        assertTrue(viewModel.state.value.searchResults.any { it.id == "America/New_York" })

        viewModel.clearQuery()
        runCurrent()
        assertEquals("", viewModel.state.value.query)
        assertTrue(viewModel.state.value.searchResults.size > 1)
    }

    @Test
    fun hourFormatIsAppliedAndPersisted() = runTest {
        val handle = SavedStateHandle()
        val viewModel = model(handle)
        viewModel.setHourFormat(WorldClockHourFormat.HOUR_12)
        runCurrent()
        assertEquals("HOUR_12", handle.get<String>("worldClockFormat"))
        assertTrue(viewModel.state.value.systemReading!!.time.endsWith("AM"))
    }

    @Test
    fun uiLocaleUpdateReformatsDatesWithoutChangingSelection() = runTest {
        val viewModel = model()
        val before = viewModel.state.value.systemReading!!.date
        viewModel.updateLocale(Locale.SIMPLIFIED_CHINESE)
        runCurrent()
        val after = viewModel.state.value.systemReading!!.date
        assertTrue(before != after)
        assertTrue(after.contains("年"))
        assertEquals(listOf("Europe/London", "America/New_York"), viewModel.state.value.addedZoneIds)
    }

    @Test
    fun tickerUpdatesWhileStartedAndStopsWhenScreenLeavesForeground() = runTest {
        val repository = MutableRepository()
        val viewModel = model(repository = repository)
        viewModel.start()
        runCurrent()
        assertEquals("08:00:00", viewModel.state.value.systemReading?.time)

        repository.instant = Instant.parse("2026-01-01T00:00:01Z")
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("08:00:01", viewModel.state.value.systemReading?.time)

        viewModel.stop()
        repository.instant = Instant.parse("2026-01-01T00:00:02Z")
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals("08:00:01", viewModel.state.value.systemReading?.time)
    }

    @Test
    fun tickerAlignsToTheNextWholeSecond() = runTest {
        val repository = MutableRepository(instant = Instant.parse("2026-01-01T00:00:00.200Z"))
        val viewModel = model(repository = repository)
        viewModel.start()
        runCurrent()

        repository.instant = Instant.parse("2026-01-01T00:00:00.999Z")
        advanceTimeBy(799)
        runCurrent()
        assertEquals("08:00:00", viewModel.state.value.systemReading?.time)

        repository.instant = Instant.parse("2026-01-01T00:00:01Z")
        advanceTimeBy(1)
        runCurrent()
        assertEquals("08:00:01", viewModel.state.value.systemReading?.time)
        viewModel.stop()
    }
}
