package com.cangshuo.toolbox.feature.reaction

import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.reaction.domain.*
import com.cangshuo.toolbox.feature.reaction.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReactionTest {
    private val dispatcher = StandardTestDispatcher()
    private class Fake : ReactionRepository {
        var now = 10_000L
        var records = emptyList<ReactionRecord>()
        var fail = false
        override fun nowMs() = now
        override fun wallMs() = 1_000_000L
        override fun waitMs() = 1_500L
        override suspend fun history(): List<ReactionRecord> { check(!fail); return records }
        override suspend fun save(record: ReactionRecord): List<ReactionRecord> {
            check(!fail); records = (listOf(record) + records.filterNot { it.id == record.id }).take(20); return records
        }
        override suspend fun clear(): List<ReactionRecord> { check(!fail); records = emptyList(); return records }
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun TestScope.ready(model: ReactionViewModel) {
        model.beginRound(); runCurrent(); advanceTimeBy(1_500); runCurrent()
        assertEquals(ReactionStage.CUE, model.state.value.stage)
        model.cueDrawn()
    }

    @Test fun statisticsUseMedianAndPopulationDeviation() {
        val stats = ReactionPolicy.statistics(listOf(100, 200, 300, 400))!!
        assertEquals(100L, stats.best); assertEquals(400L, stats.worst)
        assertEquals(250.0, stats.mean, 0.0); assertEquals(250.0, stats.median, 0.0)
        assertEquals(111.803, stats.deviation, 0.001)
        assertEquals(200.0, ReactionPolicy.statistics(listOf(300, 100, 200))!!.median, 0.0)
        assertNull(ReactionPolicy.statistics(emptyList()))
    }
    @Test fun historyRejectsPartialCorruptAndOutOfRangeRecords() {
        val valid = ReactionRecord("test", 1, listOf(80, 250, 3000), 0, 0)
        assertTrue(ReactionPolicy.valid(valid))
        assertFalse(ReactionPolicy.valid(valid.copy(samples = listOf(100))))
        assertFalse(ReactionPolicy.valid(valid.copy(samples = listOf(79, 100, 100))))
        assertFalse(ReactionPolicy.valid(valid.copy(early = -1)))
        assertFalse(ReactionPolicy.valid(valid.copy(id = "../../")))
        assertEquals("round,reaction_ms\n1,100\n2,200", ReactionPolicy.csv(listOf(100, 200)))
    }
    @Test fun falseStartCancelsPendingCue() = runTest(dispatcher) {
        val fake = Fake(); val m = ReactionViewModel(ReactionUseCases(fake), SavedStateHandle())
        runCurrent(); m.foreground(); m.beginRound(); runCurrent(); m.tap()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(ReactionStage.EARLY, m.state.value.stage); assertEquals(1, m.state.value.early)
        assertTrue(m.state.value.samples.isEmpty())
    }
    @Test fun cueMustBeDrawnBeforeResponseCanCount() = runTest(dispatcher) {
        val fake = Fake(); val m = ReactionViewModel(ReactionUseCases(fake), SavedStateHandle())
        runCurrent(); m.foreground(); m.beginRound(); runCurrent(); advanceTimeBy(1_500); runCurrent()
        m.tap(); assertEquals(ReactionStage.EARLY, m.state.value.stage)
    }
    @Test fun inputEventTimeIsUsedAndDoubleTapCannotAddSamples() = runTest(dispatcher) {
        val fake = Fake(); val m = ReactionViewModel(ReactionUseCases(fake), SavedStateHandle())
        runCurrent(); m.foreground(); ready(m)
        fake.now += 500; m.tap(fake.now - 250); m.tap()
        assertEquals(listOf(250L), m.state.value.samples)
        assertEquals(ReactionStage.RESULT, m.state.value.stage)
    }
    @Test fun implausibleResponseRetriesWithoutConsumingRound() = runTest(dispatcher) {
        val fake = Fake(); val m = ReactionViewModel(ReactionUseCases(fake), SavedStateHandle())
        runCurrent(); m.foreground(); ready(m); fake.now += 79; m.tap()
        assertEquals(ReactionStage.EARLY, m.state.value.stage); assertTrue(m.state.value.samples.isEmpty())
    }
    @Test fun drawCallbackCannotRestartClock() = runTest(dispatcher) {
        val fake = Fake(); val m = ReactionViewModel(ReactionUseCases(fake), SavedStateHandle())
        runCurrent(); m.foreground(); ready(m); fake.now += 100; m.cueDrawn(); fake.now += 100; m.tap()
        assertEquals(listOf(200L), m.state.value.samples)
    }
    @Test fun timeoutAndLateEventDoNotBecomeSlowValidResults() = runTest(dispatcher) {
        val fake = Fake(); val m = ReactionViewModel(ReactionUseCases(fake), SavedStateHandle())
        runCurrent(); m.foreground(); ready(m); advanceTimeBy(3_001); runCurrent()
        assertEquals(ReactionStage.TIMEOUT, m.state.value.stage); assertEquals(1, m.state.value.missed)
        ready(m); fake.now += 3_001; m.tap()
        assertEquals(2, m.state.value.missed); assertTrue(m.state.value.samples.isEmpty())
    }
    @Test fun backgroundInvalidatesCallbacksAndPreservesEarlierResults() = runTest(dispatcher) {
        val fake = Fake(); val m = ReactionViewModel(ReactionUseCases(fake), SavedStateHandle())
        runCurrent(); m.foreground(); ready(m); fake.now += 200; m.tap()
        m.beginRound(); runCurrent(); m.background(); advanceTimeBy(10_000); runCurrent(); m.cueDrawn(); m.tap()
        assertEquals(ReactionStage.INTERRUPTED, m.state.value.stage); assertEquals(listOf(200L), m.state.value.samples)
    }
    @Test fun threeRoundSessionPersistsOnceAndClearDoesNotResurrect() = runTest(dispatcher) {
        val fake = Fake(); val saved = SavedStateHandle(); val m = ReactionViewModel(ReactionUseCases(fake), saved)
        runCurrent(); m.foreground(); m.selectRounds(3)
        repeat(3) { ready(m); fake.now += 200 + it; m.tap(); runCurrent() }
        assertEquals(ReactionStage.COMPLETE, m.state.value.stage); assertEquals(1, fake.records.size)
        m.tap(); m.beginRound(); runCurrent(); assertEquals(1, fake.records.size)
        m.clearHistory(); runCurrent(); assertTrue(fake.records.isEmpty())
        ReactionViewModel(ReactionUseCases(fake), saved); runCurrent(); assertTrue(fake.records.isEmpty())
    }
    @Test fun failedSaveCanRetryWithoutDuplicateAndBlocksDiscard() = runTest(dispatcher) {
        val fake = Fake(); val m = ReactionViewModel(ReactionUseCases(fake), SavedStateHandle())
        runCurrent(); m.foreground(); m.selectRounds(3)
        repeat(2) { ready(m); fake.now += 200; m.tap() }
        ready(m); fake.fail = true; fake.now += 200; m.tap(); runCurrent()
        assertEquals(ReactionStorageError.SAVE, m.state.value.storageError)
        m.newSession(); assertEquals(3, m.state.value.samples.size)
        fake.fail = false; m.retry(); runCurrent(); assertEquals(1, fake.records.size)
    }
    @Test fun processRestoreNeverContinuesMeasuringAnOldCue() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("rxActive" to true, "rxSamples" to longArrayOf(200), "rxRounds" to 5))
        val m = ReactionViewModel(ReactionUseCases(Fake()), saved); runCurrent()
        assertEquals(ReactionStage.INTERRUPTED, m.state.value.stage); assertEquals(listOf(200L), m.state.value.samples)
    }
    @Test fun roundSelectionOnlyAllowedBeforeStarting() = runTest(dispatcher) {
        val fake = Fake(); val m = ReactionViewModel(ReactionUseCases(fake), SavedStateHandle())
        runCurrent(); m.selectRounds(7); assertEquals(5, m.state.value.rounds)
        m.foreground(); ready(m); m.selectRounds(3); assertEquals(5, m.state.value.rounds)
        m.newSession(); assertEquals(ReactionStage.IDLE, m.state.value.stage)
        advanceTimeBy(10_000); runCurrent(); assertEquals(ReactionStage.IDLE, m.state.value.stage)
    }
}
