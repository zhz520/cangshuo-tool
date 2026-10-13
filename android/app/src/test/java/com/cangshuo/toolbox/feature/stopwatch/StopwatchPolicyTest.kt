package com.cangshuo.toolbox.feature.stopwatch

import com.cangshuo.toolbox.feature.stopwatch.domain.*
import org.junit.Assert.*
import org.junit.Test

class StopwatchPolicyTest {
    private fun laps(count: Int): List<StopwatchLap> =
        (1..count).map { StopwatchLap(it, 1_000L, it * 1_000L) }

    @Test fun displayFormatSwitchesFromMinutesToHoursAtExactlyOneHour() {
        assertEquals("00:00.00", StopwatchPolicy.format(0))
        assertEquals("00:01.23", StopwatchPolicy.format(1_234))
        assertEquals("01:01.00", StopwatchPolicy.format(61_000))
        assertEquals("59:59.99", StopwatchPolicy.format(3_599_990))
        assertEquals("1:00:00.00", StopwatchPolicy.format(3_600_000))
        assertEquals("99:59:59.99", StopwatchPolicy.format(StopwatchPolicy.MAX_DISPLAY_MS))
    }
    @Test fun negativeAndOverflowValuesClampInsteadOfBreakingTheLayout() {
        assertEquals("00:00.00", StopwatchPolicy.format(-5))
        assertEquals("99:59:59.99", StopwatchPolicy.format(StopwatchPolicy.MAX_DISPLAY_MS + 1_000_000))
        assertEquals("99:59:59.99", StopwatchPolicy.format(Long.MAX_VALUE))
    }
    @Test fun pauseResumeAccumulatesOnlyTheRunningSegments() {
        val started = StopwatchPolicy.start(StopwatchSession(), 1_000)
        assertEquals(1_500L, started.elapsedMs(2_500))
        val paused = StopwatchPolicy.pause(started, 2_500)
        assertEquals(1_500L, paused.elapsedMs(50_000))
        val resumed = StopwatchPolicy.start(paused, 60_000)
        assertEquals(6_500L, resumed.elapsedMs(65_000))
    }
    @Test fun startAndPauseAreIdempotent() {
        val started = StopwatchPolicy.start(StopwatchSession(), 100)
        assertEquals(started, StopwatchPolicy.start(started, 5_000))
        val paused = StopwatchPolicy.pause(started, 600)
        assertEquals(paused, StopwatchPolicy.pause(paused, 9_000))
    }
    @Test fun aStaleClockCannotMakeElapsedTimeGoBackwards() {
        val started = StopwatchPolicy.start(StopwatchSession(), 10_000)
        assertEquals(0L, started.elapsedMs(9_000))
    }
    @Test fun lapsSplitAgainstThePreviousTotalAndNumberFromOne() {
        var session = StopwatchPolicy.start(StopwatchSession(), 0)
        session = StopwatchPolicy.lap(session, 1_200)!!
        session = StopwatchPolicy.lap(session, 3_000)!!
        assertEquals(2, session.laps.size)
        assertEquals(StopwatchLap(1, 1_200, 1_200), session.laps[0])
        assertEquals(StopwatchLap(2, 1_800, 3_000), session.laps[1])
    }
    @Test fun lapsAreRejectedWhilePausedOrAtZeroOrBeyondTheBudget() {
        assertNull(StopwatchPolicy.lap(StopwatchSession(), 1_000))
        val running = StopwatchPolicy.start(StopwatchSession(), 500)
        assertNull(StopwatchPolicy.lap(running, 500))
        var full = StopwatchPolicy.start(StopwatchSession(), 0)
        (1..StopwatchPolicy.MAX_LAPS).forEach { full = StopwatchPolicy.lap(full, it * 100L)!! }
        assertEquals(StopwatchPolicy.MAX_LAPS, full.laps.size)
        assertNull(StopwatchPolicy.lap(full, StopwatchPolicy.MAX_LAPS * 100L + 1_000))
    }
    @Test fun statisticsNeedAtLeastTwoLapsExceptTheAverage() {
        val one = laps(1)
        assertNull(StopwatchPolicy.fastestLap(one)); assertNull(StopwatchPolicy.slowestLap(one))
        assertEquals(1_000L, StopwatchPolicy.averageSplitMs(one))
        assertEquals(null, StopwatchPolicy.averageSplitMs(emptyList()))
        val uneven = listOf(StopwatchLap(1, 5_000, 5_000), StopwatchLap(2, 3_000, 8_000), StopwatchLap(3, 4_000, 12_000))
        assertEquals(2, StopwatchPolicy.fastestLap(uneven)!!.number)
        assertEquals(1, StopwatchPolicy.slowestLap(uneven)!!.number)
        assertEquals(4_000L, StopwatchPolicy.averageSplitMs(uneven))
    }
    @Test fun csvKeepsStableColumnsAndLineEndings() {
        val csv = StopwatchPolicy.csv(laps(2))
        assertEquals("lap,split_ms,total_ms\r\n1,1000,1000\r\n2,1000,2000\r\n", csv)
        assertEquals("lap,split_ms,total_ms\r\n", StopwatchPolicy.csv(emptyList()))
    }
    @Test fun savedStateRoundTripsAndDropsCorruptedEntries() {
        val original = laps(3)
        assertEquals(original, StopwatchPolicy.decode(StopwatchPolicy.encode(original)))
        val corrupted = listOf("1,1000,1000", "oops", "2,500,1500", "3,0,1500", "3,1000,2500", "4,0,2500")
        assertEquals(listOf(StopwatchLap(1, 1_000, 1_000), StopwatchLap(2, 500, 1_500), StopwatchLap(3, 1_000, 2_500)),
            StopwatchPolicy.decode(corrupted))
    }
    @Test fun restoreRebuildsRunningSessionsAndClampsARebootedClock() {
        val restored = StopwatchPolicy.restore(true, 2_000, 10_000, laps(1), 12_500)
        assertTrue(restored.running); assertEquals(4_500L, restored.elapsedMs(12_500))
        val rebooted = StopwatchPolicy.restore(true, 7_000, 99_999_999, emptyList(), 20_000)
        assertEquals(7_000L, rebooted.elapsedMs(20_000))
        assertEquals(StopwatchSession(false, 2_000, null, laps(1)), StopwatchPolicy.restore(false, 2_000, 10_000, laps(1), 12_000))
        assertEquals(0L, StopwatchPolicy.restore(true, -5, 10_000, emptyList(), 11_000).accumulatedMs)
    }
}
