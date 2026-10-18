package com.cangshuo.toolbox.feature.pomodoro.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroPhase
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroPolicy
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroSettings
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroTimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small synchronous snapshot so the foreground service can recover immediately after process restart. */
class PomodoroTimerStore(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val mutableSettings = MutableStateFlow(readSettings())
    private val mutableTimer = MutableStateFlow(readTimer(mutableSettings.value))

    val settings: StateFlow<PomodoroSettings> = mutableSettings.asStateFlow()
    val timer: StateFlow<PomodoroTimerState> = mutableTimer.asStateFlow()

    fun saveTimer(state: PomodoroTimerState) {
        val normalized = PomodoroPolicy.normalize(state, mutableSettings.value)
        preferences.edit {
            putInt(KEY_PHASE, normalized.phase.storage)
            putBoolean(KEY_RUNNING, normalized.running)
            putInt(KEY_DURATION, normalized.durationSeconds)
            putInt(KEY_REMAINING, normalized.remainingSeconds)
            putLong(KEY_DEADLINE, normalized.deadlineEpochMillis ?: -1L)
            putLong(KEY_SESSION_STARTED, normalized.sessionStartedAtMillis ?: -1L)
            putInt(KEY_CYCLE, normalized.completedFocusesInCycle)
            putString(KEY_LABEL, normalized.label)
        }
        mutableTimer.value = normalized
    }

    fun saveSettings(settings: PomodoroSettings) {
        val normalized = PomodoroPolicy.normalizeSettings(settings)
        preferences.edit {
            putInt(KEY_FOCUS_MINUTES, normalized.focusMinutes)
            putInt(KEY_SHORT_BREAK_MINUTES, normalized.shortBreakMinutes)
            putInt(KEY_LONG_BREAK_MINUTES, normalized.longBreakMinutes)
            putInt(KEY_ROUNDS, normalized.roundsBeforeLongBreak)
            putBoolean(KEY_AUTO_START_BREAKS, normalized.autoStartBreaks)
            putBoolean(KEY_AUTO_START_FOCUS, normalized.autoStartFocus)
        }
        mutableSettings.value = normalized
        saveTimer(PomodoroPolicy.updateSettings(mutableTimer.value, normalized))
    }

    private fun readSettings(): PomodoroSettings = PomodoroPolicy.normalizeSettings(
        PomodoroSettings(
            focusMinutes = preferences.getInt(KEY_FOCUS_MINUTES, 25),
            shortBreakMinutes = preferences.getInt(KEY_SHORT_BREAK_MINUTES, 5),
            longBreakMinutes = preferences.getInt(KEY_LONG_BREAK_MINUTES, 15),
            roundsBeforeLongBreak = preferences.getInt(KEY_ROUNDS, 4),
            autoStartBreaks = preferences.getBoolean(KEY_AUTO_START_BREAKS, false),
            autoStartFocus = preferences.getBoolean(KEY_AUTO_START_FOCUS, false),
        ),
    )

    private fun readTimer(settings: PomodoroSettings): PomodoroTimerState {
        val default = PomodoroPolicy.fresh(settings)
        val phase = PomodoroPhase.fromStorage(preferences.getInt(KEY_PHASE, default.phase.storage))
        val duration = preferences.getInt(KEY_DURATION, default.durationSeconds)
        val stored = PomodoroTimerState(
            phase = phase,
            running = preferences.getBoolean(KEY_RUNNING, false),
            durationSeconds = duration,
            remainingSeconds = preferences.getInt(KEY_REMAINING, duration),
            deadlineEpochMillis = preferences.getLong(KEY_DEADLINE, -1L).takeIf { it > 0 },
            sessionStartedAtMillis = preferences.getLong(KEY_SESSION_STARTED, -1L).takeIf { it > 0 },
            completedFocusesInCycle = preferences.getInt(KEY_CYCLE, 0),
            label = preferences.getString(KEY_LABEL, "").orEmpty(),
        )
        return PomodoroPolicy.normalize(stored, settings)
    }

    private companion object {
        const val PREFERENCES = "pomodoro_timer_v1"
        const val KEY_PHASE = "phase"
        const val KEY_RUNNING = "running"
        const val KEY_DURATION = "duration_seconds"
        const val KEY_REMAINING = "remaining_seconds"
        const val KEY_DEADLINE = "deadline_epoch_millis"
        const val KEY_SESSION_STARTED = "session_started_millis"
        const val KEY_CYCLE = "completed_focuses_in_cycle"
        const val KEY_LABEL = "label"
        const val KEY_FOCUS_MINUTES = "focus_minutes"
        const val KEY_SHORT_BREAK_MINUTES = "short_break_minutes"
        const val KEY_LONG_BREAK_MINUTES = "long_break_minutes"
        const val KEY_ROUNDS = "rounds_before_long_break"
        const val KEY_AUTO_START_BREAKS = "auto_start_breaks"
        const val KEY_AUTO_START_FOCUS = "auto_start_focus"
    }
}
