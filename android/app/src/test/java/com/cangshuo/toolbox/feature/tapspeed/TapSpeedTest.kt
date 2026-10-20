package com.cangshuo.toolbox.feature.tapspeed

import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.tapspeed.domain.*
import com.cangshuo.toolbox.feature.tapspeed.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TapSpeedTest {
    private val dispatcher = StandardTestDispatcher()
    private class Fake : TapRepository {
        var clock = 10_000L
        var records = emptyList<TapRecord>()
        var fail = false
        override fun now() = clock
        override fun wall() = 1_000_000L
        override suspend fun history(): List<TapRecord> { check(!fail); return records }
        override suspend fun save(record: TapRecord): List<TapRecord> {
            check(!fail); records = (listOf(record) + records.filterNot { it.id == record.id }).take(20); return records
        }
        override suspend fun clear(): List<TapRecord> { check(!fail); records = emptyList(); return records }
    }
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private fun armed(seconds: Int = 5) = TapSession(stage = TapStage.ARMED, seconds = seconds)

    @Test fun firstPressStartsAndCountsWithoutDividingByZero() {
        val hit = TapPolicy.hit(armed(), 100, 100, TapPolicy.TOUCH)
        assertEquals(TapStage.RUNNING, hit.stage); assertEquals(100L, hit.startedAt)
        assertEquals(1, hit.taps); assertEquals(1.0, hit.average, 0.0)
    }
    @Test fun deadlineIsExclusiveAndFinishesEvenBeforeTickerRuns() {
        val one = TapPolicy.hit(armed(1), 100, 100, TapPolicy.TOUCH)
        val two = TapPolicy.hit(one, 1099, 1099, TapPolicy.TOUCH)
        assertEquals(2, two.taps)
        val done = TapPolicy.hit(two, 1100, 1100, TapPolicy.TOUCH)
        assertEquals(TapStage.COMPLETE, done.stage); assertEquals(2, done.taps); assertEquals(2.0, done.average, 0.0)
    }
    @Test fun countsAreAssignedToCorrectSecondAndZerosArePreserved() {
        var s = TapPolicy.hit(armed(), 100, 100, TapPolicy.TOUCH)
        s = TapPolicy.hit(s, 1099, 1099, TapPolicy.TOUCH)
        s = TapPolicy.hit(s, 1100, 1100, TapPolicy.TOUCH)
        s = TapPolicy.hit(s, 4100, 4100, TapPolicy.TOUCH)
        s = TapPolicy.advance(s, 5100)
        assertEquals(listOf(2, 1, 0, 0, 1), s.bins); assertEquals(2, s.peak); assertEquals(.8, s.average, 0.0)
    }
    @Test fun futureOutOfOrderAndInvalidSourcesAreRejected() {
        val s = TapPolicy.hit(armed(), 100, 100, TapPolicy.TOUCH)
        assertEquals(s, TapPolicy.hit(s, 200, 150, TapPolicy.TOUCH))
        assertEquals(s, TapPolicy.hit(s, 99, 100, TapPolicy.TOUCH))
        assertEquals(s, TapPolicy.hit(s, 100, 100, 7))
        assertEquals(s, TapPolicy.hit(s, -1, 100, TapPolicy.TOUCH))
    }
    @Test fun simultaneousPressRulesIgnoreHoldingAndCountMultitouch() {
        assertEquals(1, TapPolicy.downCount(false, false, 3))
        assertEquals(0, TapPolicy.downCount(false, true, 2))
        assertEquals(2, TapPolicy.downCount(true, true, 2))
        assertEquals(0, TapPolicy.downCount(true, false, 0))
    }
    @Test fun equalTimestampsCanRepresentSeparateFingersAndSourcesStayVisible() {
        val one = TapPolicy.hit(armed(), 100, 100, TapPolicy.TOUCH)
        val two = TapPolicy.hit(one, 100, 100, TapPolicy.KEYBOARD)
        assertEquals(2, two.taps); assertEquals(3, two.sources)
    }
    @Test fun resourceCapStopsWithoutPretendingFullCompletion() {
        val s = TapSession(TapStage.RUNNING, seconds = 1, startedAt = 100, bins = listOf(9999), sources = 1, lastInputAt = 100)
        val capped = TapPolicy.hit(s, 200, 200, TapPolicy.TOUCH)
        assertEquals(TapStage.LIMIT, capped.stage); assertEquals(10000, capped.taps)
        assertEquals(capped, TapPolicy.hit(capped, 300, 300, TapPolicy.TOUCH))
    }
    @Test fun historyValidationRejectsCorruptCountsAndInvalidDurations() {
        val good = TapRecord("test", 1, 1, false, listOf(3), 1)
        assertTrue(TapPolicy.valid(good)); assertFalse(TapPolicy.valid(good.copy(bins = listOf(-1))))
        assertFalse(TapPolicy.valid(good.copy(bins = listOf(0))))
        assertFalse(TapPolicy.valid(good.copy(seconds = 2)))
        assertFalse(TapPolicy.valid(good.copy(sources = 0)))
        assertFalse(TapPolicy.validBins(60, List(60) { 10000 }))
    }
    @Test fun csvMarksIncompleteAndContainsOnlyObservedBins() {
        val s = TapSession(TapStage.INTERRUPTED, seconds = 5, bins = listOf(2, 0, 0, 0, 0), elapsedMs = 500, sources = 1)
        assertEquals(listOf(2), TapPolicy.visibleBins(s))
        assertTrue(TapPolicy.csv(s).endsWith("1,2,5,500,false,1,false"))
    }
    @Test fun wallClockDoesNotControlTheDeadlineAndClockRegressionDoesNotReverseProgress() {
        val s = TapPolicy.hit(armed(), 100, 100, TapPolicy.TOUCH)
        val later = TapPolicy.advance(s, 600)
        assertEquals(500L, TapPolicy.advance(later, 200).elapsedMs)
        assertEquals(5000L, TapPolicy.advance(later, Long.MAX_VALUE).elapsedMs)
    }
    @Test fun armWaitsForFirstTapAndPresetCannotChangeDuringRun() = runTest(dispatcher) {
        val fake = Fake(); val m = TapViewModel(TapUseCases(fake), SavedStateHandle())
        runCurrent(); m.foreground(); m.selectSeconds(1); m.arm(); advanceTimeBy(5000); runCurrent()
        assertEquals(TapStage.ARMED, m.state.value.session.stage); assertEquals(0, m.state.value.session.taps)
        m.selectSeconds(5); m.tap(); assertEquals(1, m.state.value.session.seconds)
        fake.clock += 1000; advanceTimeBy(50); runCurrent()
        assertEquals(TapStage.COMPLETE, m.state.value.session.stage); assertEquals(1, fake.records.size)
    }
    @Test fun backgroundCancelsActiveSessionWithoutSaving() = runTest(dispatcher) {
        val fake = Fake(); val m = TapViewModel(TapUseCases(fake), SavedStateHandle())
        runCurrent(); m.foreground(); m.arm(); m.tap(); fake.clock += 100; m.background();
        fake.clock += 5000; advanceTimeBy(6000); runCurrent(); m.tap()
        assertEquals(TapStage.INTERRUPTED, m.state.value.session.stage); assertTrue(fake.records.isEmpty())
    }
    @Test fun saveFailurePreservesResultAndRetriesExactlyOnce() = runTest(dispatcher) {
        val fake = Fake(); val m = TapViewModel(TapUseCases(fake), SavedStateHandle())
        runCurrent(); m.foreground(); m.selectSeconds(1); m.arm(); m.tap(); fake.fail = true
        fake.clock += 1000; advanceTimeBy(50); runCurrent()
        assertEquals(TapStorageError.SAVE, m.state.value.error); m.restart(); assertEquals(TapStage.COMPLETE, m.state.value.session.stage)
        fake.fail = false; m.retry(); runCurrent(); assertEquals(1, fake.records.size)
        m.retry(); runCurrent(); assertEquals(1, fake.records.size)
    }
    @Test fun clearHistoryDoesNotResurrectCompletedSavedState() = runTest(dispatcher) {
        val fake = Fake(); val saved = SavedStateHandle(); val m = TapViewModel(TapUseCases(fake), saved)
        runCurrent(); m.foreground(); m.selectSeconds(1); m.arm(); m.tap(); fake.clock += 1000; advanceTimeBy(50); runCurrent()
        m.clearHistory(); runCurrent(); TapViewModel(TapUseCases(fake), saved); runCurrent()
        assertTrue(fake.records.isEmpty())
    }
    @Test fun processRestoreInterruptsOldMeasurementButPreservesPartialCounts() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("tapStarted" to true, "tapBins" to intArrayOf(2), "tapSeconds" to 1, "tapSources" to 1))
        val m = TapViewModel(TapUseCases(Fake()), saved); runCurrent()
        assertEquals(TapStage.INTERRUPTED, m.state.value.session.stage); assertEquals(2, m.state.value.session.taps)
    }
    @Test fun stalePressBeforeArmingCannotStartANewSession() = runTest(dispatcher) {
        val fake = Fake(); val m = TapViewModel(TapUseCases(fake), SavedStateHandle())
        runCurrent(); m.foreground(); m.arm(); m.tap(fake.clock - 1)
        assertEquals(TapStage.ARMED, m.state.value.session.stage); assertEquals(0, m.state.value.session.taps)
    }
}
