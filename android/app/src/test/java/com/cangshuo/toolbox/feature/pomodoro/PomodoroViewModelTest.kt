package com.cangshuo.toolbox.feature.pomodoro

import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroAdvance
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroError
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroPhase
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroPolicy
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroRepository
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroSession
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroSettings
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroTimerState
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroUseCases
import com.cangshuo.toolbox.feature.pomodoro.ui.PomodoroMessage
import com.cangshuo.toolbox.feature.pomodoro.ui.PomodoroViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class PomodoroViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 18).toEpochDay()

    private class FakeRepository(initialHistory: List<PomodoroSession> = emptyList()) : PomodoroRepository {
        val timerFlow = MutableStateFlow(PomodoroPolicy.fresh(PomodoroSettings()))
        val settingsFlow = MutableStateFlow(PomodoroSettings())
        val historyFlow = MutableStateFlow(initialHistory)
        var now = 1_000L
        var failWrites = false

        override fun observeTimer(): StateFlow<PomodoroTimerState> = timerFlow
        override fun observeSettings(): StateFlow<PomodoroSettings> = settingsFlow
        override fun observeHistory(): Flow<List<PomodoroSession>> = historyFlow
        override suspend fun tick(): PomodoroAdvance =
            apply(PomodoroPolicy.tick(timerFlow.value, settingsFlow.value, now))

        override suspend fun start(): PomodoroTimerState {
            if (failWrites) throw IllegalStateException("write")
            return PomodoroPolicy.start(timerFlow.value, settingsFlow.value, now).also { timerFlow.value = it }
        }

        override suspend fun pause(): PomodoroTimerState =
            PomodoroPolicy.pause(timerFlow.value, settingsFlow.value, now).also { timerFlow.value = it }

        override suspend fun skip(): PomodoroAdvance =
            apply(PomodoroPolicy.skip(timerFlow.value, settingsFlow.value, now))

        override suspend fun reset(): PomodoroAdvance =
            apply(PomodoroPolicy.reset(timerFlow.value, settingsFlow.value, now))

        override suspend fun switchPhase(phase: PomodoroPhase): PomodoroTimerState =
            PomodoroPolicy.switchPhase(timerFlow.value, settingsFlow.value, phase).also { timerFlow.value = it }

        override suspend fun setLabel(label: String): PomodoroTimerState {
            if (failWrites) throw IllegalStateException("write")
            return timerFlow.value.copy(label = PomodoroPolicy.normalizeLabel(label)).also { timerFlow.value = it }
        }

        override suspend fun updateSettings(settings: PomodoroSettings): PomodoroSettings {
            val normalized = PomodoroPolicy.normalizeSettings(settings)
            settingsFlow.value = normalized
            timerFlow.value = PomodoroPolicy.updateSettings(timerFlow.value, normalized)
            return normalized
        }

        override suspend fun clearHistory(): Int {
            if (failWrites) throw IllegalStateException("write")
            val count = historyFlow.value.size
            historyFlow.value = emptyList()
            return count
        }

        override fun nowMillis(): Long = now
        override fun todayEpochDay(): Long = LocalDate.of(2026, 10, 18).toEpochDay()

        private fun apply(advance: PomodoroAdvance): PomodoroAdvance {
            val stored = advance.records.mapIndexed { index, session ->
                session.copy(id = historyFlow.value.size.toLong() + index + 1)
            }
            if (stored.isNotEmpty()) historyFlow.value = stored + historyFlow.value
            timerFlow.value = advance.state
            return advance.copy(records = stored)
        }
    }

    private fun session(minutes: Int): PomodoroSession {
        val ended = LocalDate.of(2026, 10, 18).atTime(12, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return PomodoroSession(1, "Read", PomodoroPhase.FOCUS, minutes * 60, minutes * 60, true,
            ended - minutes * 60_000L, ended)
    }

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun initialHistoryAndStatisticsArePublished() = runTest {
        val model = PomodoroViewModel(PomodoroUseCases(FakeRepository(listOf(session(25)))))
        assertTrue(model.state.value.loading)
        runCurrent()
        assertFalse(model.state.value.loading)
        assertEquals(today, model.state.value.todayEpochDay)
        assertEquals(1, model.state.value.history.size)
        assertEquals(1, model.state.value.stats.todayCompletedFocuses)
    }

    @Test fun toggleSkipAndResetFlowThroughTheUseCases() = runTest {
        val repository = FakeRepository()
        val model = PomodoroViewModel(PomodoroUseCases(repository))
        runCurrent()
        model.toggleTimer()
        advanceUntilIdle()
        assertTrue(model.state.value.timer.running)
        model.toggleTimer()
        advanceUntilIdle()
        assertFalse(model.state.value.timer.running)
        model.skip()
        advanceUntilIdle()
        assertEquals(PomodoroPhase.SHORT_BREAK, model.state.value.timer.phase)
        assertEquals(PomodoroMessage.SKIPPED, model.state.value.message)
        model.reset()
        advanceUntilIdle()
        assertEquals(PomodoroMessage.RESET, model.state.value.message)
    }

    @Test fun settingsAreValidatedAndSaved() = runTest {
        val repository = FakeRepository()
        val model = PomodoroViewModel(PomodoroUseCases(repository))
        runCurrent()
        model.saveSettings(PomodoroSettings(roundsBeforeLongBreak = 1))
        advanceUntilIdle()
        assertEquals(PomodoroError.DURATION, model.state.value.error)
        model.saveSettings(PomodoroSettings(50, 10, 20, 3, autoStartBreaks = true))
        advanceUntilIdle()
        assertEquals(50, model.state.value.settings.focusMinutes)
        assertEquals(PomodoroMessage.SAVED, model.state.value.message)
    }

    @Test fun labelLimitsAndHistoryClearAreEnforced() = runTest {
        val repository = FakeRepository(listOf(session(25)))
        val model = PomodoroViewModel(PomodoroUseCases(repository))
        runCurrent()
        model.setLabel("\uD83D\uDE00".repeat(61))
        assertEquals(PomodoroError.LABEL, model.state.value.error)
        model.clearError()
        model.setLabel("  Read \n deeply  ")
        advanceUntilIdle()
        assertEquals("Read deeply", model.state.value.timer.label)
        model.clearHistory()
        advanceUntilIdle()
        assertTrue(model.state.value.history.isEmpty())
        assertEquals(PomodoroMessage.HISTORY_CLEARED, model.state.value.message)
    }
}
