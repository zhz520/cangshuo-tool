package com.cangshuo.toolbox.feature.fps

import com.cangshuo.toolbox.feature.fps.domain.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class FpsPolicyTest {
    private fun record(a: FpsAccumulator) = requireNotNull(a.snapshot("test-id", 1L))
    private fun sample(vararg gaps: Long, budget: Double = 60.0): FpsRecord {
        val a = FpsAccumulator(FpsRequest(), budget)
        var time = 0L
        a.frame(time, 60.0)
        gaps.forEach { time += it; a.frame(time, 60.0) }
        return record(a)
    }

    @Test fun firstCallbackOnlyAnchorsTheClock() {
        val a = FpsAccumulator(FpsRequest(), 60.0)
        assertNull(a.snapshot("test", 1))
        a.frame(9_000_000_000L, 60.0)
        assertEquals(FpsStage.RUNNING, a.stage)
        assertNull(a.snapshot("test", 1))
        a.frame(9_016_666_667L, 60.0)
        val r = record(a)
        assertEquals(1, r.frames)
        assertEquals(16_666_667L, r.elapsedNanos)
        assertEquals(60.0, r.fps, .001)
        assertTrue(FpsPolicy.valid(r))
    }
    @Test fun computesFullSessionFromActualNanoseconds() {
        for (hz in listOf(30, 60, 90, 120, 144, 240)) {
            val a = FpsAccumulator(FpsRequest(), hz.toDouble())
            for (i in 0..(hz * 10)) a.frame(i * 1_000_000_000L / hz, hz.toDouble())
            val r = record(a)
            assertEquals(FpsStage.COMPLETE, a.stage)
            assertEquals(hz * 10, r.frames)
            assertEquals(hz.toDouble(), r.fps, .001)
            assertTrue(r.complete)
            assertTrue(FpsPolicy.valid(r))
            a.frame(20_000_000_000, hz.toDouble())
            assertEquals(r, record(a))
        }
    }
    @Test fun completionIncludesTheLastWholeInterval() {
        val a = FpsAccumulator(FpsRequest(), 60.0)
        a.frame(0, 60.0)
        a.frame(9_900_000_000, 60.0)
        a.frame(10_100_000_000, 60.0)
        val r = record(a)
        assertEquals(10_100_000_000, r.elapsedNanos)
        assertEquals(2, r.frames)
        assertTrue(FpsPolicy.valid(r))
    }
    @Test fun duplicateNegativeAndOutOfOrderTimestampsDoNotMoveAnchor() {
        val a = FpsAccumulator(FpsRequest(), 60.0)
        a.frame(-1, 60.0); a.frame(100, 60.0)
        a.frame(100, 60.0); a.frame(99, 60.0); a.frame(200, 60.0)
        assertEquals(3, record(a).ignored)
        assertEquals(100, record(a).elapsedNanos)
        assertEquals(1, record(a).frames)
    }
    @Test fun longFrameThresholdAndMissingSlotsAreSeparate() {
        val r = sample(16_666_667, 25_000_000, 33_333_333, 50_000_000)
        assertEquals(2, r.longFrames)
        assertEquals(4, r.estimatedMissed)
        assertEquals(50.0, r.longPercent, 0.0)
    }
    @Test fun percentilesUseNearestRankAndSnapshotsDoNotMutateIntervals() {
        val a = FpsAccumulator(FpsRequest(), 60.0)
        a.frame(0, 60.0)
        var time = 0L
        for (n in 100 downTo 1) { time += n; a.frame(time, 60.0) }
        val first = record(a)
        assertEquals(95, first.p95Nanos)
        assertEquals(99, first.p99Nanos)
        assertEquals(100, first.worstNanos)
        assertEquals(first, record(a))
        a.frame(time + 101, 60.0)
        assertEquals(101, record(a).frames)
        assertEquals(100, first.frames)
    }
    @Test fun manualBaselineDoesNotPolluteObservedDisplayRate() {
        val r = sample(16_666_667, budget = 30.0)
        assertEquals(30.0, r.budgetHz, 0.0)
        assertEquals(60.0, r.minRefresh, 0.0)
        assertEquals(60.0, r.maxRefresh, 0.0)
        assertEquals(0, r.longFrames)
    }
    @Test fun tracksRefreshChangesAndIgnoresInvalidRates() {
        val a = FpsAccumulator(FpsRequest(), 60.0)
        a.frame(0, 120.0); a.frame(1, Double.NaN); a.frame(2, 90.0)
        a.frame(3, Double.POSITIVE_INFINITY); a.frame(4, 0.0)
        assertEquals(90.0, record(a).minRefresh, 0.0)
        assertEquals(120.0, record(a).maxRefresh, 0.0)
    }
    @Test fun windowsIncludeThePartialTailWithConservedTotals() {
        val r = sample(600_000_000, 600_000_000, 100_000_000)
        assertEquals(listOf(FpsWindow(1_200_000_000, 2), FpsWindow(100_000_000, 1)), r.windows)
        assertEquals(r.elapsedNanos, r.windows.sumOf { it.nanos })
        assertEquals(r.frames, r.windows.sumOf { it.count })
        assertTrue(FpsPolicy.valid(r))
    }
    @Test fun hugeGapInterruptsRatherThanBecomingAResult() {
        val a = FpsAccumulator(FpsRequest(), 60.0)
        a.frame(0, 60.0); a.frame(10, 60.0); a.frame(10 + FpsPolicy.MAX_GAP + 1, 60.0)
        assertEquals(FpsStage.INTERRUPTED, a.stage)
        assertEquals(10, record(a).elapsedNanos)
        assertFalse(record(a).complete)
    }
    @Test fun maximumGapIsAcceptedButSamplesAreBounded() {
        assertTrue(FpsPolicy.valid(sample(FpsPolicy.MAX_GAP)))
        val a = FpsAccumulator(FpsRequest(), 60.0)
        for (n in 0..(FpsPolicy.MAX_SAMPLES + 10)) a.frame(n.toLong(), 60.0)
        assertEquals(FpsStage.LIMIT, a.stage)
        assertEquals(FpsPolicy.MAX_SAMPLES, record(a).frames)
        assertTrue(FpsPolicy.valid(record(a)))
    }
    @Test fun onlySupportedRequestsAndFiniteRatesAreAccepted() {
        assertFalse(FpsPolicy.valid(FpsRequest(seconds = 0)))
        assertFalse(FpsPolicy.valid(FpsRequest(seconds = 11)))
        assertFalse(FpsPolicy.valid(FpsRequest(targetHz = 61)))
        assertFalse(FpsPolicy.valid(FpsRequest(objects = 25)))
        for (rate in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, 19.9, 360.1)) assertFalse(FpsPolicy.refresh(rate))
        for (rate in listOf(20.0, 59.94, 360.0)) assertTrue(FpsPolicy.refresh(rate))
        assertThrows(IllegalArgumentException::class.java) { FpsAccumulator(FpsRequest(), 0.0) }
    }
    @Test fun storageRejectsCorruptOrInconsistentAggregates() {
        val r = sample(16_666_667, 16_666_667)
        val corrupt = listOf(r.copy(id = "bad\n"), r.copy(at = 0), r.copy(frames = 0), r.copy(frames = 3),
            r.copy(p95Nanos = 0), r.copy(p99Nanos = r.worstNanos + 1), r.copy(worstNanos = FpsPolicy.MAX_GAP + 1),
            r.copy(estimatedMissed = -1), r.copy(longFrames = 3), r.copy(ignored = -1), r.copy(windows = emptyList()),
            r.copy(minRefresh = 120.0), r.copy(maxRefresh = Double.NaN), r.copy(complete = true),
            r.copy(elapsedNanos = r.elapsedNanos + 1))
        corrupt.forEach { assertFalse(it.toString(), FpsPolicy.valid(it)) }
    }
    @Test fun csvUsesLocaleIndependentNumbersAndActualWindowDuration() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val r = sample(600_000_000, 600_000_000, 100_000_000)
            val rows = FpsPolicy.csv(r).trim().lines().map { it.split(',') }
            assertEquals(3, rows.size)
            assertTrue(rows.all { it.size == 7 })
            assertEquals("1200.0", rows[1][1]); assertEquals("1300.0", rows[2][1])
            assertEquals("false", rows[2][6]); assertEquals("100.0", rows[2][4])
            assertEquals("10.0", rows[2][2])
        } finally { Locale.setDefault(original) }
    }
}
