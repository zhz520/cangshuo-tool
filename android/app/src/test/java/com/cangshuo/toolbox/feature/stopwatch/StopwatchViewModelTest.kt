package com.cangshuo.toolbox.feature.stopwatch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.stopwatch.domain.*
import com.cangshuo.toolbox.feature.stopwatch.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StopwatchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private class FakeClock(var now: Long = 10_000L) : StopwatchRepository {
        override fun nowMs() = now
    }
    private fun model(clock: FakeClock = FakeClock(), saved: SavedStateHandle = SavedStateHandle()) =
        StopwatchViewModel(StopwatchUseCases(clock), saved).also { store.put("stopwatch", it) }
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }

    // Stop the persistent ViewModel ticker before runTest advances its scheduler to idle.
    private fun timedTest(block: suspend TestScope.() -> Unit) = runTest {
        try { block() } finally { store.clear() }
    }

    @Test fun displayTicksFromTheMonotonicClockAndFreezesWhenPaused() = timedTest {
        val clock = FakeClock(); val model = model(clock)
        model.start(); runCurrent()
        assertEquals(0L, model.state.value.displayMs); assertFalse(model.state.value.running)
        model.toggleRun(); runCurrent(); assertTrue(model.state.value.running)
        clock.now = 15_000
        advanceTimeBy(StopwatchPolicy.TICK_MS + 1); runCurrent()
        assertEquals(5_000L, model.state.value.displayMs); assertTrue(model.state.value.started)
        model.toggleRun(); runCurrent(); assertFalse(model.state.value.running)
        clock.now = 30_000
        advanceTimeBy(StopwatchPolicy.TICK_MS * 4); runCurrent()
        assertEquals(5_000L, model.state.value.displayMs)
    }
    @Test fun leavingTheForegroundStopsTickingButKeepsTheSession() = timedTest {
        val clock = FakeClock(); val model = model(clock)
        model.start(); model.toggleRun(); runCurrent(); clock.now = 12_000
        advanceTimeBy(StopwatchPolicy.TICK_MS + 1); runCurrent()
        model.stop()
        clock.now = 40_000
        advanceTimeBy(StopwatchPolicy.TICK_MS * 3); runCurrent()
        assertEquals(2_000L, model.state.value.displayMs)
        model.start(); runCurrent()
        assertEquals(30_000L, model.state.value.displayMs); assertTrue(model.state.value.running)
        model.toggleRun(); runCurrent()
        assertEquals(30_000L, model.state.value.displayMs)
    }
    @Test fun lapsRequireRunningAndExposeStatisticsOnlyFromTwoLaps() = timedTest {
        val clock = FakeClock(); val model = model(clock)
        model.start(); model.lap(); runCurrent()
        assertEquals(StopwatchError.NOT_RUNNING, model.state.value.error)
        model.toggleRun(); runCurrent()
        clock.now = 11_000; model.lap()
        clock.now = 14_000; model.lap()
        clock.now = 20_000; model.lap()
        val laps = model.state.value.laps
        assertEquals(listOf(1_000L, 3_000L, 6_000L), laps.map { it.splitMs })
        assertEquals(1, laps.minByOrNull { it.splitMs }!!.number)
        assertEquals(3, laps.maxByOrNull { it.splitMs }!!.number)
        assertEquals(null, model.state.value.error)
    }
    @Test fun lapLimitIsReportedWithoutDroppingExistingLaps() = timedTest {
        val clock = FakeClock(); val model = model(clock)
        model.start(); model.toggleRun(); runCurrent()
        repeat(StopwatchPolicy.MAX_LAPS) { clock.now += 100; model.lap() }
        assertEquals(StopwatchPolicy.MAX_LAPS, model.state.value.laps.size)
        clock.now += 100; model.lap()
        assertEquals(StopwatchError.LAP_LIMIT, model.state.value.error)
        assertEquals(StopwatchPolicy.MAX_LAPS, model.state.value.laps.size)
    }
    @Test fun resetClearsLapsAndTimingButKeepsPreferences() = timedTest {
        val clock = FakeClock(); val model = model(clock)
        model.start(); model.setKeepScreenOn(false); model.selectReport(true)
        model.toggleRun(); runCurrent(); clock.now = 12_000; model.lap(); runCurrent()
        model.reset()
        assertEquals(0L, model.state.value.displayMs); assertTrue(model.state.value.laps.isEmpty())
        assertFalse(model.state.value.started); assertFalse(model.state.value.keepScreenOn); assertTrue(model.state.value.csvReport)
    }
    @Test fun sessionAndLapsSurviveRecreationAndCatchUpWhileRunning() = timedTest {
        val saved = SavedStateHandle(); val clock = FakeClock()
        val first = model(clock, saved)
        first.start(); first.toggleRun(); runCurrent()
        clock.now = 13_500; first.lap(); runCurrent()
        first.stop(); store.clear()
        clock.now = 25_000
        val second = model(clock, saved)
        second.start(); runCurrent()
        assertEquals(15_000L, second.state.value.displayMs)
        assertEquals(1, second.state.value.laps.size); assertEquals(3_500L, second.state.value.laps.first().splitMs)
        assertTrue(second.state.value.running)
        second.toggleRun(); runCurrent(); assertFalse(second.state.value.running)
        assertEquals(15_000L, second.state.value.displayMs)
    }
    @Test fun aRebootedClockCannotCreateAPhantomSegment() = timedTest {
        val saved = SavedStateHandle(mapOf("swRunning" to true, "swAccum" to 7_000L, "swStarted" to 99_999_999L))
        val clock = FakeClock(20_000); val model = model(clock, saved)
        model.start(); runCurrent()
        assertEquals(7_000L, model.state.value.displayMs); assertTrue(model.state.value.running)
    }
    @Test fun corruptedSavedLapsAndCountersAreIgnoredSafely() = timedTest {
        val saved = SavedStateHandle(mapOf("swLaps" to arrayListOf("1,1000,1000", "junk", "2,1000,3000"), "swAccum" to -50L))
        val model = model(FakeClock(), saved)
        assertEquals(listOf(StopwatchLap(1, 1_000, 1_000)), model.state.value.laps)
        assertEquals(1_000L, model.state.value.displayMs)
    }
    @Test fun reportAndListPreferencesPersistWithinTheSession() = timedTest {
        val saved = SavedStateHandle(); val model = model(saved = saved)
        model.selectReport(true); model.showAll(true); store.clear()
        val restored = model(saved = saved)
        assertTrue(restored.state.value.csvReport); assertFalse(restored.state.value.showAll)
    }
    @Test fun repeatedLapsAtTheSameInstantAreRejectedAndResetClearsTheError() = timedTest {
        val clock = FakeClock(); val model = model(clock)
        model.start(); model.toggleRun(); runCurrent()
        clock.now += 1_000; model.lap(); model.lap()
        assertEquals(StopwatchError.LAP_TOO_SOON, model.state.value.error)
        assertEquals(1, model.state.value.laps.size)
        model.reset()
        assertNull(model.state.value.error)
        assertTrue(model.state.value.laps.isEmpty())
    }
}
