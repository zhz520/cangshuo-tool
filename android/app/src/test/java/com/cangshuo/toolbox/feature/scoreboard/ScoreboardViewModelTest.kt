package com.cangshuo.toolbox.feature.scoreboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreboardClock
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreboardPolicy
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreboardRepository
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreboardUseCases
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreSide
import com.cangshuo.toolbox.feature.scoreboard.ui.ScoreboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScoreboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    private class FakeClock(var monotonic: Long = 10_000L, var epoch: Long = 1_700_000_000_000L) : ScoreboardRepository {
        override fun clock() = ScoreboardClock(monotonic, epoch)
    }

    private fun FakeClock.advance(ms: Long) {
        monotonic += ms
        epoch += ms
    }

    private fun model(clock: FakeClock = FakeClock(), saved: SavedStateHandle = SavedStateHandle()) =
        ScoreboardViewModel(ScoreboardUseCases(clock), saved).also { store.put("scoreboard", it) }

    private fun timedTest(block: suspend TestScope.() -> Unit) = runTest {
        try { block() } finally { store.clear() }
    }

    @Before fun before() { Dispatchers.setMain(dispatcher) }

    @After fun after() { store.clear(); Dispatchers.resetMain() }

    @Test fun scoreStepUndoAndPersistWorkImmediately() = timedTest {
        val clock = FakeClock()
        val saved = SavedStateHandle()
        val model = model(clock, saved)
        model.setStep(3)
        clock.epoch += 1
        model.increment(ScoreSide.LEFT)
        clock.epoch += 1
        model.increment(ScoreSide.LEFT)
        model.decrement(ScoreSide.RIGHT)
        assertEquals(6, model.state.value.session.core.leftScore)
        assertEquals(0, model.state.value.session.core.rightScore)
        assertEquals(2, model.state.value.session.journal.size)
        assertEquals(6, saved.get<Int>("sbLeftScore"))
        assertEquals(3, saved.get<Int>("sbStep"))
        model.undo()
        assertEquals(3, model.state.value.session.core.leftScore)
        assertEquals(1, model.state.value.session.journal.size)
    }

    @Test fun namesScoresSwapAndNextPeriodShareTheSameUndoJournal() = timedTest {
        val model = model()
        model.editNames("主队", "客队")
        model.setScore(ScoreSide.LEFT, 12)
        model.setScore(ScoreSide.RIGHT, 9)
        model.swap()
        assertEquals("客队", model.state.value.session.core.leftName)
        assertEquals(9, model.state.value.session.core.leftScore)
        model.nextPeriod()
        assertEquals(2, model.state.value.session.core.period)
        assertEquals(0, model.state.value.session.core.leftScore)
        model.undo()
        assertEquals(1, model.state.value.session.core.period)
        assertEquals(9, model.state.value.session.core.leftScore)
        assertEquals(4, model.state.value.session.journal.size)
    }

    @Test fun timerRunsFromTheMonotonicClockAndPausesWithoutDrift() = timedTest {
        val clock = FakeClock()
        val model = model(clock)
        model.start()
        model.toggleTimer()
        runCurrent()
        clock.advance(4_000)
        advanceTimeBy(ScoreboardPolicy.TICK_MS + 1)
        runCurrent()
        assertEquals(4_000L, model.state.value.displayMs)
        assertTrue(model.state.value.session.running)
        model.toggleTimer()
        runCurrent()
        assertEquals(4_000L, model.state.value.displayMs)
        clock.advance(16_000)
        advanceTimeBy(ScoreboardPolicy.TICK_MS * 4)
        runCurrent()
        assertEquals(4_000L, model.state.value.displayMs)
        assertFalse(model.state.value.session.running)
    }

    @Test fun activeTimerSurvivesRecreationAndCatchesUp() = timedTest {
        val clock = FakeClock()
        val saved = SavedStateHandle()
        val first = model(clock, saved)
        first.start()
        first.toggleTimer()
        runCurrent()
        clock.advance(5_000)
        first.stop()
        store.clear()
        val second = model(clock, saved)
        second.start()
        runCurrent()
        assertEquals(5_000L, second.state.value.displayMs)
        assertTrue(second.state.value.session.running)
        second.toggleTimer()
        assertEquals(5_000L, second.state.value.displayMs)
    }

    @Test fun aRebootedMonotonicClockUsesWallTimeInsteadOfPhantomTimerSeconds() = timedTest {
        val saved = SavedStateHandle(mapOf(
            "sbRunning" to true,
            "sbAccum" to 7_000L,
            "sbStarted" to 500_000L,
            "sbSavedAt" to 2_000_000L,
        ))
        val model = model(FakeClock(monotonic = 600_000L, epoch = 2_005_000L), saved)
        assertEquals(12_000L, model.state.value.displayMs)
        assertTrue(model.state.value.session.running)
    }

    @Test fun journalAndNamesSurviveRecreationAndCorruptionStaysBounded() = timedTest {
        val clock = FakeClock()
        val saved = SavedStateHandle()
        val first = model(clock, saved)
        first.editNames("火箭", "飞鸟")
        first.increment(ScoreSide.LEFT)
        first.increment(ScoreSide.RIGHT)
        first.stop()
        store.clear()
        val second = model(clock, saved)
        assertEquals("火箭", second.state.value.session.core.leftName)
        assertEquals(1, second.state.value.session.core.leftScore)
        assertEquals(3, second.state.value.session.journal.size)
        second.undo()
        assertEquals(0, second.state.value.session.core.rightScore)

        val damaged = SavedStateHandle(mapOf(
            "sbLeftScore" to -9,
            "sbRightScore" to 99_999,
            "sbPeriod" to -2,
            "sbStep" to 99,
            "sbJournal" to arrayListOf("junk"),
        ))
        val third = model(clock, damaged)
        assertEquals(0, third.state.value.session.core.leftScore)
        assertEquals(ScoreboardPolicy.MAX_SCORE, third.state.value.session.core.rightScore)
        assertEquals(1, third.state.value.session.core.period)
        assertEquals(ScoreboardPolicy.MAX_STEP, third.state.value.session.step)
        assertTrue(third.state.value.session.journal.isEmpty())
    }

    @Test fun resetKeepsPreferenceAndNamesWhileClearingMatchState() = timedTest {
        val clock = FakeClock()
        val model = model(clock)
        model.editNames("Alpha", "Beta")
        model.setStep(2)
        model.setKeepScreenOn(false)
        model.increment(ScoreSide.LEFT)
        model.toggleTimer()
        model.reset()
        assertEquals("Alpha", model.state.value.session.core.leftName)
        assertEquals(2, model.state.value.session.step)
        assertEquals(0, model.state.value.session.core.leftScore)
        assertEquals(0L, model.state.value.displayMs)
        assertFalse(model.state.value.keepScreenOn)
        assertTrue(model.state.value.session.journal.isEmpty())
    }
}
