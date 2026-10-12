package com.cangshuo.toolbox.feature.coinflip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.coinflip.domain.*
import com.cangshuo.toolbox.feature.coinflip.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoinFlipViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private class FakeRepository : CoinRepository {
        var calls = 0
        var fail = false
        var wait = 0L
        override suspend fun flip(count: Int): List<CoinSide> {
            calls++
            delay(wait)
            if (fail) error("Random source unavailable")
            return List(count) { if (it % 2 == 0) CoinSide.HEADS else CoinSide.TAILS }
        }
    }
    private fun model(repo: CoinRepository = FakeRepository(), saved: SavedStateHandle = SavedStateHandle()) =
        CoinFlipViewModel(FlipCoinsUseCase(repo), saved).also { store.put("coin", it) }
    private fun CoinFlipViewModel.single() = flip(false, "Heads", "Tails")
    private fun CoinFlipViewModel.batch() = flip(true, "Heads", "Tails")
    private fun CoinFlipViewModel.finish() = settle(state.value.flipId)
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }

    @Test fun flipsRequireTheForegroundAndFastDoubleTapCannotStartTwoJobs() = runTest {
        val repo = FakeRepository(); val model = model(repo)
        model.single(); runCurrent(); assertEquals(0, repo.calls)
        model.start(); model.single(); model.single(); runCurrent()
        assertEquals(1, repo.calls); assertTrue(model.state.value.busy)
        assertEquals(0L, model.state.value.session.total)
        model.finish(); assertEquals(1L, model.state.value.session.total)
        model.finish(); assertEquals(1L, model.state.value.session.total)
    }
    @Test fun invalidCountsAndIdenticalLabelsDoNotCallTheRandomSource() = runTest {
        val repo = FakeRepository(); val model = model(repo); model.start()
        model.updateCount("101"); model.batch(); runCurrent()
        assertEquals(CoinError.COUNT, model.state.value.error)
        model.updateHeads("same"); model.updateTails("SAME"); model.single(); runCurrent()
        assertEquals(CoinError.LABELS, model.state.value.error); assertEquals(0, repo.calls)
    }
    @Test fun singleFlipDoesNotDependOnAnUnrelatedInvalidBatchCount() = runTest {
        val model = model(); model.start(); model.updateCount(""); model.single(); runCurrent(); model.finish()
        assertEquals(1L, model.state.value.session.total); assertNull(model.state.value.error)
    }
    @Test fun batchAndCustomLabelsAreCommittedTogetherOnlyAfterSettlement() = runTest {
        val model = model(); model.start(); model.updateCount("3"); model.updateHeads("Go"); model.updateTails("Stay")
        model.batch(); runCurrent(); assertEquals(0L, model.state.value.session.total)
        model.finish()
        assertEquals(3L, model.state.value.session.total); assertEquals(2L, model.state.value.session.heads)
        assertEquals(CoinLabels("Go", "Stay"), model.state.value.lastBatch!!.labels)
        model.updateHeads("Buy"); assertEquals("Go", model.state.value.session.records.first().labels.heads)
    }
    @Test fun editsDuringAFlipCannotChangeItsCapturedOptions() = runTest {
        val model = model(); model.start(); model.batch()
        model.updateCount("100"); model.updateHeads("Buy"); model.setAnimation(false); runCurrent(); model.finish()
        assertEquals("10", model.state.value.countInput); assertEquals("", model.state.value.headsInput)
        assertTrue(model.state.value.animation); assertEquals(10, model.state.value.lastBatch!!.sides.size)
    }
    @Test fun stoppingBeforeSettlementDiscardsTheAttemptAndKeepsEarlierResults() = runTest {
        val model = model(); model.start(); model.single(); runCurrent(); model.finish()
        model.batch(); runCurrent(); val cancelledId = model.state.value.flipId
        model.stop(); model.settle(cancelledId)
        assertEquals(1L, model.state.value.session.total); assertFalse(model.state.value.busy)
        assertEquals(CoinError.INTERRUPTED, model.state.value.error)
        model.start(); model.single(); runCurrent(); model.finish(); assertEquals(2L, model.state.value.session.total)
    }
    @Test fun staleNonCancellableResultsCannotOverwriteTheNextAttempt() = runTest {
        var calls = 0
        val repo = object : CoinRepository {
            override suspend fun flip(count: Int): List<CoinSide> {
                val current = ++calls
                if (current == 1) withContext(NonCancellable) { delay(1_000) }
                return List(count) { if (current == 1) CoinSide.TAILS else CoinSide.HEADS }
            }
        }
        val model = model(repo); model.start(); model.single(); runCurrent(); val old = model.state.value.flipId
        model.stop(); model.start(); model.single(); runCurrent(); model.finish()
        advanceUntilIdle(); model.settle(old)
        assertEquals(1L, model.state.value.session.total); assertEquals(CoinSide.HEADS, model.state.value.session.lastSide)
    }
    @Test fun failuresAndTimeoutsExitLoadingWithoutFakeResultsAndCanRetry() = runTest {
        val repo = FakeRepository(); val model = model(repo); model.start()
        repo.fail = true; model.single(); runCurrent()
        assertFalse(model.state.value.busy); assertEquals(CoinError.FAILED, model.state.value.error)
        repo.fail = false; repo.wait = 4_000; model.single(); advanceUntilIdle()
        assertFalse(model.state.value.busy); assertEquals(0L, model.state.value.session.total)
        repo.wait = 0; model.single(); runCurrent(); model.finish()
        assertNull(model.state.value.error); assertEquals(1L, model.state.value.session.total)
    }
    @Test fun clearCancelsPendingWorkButPreservesInputsAndPreferences() = runTest {
        val repo = FakeRepository(); repo.wait = 1_000
        val model = model(repo); model.start(); model.updateHeads("Go"); model.updateCount("50"); model.setAnimation(false)
        model.batch(); runCurrent(); val old = model.state.value.flipId
        model.clear(); advanceUntilIdle(); model.settle(old)
        assertFalse(model.state.value.busy); assertNull(model.state.value.lastBatch)
        assertEquals(0L, model.state.value.session.total); assertEquals("Go", model.state.value.headsInput)
        assertEquals("50", model.state.value.countInput); assertFalse(model.state.value.animation)
    }
    @Test fun tooLongInputIsRejectedAndPreviousEditableTextIsPreserved() {
        val model = model(); model.updateHeads("Go"); model.updateHeads("x".repeat(21))
        assertEquals("Go", model.state.value.headsInput); assertEquals(CoinError.LABELS, model.state.value.error)
        model.updateCount("50"); model.updateCount("1000")
        assertEquals("50", model.state.value.countInput); assertEquals(CoinError.COUNT, model.state.value.error)
    }
    @Test fun savedOptionsRestoreWithoutPersistingResultsOrPendingWork() = runTest {
        val saved = SavedStateHandle(); val model = model(saved = saved); model.start()
        model.updateHeads("Go"); model.updateTails("Stay"); model.updateCount("50"); model.setAnimation(false); model.selectReport(true)
        model.single(); runCurrent(); model.finish(); store.clear()
        val restored = model(saved = saved)
        assertEquals("Go", restored.state.value.headsInput); assertEquals("Stay", restored.state.value.tailsInput)
        assertEquals("50", restored.state.value.countInput); assertFalse(restored.state.value.animation)
        assertTrue(restored.state.value.csvReport); assertEquals(0L, restored.state.value.session.total)
        assertNull(restored.state.value.pending); assertFalse(restored.state.value.busy)
    }
    @Test fun corruptedSavedOptionsStayBoundedAndAreValidatedAtSubmission() = runTest {
        val model = model(saved = SavedStateHandle(mapOf("coinHeads" to "x".repeat(100), "coinCount" to "bad")))
        assertEquals("", model.state.value.headsInput); model.start(); model.batch(); runCurrent()
        assertEquals(CoinError.COUNT, model.state.value.error)
    }
}
