package com.cangshuo.toolbox.feature.pomodoro.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class PomodoroPhase(val storage: Int) {
    FOCUS(0), SHORT_BREAK(1), LONG_BREAK(2);

    companion object {
        fun fromStorage(value: Int): PomodoroPhase = entries.firstOrNull { it.storage == value } ?: FOCUS
    }
}

enum class PomodoroError { DURATION, ROUNDS, LABEL, STORAGE }

data class PomodoroSettings(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val roundsBeforeLongBreak: Int = 4,
    val autoStartBreaks: Boolean = false,
    val autoStartFocus: Boolean = false,
)

data class PomodoroTimerState(
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val running: Boolean = false,
    val durationSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val deadlineEpochMillis: Long? = null,
    val sessionStartedAtMillis: Long? = null,
    val completedFocusesInCycle: Int = 0,
    val label: String = "",
) {
    fun remainingAt(nowMillis: Long): Int {
        val deadline = deadlineEpochMillis
        if (!running || deadline == null) return remainingSeconds.coerceAtLeast(0)
        val millis = (deadline - nowMillis).coerceAtLeast(0)
        return ((millis + 999L) / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun progress(nowMillis: Long): Float {
        if (durationSeconds <= 0) return 0f
        return (1f - remainingAt(nowMillis).toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
    }
}

data class PomodoroSession(
    val id: Long,
    val label: String,
    val phase: PomodoroPhase,
    val plannedSeconds: Int,
    val elapsedSeconds: Int,
    val completed: Boolean,
    val startedAt: Long,
    val endedAt: Long,
)

data class PomodoroStats(
    val todayFocusMinutes: Int = 0,
    val todayCompletedFocuses: Int = 0,
    val weekFocusMinutes: Int = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val completedFocuses: Int = 0,
)

data class PomodoroAdvance(
    val state: PomodoroTimerState,
    val records: List<PomodoroSession> = emptyList(),
)

interface PomodoroRepository {
    fun observeTimer(): StateFlow<PomodoroTimerState>
    fun observeSettings(): StateFlow<PomodoroSettings>
    fun observeHistory(): Flow<List<PomodoroSession>>

    suspend fun tick(): PomodoroAdvance
    suspend fun start(): PomodoroTimerState
    suspend fun pause(): PomodoroTimerState
    suspend fun skip(): PomodoroAdvance
    suspend fun reset(): PomodoroAdvance
    suspend fun switchPhase(phase: PomodoroPhase): PomodoroTimerState
    suspend fun setLabel(label: String): PomodoroTimerState
    suspend fun updateSettings(settings: PomodoroSettings): PomodoroSettings
    suspend fun clearHistory(): Int

    fun nowMillis(): Long
    fun todayEpochDay(): Long
}

class PomodoroUseCases(private val repository: PomodoroRepository) {
    fun observeTimer() = repository.observeTimer()
    fun observeSettings() = repository.observeSettings()
    fun observeHistory() = repository.observeHistory()
    suspend fun tick() = repository.tick()
    suspend fun start() = repository.start()
    suspend fun pause() = repository.pause()
    suspend fun skip() = repository.skip()
    suspend fun reset() = repository.reset()
    suspend fun switchPhase(phase: PomodoroPhase) = repository.switchPhase(phase)
    suspend fun setLabel(label: String) = repository.setLabel(label)

    suspend fun updateSettings(settings: PomodoroSettings): PomodoroSettings {
        val error = PomodoroPolicy.validateSettings(settings)
        if (error != null) throw IllegalArgumentException("Invalid Pomodoro settings")
        return repository.updateSettings(PomodoroPolicy.normalizeSettings(settings))
    }

    suspend fun clearHistory() = repository.clearHistory()
    fun now() = repository.nowMillis()
    fun todayEpochDay() = repository.todayEpochDay()
    fun stats(sessions: List<PomodoroSession>, zone: ZoneId = ZoneId.systemDefault()) =
        PomodoroPolicy.stats(sessions, repository.todayEpochDay(), zone)
}

object PomodoroPolicy {
    const val MAX_FOCUS_MINUTES = 120
    const val MAX_SHORT_BREAK_MINUTES = 60
    const val MAX_LONG_BREAK_MINUTES = 180
    const val MIN_ROUNDS = 2
    const val MAX_ROUNDS = 8
    const val MAX_LABEL_CODE_POINTS = 60
    const val MAX_HISTORY = 2_000
    const val MAX_CATCH_UP_TRANSITIONS = 12
    const val TICK_MILLIS = 500L

    fun validateSettings(settings: PomodoroSettings): PomodoroError? = when {
        settings.focusMinutes !in 1..MAX_FOCUS_MINUTES -> PomodoroError.DURATION
        settings.shortBreakMinutes !in 1..MAX_SHORT_BREAK_MINUTES -> PomodoroError.DURATION
        settings.longBreakMinutes !in 1..MAX_LONG_BREAK_MINUTES -> PomodoroError.DURATION
        settings.roundsBeforeLongBreak !in MIN_ROUNDS..MAX_ROUNDS -> PomodoroError.ROUNDS
        else -> null
    }

    fun validateLabel(label: String): PomodoroError? =
        if (label.trim().codePointCount(0, label.trim().length) > MAX_LABEL_CODE_POINTS) {
            PomodoroError.LABEL
        } else null

    fun normalizeSettings(settings: PomodoroSettings) = settings.copy(
        focusMinutes = settings.focusMinutes.coerceIn(1, MAX_FOCUS_MINUTES),
        shortBreakMinutes = settings.shortBreakMinutes.coerceIn(1, MAX_SHORT_BREAK_MINUTES),
        longBreakMinutes = settings.longBreakMinutes.coerceIn(1, MAX_LONG_BREAK_MINUTES),
        roundsBeforeLongBreak = settings.roundsBeforeLongBreak.coerceIn(MIN_ROUNDS, MAX_ROUNDS),
    )

    fun normalizeLabel(label: String) = label.trim().replace(Regex("\\s+"), " ")

    fun durationSeconds(settings: PomodoroSettings, phase: PomodoroPhase): Int = when (phase) {
        PomodoroPhase.FOCUS -> settings.focusMinutes * 60
        PomodoroPhase.SHORT_BREAK -> settings.shortBreakMinutes * 60
        PomodoroPhase.LONG_BREAK -> settings.longBreakMinutes * 60
    }

    fun fresh(settings: PomodoroSettings, phase: PomodoroPhase = PomodoroPhase.FOCUS,
        completedFocusesInCycle: Int = 0, label: String = ""): PomodoroTimerState {
        val safe = normalizeSettings(settings)
        val seconds = durationSeconds(safe, phase)
        val cycle = completedFocusesInCycle.coerceIn(0, safe.roundsBeforeLongBreak)
        return PomodoroTimerState(
            phase = phase,
            running = false,
            durationSeconds = seconds,
            remainingSeconds = seconds,
            completedFocusesInCycle = cycle,
            label = normalizeLabel(label),
        )
    }

    fun normalize(state: PomodoroTimerState, settings: PomodoroSettings): PomodoroTimerState {
        val safe = normalizeSettings(settings)
        val expected = durationSeconds(safe, state.phase)
        val effectiveDuration = if (state.running && state.durationSeconds > 0) state.durationSeconds else expected
        val cycle = state.completedFocusesInCycle.coerceIn(0, safe.roundsBeforeLongBreak)
        val remaining = if (state.durationSeconds == effectiveDuration) {
            state.remainingSeconds.coerceIn(0, effectiveDuration)
        } else if (!state.running) {
            expected
        } else {
            state.remainingSeconds.coerceIn(0, effectiveDuration)
        }
        return state.copy(
            durationSeconds = effectiveDuration,
            remainingSeconds = remaining,
            completedFocusesInCycle = cycle,
            label = normalizeLabel(state.label).takeCodePoints(MAX_LABEL_CODE_POINTS),
            deadlineEpochMillis = state.deadlineEpochMillis.takeIf { state.running },
            sessionStartedAtMillis = state.sessionStartedAtMillis.takeIf {
                state.running || remaining < effectiveDuration
            },
        )
    }

    fun start(state: PomodoroTimerState, settings: PomodoroSettings, now: Long): PomodoroTimerState {
        val normalized = normalize(state, settings)
        if (normalized.running) return normalized
        val remaining = if (normalized.remainingSeconds <= 0) normalized.durationSeconds else normalized.remainingSeconds
        return normalized.copy(
            running = true,
            remainingSeconds = remaining,
            deadlineEpochMillis = now + remaining * 1_000L,
            sessionStartedAtMillis = normalized.sessionStartedAtMillis ?: now,
        )
    }

    fun pause(state: PomodoroTimerState, settings: PomodoroSettings, now: Long): PomodoroTimerState {
        val normalized = normalize(state, settings)
        if (!normalized.running) return normalized
        return normalized.copy(
            running = false,
            remainingSeconds = normalized.remainingAt(now),
            deadlineEpochMillis = null,
        )
    }

    fun switchPhase(
        state: PomodoroTimerState,
        settings: PomodoroSettings,
        phase: PomodoroPhase,
    ): PomodoroTimerState {
        if (state.running) return state
        val safe = normalizeSettings(settings)
        val cycle = when (phase) {
            PomodoroPhase.FOCUS -> state.completedFocusesInCycle.coerceAtMost(safe.roundsBeforeLongBreak - 1)
            PomodoroPhase.SHORT_BREAK -> state.completedFocusesInCycle
            PomodoroPhase.LONG_BREAK -> safe.roundsBeforeLongBreak
        }
        return fresh(safe, phase, cycle, state.label)
    }

    fun updateSettings(
        state: PomodoroTimerState,
        settings: PomodoroSettings,
    ): PomodoroTimerState {
        val safe = normalizeSettings(settings)
        if (state.running) return normalize(state, safe)
        return fresh(safe, state.phase, state.completedFocusesInCycle, state.label)
    }

    fun tick(state: PomodoroTimerState, settings: PomodoroSettings, now: Long): PomodoroAdvance {
        val safe = normalizeSettings(settings)
        var current = normalize(state, safe)
        val records = ArrayList<PomodoroSession>()
        var transitions = 0
        while (current.running && current.deadlineEpochMillis != null &&
            current.deadlineEpochMillis <= now && transitions < MAX_CATCH_UP_TRANSITIONS
        ) {
            val due = current.deadlineEpochMillis!!
            records += record(current, due, completed = true)
            val autoStart = if (current.phase == PomodoroPhase.FOCUS) safe.autoStartBreaks else safe.autoStartFocus
            current = nextPhase(current, safe, due, completed = true, autoStart = autoStart)
            transitions++
        }
        if (current.running && current.deadlineEpochMillis != null && current.deadlineEpochMillis > now) {
            current = current.copy(remainingSeconds = current.remainingAt(now))
        } else if (current.running && transitions >= MAX_CATCH_UP_TRANSITIONS) {
            // Bound very old persisted sessions instead of creating an unbounded history burst.
            current = current.copy(running = false, deadlineEpochMillis = null, remainingSeconds = current.durationSeconds)
        }
        return PomodoroAdvance(current, records)
    }

    fun skip(state: PomodoroTimerState, settings: PomodoroSettings, now: Long): PomodoroAdvance {
        val safe = normalizeSettings(settings)
        val current = normalize(state, safe)
        val remaining = current.remainingAt(now)
        val elapsed = (current.durationSeconds - remaining).coerceIn(0, current.durationSeconds)
        val records = if (isMeaningful(current, elapsed)) listOf(record(current, now, completed = false)) else emptyList()
        val autoStart = if (current.phase == PomodoroPhase.FOCUS) safe.autoStartBreaks else safe.autoStartFocus
        return PomodoroAdvance(nextPhase(current, safe, now, completed = false, autoStart = autoStart), records)
    }

    fun reset(state: PomodoroTimerState, settings: PomodoroSettings, now: Long): PomodoroAdvance {
        val safe = normalizeSettings(settings)
        val current = normalize(state, safe)
        val remaining = current.remainingAt(now)
        val elapsed = (current.durationSeconds - remaining).coerceIn(0, current.durationSeconds)
        val records = if (isMeaningful(current, elapsed)) listOf(record(current, now, completed = false)) else emptyList()
        return PomodoroAdvance(fresh(safe, current.phase, current.completedFocusesInCycle, current.label), records)
    }

    fun stats(sessions: List<PomodoroSession>, todayEpochDay: Long, zone: ZoneId): PomodoroStats {
        val focus = sessions.filter { it.phase == PomodoroPhase.FOCUS && it.elapsedSeconds > 0 }
        val today = LocalDate.ofEpochDay(todayEpochDay)
        val startWeek = today.minusDays(6)
        val todayRows = focus.filter { dateOf(it.endedAt, zone) == today }
        val completedDates = focus.filter { it.completed }.map { dateOf(it.endedAt, zone) }
            .distinct().sorted()
        val currentDate = when {
            completedDates.contains(today) -> today
            completedDates.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> null
        }
        var currentStreak = 0
        var cursor = currentDate
        while (cursor != null && completedDates.contains(cursor)) {
            currentStreak++
            cursor = cursor.minusDays(1)
        }
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (date in completedDates) {
            run = if (previous != null && date == previous.plusDays(1)) run + 1 else 1
            longest = maxOf(longest, run)
            previous = date
        }
        return PomodoroStats(
            todayFocusMinutes = todayRows.sumOf { it.elapsedSeconds } / 60,
            todayCompletedFocuses = todayRows.count { it.completed },
            weekFocusMinutes = focus.filter { dateOf(it.endedAt, zone) in startWeek..today }
                .sumOf { it.elapsedSeconds } / 60,
            currentStreakDays = currentStreak,
            longestStreakDays = longest,
            completedFocuses = focus.count { it.completed },
        )
    }

    fun formatClock(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val hours = safe / 3_600
        val minutes = safe / 60 % 60
        val remainder = safe % 60
        return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainder)
        else String.format(Locale.US, "%02d:%02d", minutes, remainder)
    }

    fun roundLabel(state: PomodoroTimerState, settings: PomodoroSettings): Int =
        (state.completedFocusesInCycle + 1).coerceIn(1, settings.roundsBeforeLongBreak)

    private fun nextPhase(
        state: PomodoroTimerState,
        settings: PomodoroSettings,
        atMillis: Long,
        completed: Boolean,
        autoStart: Boolean,
    ): PomodoroTimerState {
        var cycle = state.completedFocusesInCycle
        val phase = when (state.phase) {
            PomodoroPhase.FOCUS -> {
                if (completed) {
                    cycle = (cycle + 1).coerceAtMost(settings.roundsBeforeLongBreak)
                    if (cycle >= settings.roundsBeforeLongBreak) PomodoroPhase.LONG_BREAK
                    else PomodoroPhase.SHORT_BREAK
                } else {
                    PomodoroPhase.SHORT_BREAK
                }
            }
            PomodoroPhase.SHORT_BREAK -> PomodoroPhase.FOCUS
            PomodoroPhase.LONG_BREAK -> {
                cycle = 0
                PomodoroPhase.FOCUS
            }
        }
        val next = fresh(settings, phase, cycle, state.label)
        return if (autoStart) start(next, settings, atMillis) else next
    }

    private fun record(state: PomodoroTimerState, endedAt: Long, completed: Boolean): PomodoroSession {
        val elapsed = if (completed) state.durationSeconds else
            (state.durationSeconds - state.remainingAt(endedAt)).coerceIn(0, state.durationSeconds)
        val started = state.sessionStartedAtMillis ?: (endedAt - elapsed * 1_000L)
        return PomodoroSession(
            id = 0,
            label = state.label,
            phase = state.phase,
            plannedSeconds = state.durationSeconds,
            elapsedSeconds = elapsed,
            completed = completed,
            startedAt = started,
            endedAt = endedAt,
        )
    }

    private fun isMeaningful(state: PomodoroTimerState, elapsedSeconds: Int): Boolean =
        state.phase == PomodoroPhase.FOCUS && elapsedSeconds >= 60

    private fun dateOf(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    private fun String.takeCodePoints(limit: Int): String {
        if (codePointCount(0, length) <= limit) return this
        return substring(0, offsetByCodePoints(0, limit))
    }
}
