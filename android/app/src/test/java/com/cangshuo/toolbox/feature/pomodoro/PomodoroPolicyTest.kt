package com.cangshuo.toolbox.feature.pomodoro

import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroError
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroPhase
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroPolicy
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroSession
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroSettings
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroPolicyTest {
    private val classic = PomodoroSettings()

    @Test fun settingsAndLabelsAreValidatedAndNormalized() {
        assertEquals(PomodoroError.DURATION, PomodoroPolicy.validateSettings(classic.copy(focusMinutes = 0)))
        assertEquals(PomodoroError.DURATION, PomodoroPolicy.validateSettings(classic.copy(shortBreakMinutes = 61)))
        assertEquals(PomodoroError.ROUNDS, PomodoroPolicy.validateSettings(classic.copy(roundsBeforeLongBreak = 1)))
        assertNull(PomodoroPolicy.validateSettings(classic.copy(focusMinutes = 120, longBreakMinutes = 180)))
        assertNull(PomodoroPolicy.validateLabel("\uD83D\uDE00".repeat(60)))
        assertEquals(PomodoroError.LABEL, PomodoroPolicy.validateLabel("\uD83D\uDE00".repeat(61)))
        assertEquals("Read book", PomodoroPolicy.normalizeLabel("  Read \n book  "))
        assertEquals(2_700, PomodoroPolicy.durationSeconds(classic.copy(shortBreakMinutes = 45), PomodoroPhase.SHORT_BREAK))
    }

    @Test fun focusCompletionMovesToShortBreakWithoutAutoStart() {
        val running = PomodoroPolicy.start(PomodoroPolicy.fresh(classic), classic, 0)
        val before = PomodoroPolicy.tick(running, classic, 1_200_000)
        assertEquals(300, before.state.remainingSeconds)
        assertEquals(0, before.records.size)
        val completed = PomodoroPolicy.tick(running, classic, 1_500_000)
        assertEquals(PomodoroPhase.SHORT_BREAK, completed.state.phase)
        assertFalse(completed.state.running)
        assertEquals(1, completed.state.completedFocusesInCycle)
        assertEquals(1, completed.records.size)
        assertTrue(completed.records.single().completed)
        assertEquals(1_500, completed.records.single().elapsedSeconds)
    }

    @Test fun fourthFocusUsesLongBreakAndLongBreakResetsCycle() {
        val fourth = PomodoroPolicy.fresh(classic, completedFocusesInCycle = 3)
        val running = PomodoroPolicy.start(fourth, classic, 10)
        val completed = PomodoroPolicy.tick(running, classic, 1_500_010)
        assertEquals(PomodoroPhase.LONG_BREAK, completed.state.phase)
        assertEquals(4, completed.state.completedFocusesInCycle)
        assertEquals(4, PomodoroPolicy.roundLabel(completed.state, classic))
        val breakRunning = PomodoroPolicy.start(completed.state, classic, 2_000_000)
        val next = PomodoroPolicy.tick(breakRunning, classic, 2_900_000)
        assertEquals(PomodoroPhase.FOCUS, next.state.phase)
        assertEquals(0, next.state.completedFocusesInCycle)
    }

    @Test fun automaticTransitionsUseDeadlineAndRespectBothSwitches() {
        val settings = classic.copy(autoStartBreaks = true, autoStartFocus = false)
        val running = PomodoroPolicy.start(PomodoroPolicy.fresh(settings), settings, 0)
        val breakState = PomodoroPolicy.tick(running, settings, 1_500_000).state
        assertTrue(breakState.running)
        assertEquals(PomodoroPhase.SHORT_BREAK, breakState.phase)
        assertEquals(1_800_000L, breakState.deadlineEpochMillis)
        val focusState = PomodoroPolicy.tick(breakState, settings, 1_800_000).state
        assertFalse(focusState.running)
        assertEquals(PomodoroPhase.FOCUS, focusState.phase)
    }

    @Test fun skipAndResetRetainOnlyMeaningfulFocusTime() {
        val running = PomodoroPolicy.start(PomodoroPolicy.fresh(classic), classic, 0)
        val earlySkip = PomodoroPolicy.skip(running, classic, 30_000)
        assertTrue(earlySkip.records.isEmpty())
        assertEquals(PomodoroPhase.SHORT_BREAK, earlySkip.state.phase)
        val later = PomodoroPolicy.start(PomodoroPolicy.fresh(classic), classic, 100_000)
        val partial = PomodoroPolicy.skip(later, classic, 220_000)
        assertEquals(1, partial.records.size)
        assertEquals(120, partial.records.single().elapsedSeconds)
        assertFalse(partial.records.single().completed)
        val reset = PomodoroPolicy.reset(later, classic, 160_000)
        assertEquals(1, reset.records.size)
        assertEquals(PomodoroPhase.FOCUS, reset.state.phase)
        assertEquals(1_500, reset.state.remainingSeconds)
    }

    @Test fun pauseUsesCeilingSecondsAndPreservesSessionStart() {
        val running = PomodoroPolicy.start(PomodoroPolicy.fresh(classic), classic, 1_000)
        val paused = PomodoroPolicy.pause(running, classic, 61_001)
        assertFalse(paused.running)
        assertEquals(1_440, paused.remainingSeconds)
        assertEquals(1_000L, paused.sessionStartedAtMillis)
        val resumed = PomodoroPolicy.start(paused, classic, 100_000)
        assertTrue(resumed.running)
        assertEquals(1_540_000L, resumed.deadlineEpochMillis)
    }

    @Test fun settingsChangeResetsIdlePhaseButNotRunningState() {
        val paused = PomodoroPolicy.fresh(classic)
        val changed = PomodoroPolicy.updateSettings(paused, classic.copy(focusMinutes = 50))
        assertEquals(3_000, changed.durationSeconds)
        assertEquals(3_000, changed.remainingSeconds)
        val running = PomodoroPolicy.start(paused, classic, 0)
        val runningChange = PomodoroPolicy.updateSettings(running, classic.copy(focusMinutes = 50))
        assertTrue(runningChange.running)
        assertEquals(1_500, runningChange.durationSeconds)
    }

    @Test fun statsAggregateFocusMinutesAndStreaks() {
        val zone = ZoneId.of("Asia/Shanghai")
        val today = LocalDate.of(2026, 10, 18)
        fun session(day: LocalDate, minutes: Int, completed: Boolean = true, phase: PomodoroPhase = PomodoroPhase.FOCUS): PomodoroSession {
            val end = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            return PomodoroSession(0, "", phase, minutes * 60, minutes * 60, completed, end - minutes * 60_000L, end)
        }
        val sessions = listOf(
            session(today, 25),
            session(today.minusDays(1), 50),
            session(today.minusDays(2), 25),
            session(today.minusDays(4), 25),
            session(today, 10, phase = PomodoroPhase.SHORT_BREAK),
            session(today, 25, completed = false),
        )
        val stats = PomodoroPolicy.stats(sessions, today.toEpochDay(), zone)
        assertEquals(50, stats.todayFocusMinutes)
        assertEquals(1, stats.todayCompletedFocuses)
        assertEquals(150, stats.weekFocusMinutes)
        assertEquals(3, stats.currentStreakDays)
        assertEquals(3, stats.longestStreakDays)
        assertEquals(4, stats.completedFocuses)
    }

    @Test fun catchUpIsBoundedAndClockFormattingIsStable() {
        val settings = classic.copy(focusMinutes = 1, shortBreakMinutes = 1, longBreakMinutes = 1,
            roundsBeforeLongBreak = 2, autoStartBreaks = true, autoStartFocus = true)
        val running = PomodoroPolicy.start(PomodoroPolicy.fresh(settings), settings, 0)
        val advance = PomodoroPolicy.tick(running, settings, 86_400_000)
        assertTrue(advance.records.size <= PomodoroPolicy.MAX_CATCH_UP_TRANSITIONS)
        assertFalse(advance.state.running)
        assertEquals("25:00", PomodoroPolicy.formatClock(1_500))
        assertEquals("1:01:01", PomodoroPolicy.formatClock(3_661))
    }

    @Test fun manualPhaseSwitchNeverInterruptsRunningTimer() {
        val running = PomodoroPolicy.start(PomodoroPolicy.fresh(classic), classic, 0)
        assertEquals(running, PomodoroPolicy.switchPhase(running, classic, PomodoroPhase.LONG_BREAK))
        val switched = PomodoroPolicy.switchPhase(PomodoroPolicy.fresh(classic), classic, PomodoroPhase.LONG_BREAK)
        assertEquals(PomodoroPhase.LONG_BREAK, switched.phase)
        assertEquals(900, switched.remainingSeconds)
    }
}
