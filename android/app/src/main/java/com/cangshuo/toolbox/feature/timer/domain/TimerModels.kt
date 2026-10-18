package com.cangshuo.toolbox.feature.timer.domain

import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.flow.StateFlow

enum class CountdownStatus(val storage: Int) {
    IDLE(0), RUNNING(1), PAUSED(2), FINISHED(3);

    companion object {
        fun fromStorage(value: Int): CountdownStatus = entries.firstOrNull { it.storage == value } ?: IDLE
    }
}

data class CountdownTimerState(
    val status: CountdownStatus = CountdownStatus.IDLE,
    val totalMillis: Long = CountdownPolicy.DEFAULT_DURATION_MILLIS,
    val remainingMillis: Long = CountdownPolicy.DEFAULT_DURATION_MILLIS,
    val deadlineElapsedMillis: Long? = null,
    val deadlineWallMillis: Long? = null,
    val label: String = "",
    val keepScreenOn: Boolean = true,
    val completionNotified: Boolean = false,
) {
    fun remainingAt(nowElapsedMillis: Long): Long {
        val deadline = deadlineElapsedMillis
        return if (status == CountdownStatus.RUNNING && deadline != null) {
            (deadline - nowElapsedMillis).coerceAtLeast(0L)
        } else {
            remainingMillis.coerceAtLeast(0L)
        }
    }

    fun progress(nowElapsedMillis: Long): Float {
        if (totalMillis <= 0L) return 0f
        return (1f - remainingAt(nowElapsedMillis).toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    }

    fun remainingSecondsAt(nowElapsedMillis: Long): Long =
        ceil(remainingAt(nowElapsedMillis) / 1_000.0).toLong()
}

data class TimerDurationInput(
    val hours: String = "0",
    val minutes: String = "5",
    val seconds: String = "0",
    val label: String = "",
)

sealed interface TimerValidation {
    data class Valid(val durationMillis: Long, val label: String) : TimerValidation
    data class Invalid(val error: TimerError) : TimerValidation
}

enum class TimerError { DURATION, LABEL, STORAGE }

interface CountdownRepository {
    val state: StateFlow<CountdownTimerState>
    fun prepare(durationMillis: Long)
    fun start(durationMillis: Long, label: String)
    fun pause()
    fun resume()
    fun reset()
    fun extend(additionalMillis: Long = CountdownPolicy.MINUTE_MILLIS)
    fun finish(): Boolean
    fun markCompletionNotified()
    fun setKeepScreenOn(enabled: Boolean)
    fun ensureService()
    fun nowElapsedMillis(): Long
    fun nowWallMillis(): Long
}

class CountdownUseCases(private val repository: CountdownRepository) {
    fun observe() = repository.state
    fun nowElapsed() = repository.nowElapsedMillis()

    fun validate(input: TimerDurationInput): TimerValidation {
        val hours = input.hours.toIntOrNull()
        val minutes = input.minutes.toIntOrNull()
        val seconds = input.seconds.toIntOrNull()
        if (hours == null || minutes == null || seconds == null || hours !in 0..23 ||
            minutes !in 0..59 || seconds !in 0..59
        ) return TimerValidation.Invalid(TimerError.DURATION)
        val millis = (hours * 3_600L + minutes * 60L + seconds) * 1_000L
        if (millis !in CountdownPolicy.MIN_DURATION_MILLIS..CountdownPolicy.MAX_DURATION_MILLIS) {
            return TimerValidation.Invalid(TimerError.DURATION)
        }
        val label = CountdownPolicy.normalizeLabel(input.label)
        if (label.codePointCount(0, label.length) > CountdownPolicy.MAX_LABEL_CODE_POINTS) {
            return TimerValidation.Invalid(TimerError.LABEL)
        }
        return TimerValidation.Valid(millis, label)
    }

    fun prepare(durationMillis: Long) = repository.prepare(durationMillis)
    fun start(durationMillis: Long, label: String) = repository.start(durationMillis, label)
    fun pause() = repository.pause()
    fun resume() = repository.resume()
    fun reset() = repository.reset()
    fun extend() = repository.extend()
    fun finish() = repository.finish()
    fun setKeepScreenOn(enabled: Boolean) = repository.setKeepScreenOn(enabled)
    fun ensureService() = repository.ensureService()
}

object CountdownPolicy {
    const val DEFAULT_DURATION_MILLIS = 5 * 60_000L
    const val MINUTE_MILLIS = 60_000L
    const val MIN_DURATION_MILLIS = 1_000L
    const val MAX_DURATION_MILLIS = (23L * 3_600L + 59L * 60L + 59L) * 1_000L
    const val MAX_LABEL_CODE_POINTS = 60
    val presetsMinutes = listOf(1, 3, 5, 10, 15, 25, 30, 45, 60)

    fun fresh(durationMillis: Long, state: CountdownTimerState): CountdownTimerState {
        val duration = durationMillis.coerceIn(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS)
        return state.copy(
            status = CountdownStatus.IDLE,
            totalMillis = duration,
            remainingMillis = duration,
            deadlineElapsedMillis = null,
            deadlineWallMillis = null,
            completionNotified = false,
        )
    }

    fun start(
        current: CountdownTimerState,
        durationMillis: Long,
        label: String,
        nowElapsedMillis: Long,
        nowWallMillis: Long,
    ): CountdownTimerState {
        val total = durationMillis.coerceIn(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS)
        return current.copy(
            status = CountdownStatus.RUNNING,
            totalMillis = total,
            remainingMillis = total,
            deadlineElapsedMillis = nowElapsedMillis + total,
            deadlineWallMillis = nowWallMillis + total,
            label = normalizeLabel(label),
            completionNotified = false,
        )
    }

    fun pause(current: CountdownTimerState, nowElapsedMillis: Long): CountdownTimerState {
        if (current.status != CountdownStatus.RUNNING) return current
        if (current.remainingAt(nowElapsedMillis) <= 0L) return finish(current)
        return current.copy(
            status = CountdownStatus.PAUSED,
            remainingMillis = current.remainingAt(nowElapsedMillis),
            deadlineElapsedMillis = null,
            deadlineWallMillis = null,
        )
    }

    fun resume(current: CountdownTimerState, nowElapsedMillis: Long, nowWallMillis: Long): CountdownTimerState {
        if (current.status != CountdownStatus.PAUSED) return current
        val remaining = current.remainingMillis.coerceAtLeast(1L)
        return current.copy(
            status = CountdownStatus.RUNNING,
            remainingMillis = remaining,
            deadlineElapsedMillis = nowElapsedMillis + remaining,
            deadlineWallMillis = nowWallMillis + remaining,
        )
    }

    fun finish(current: CountdownTimerState): CountdownTimerState = current.copy(
        status = CountdownStatus.FINISHED,
        remainingMillis = 0L,
        deadlineElapsedMillis = null,
        deadlineWallMillis = null,
        completionNotified = false,
    )

    fun restore(
        status: CountdownStatus,
        totalMillis: Long,
        storedRemainingMillis: Long,
        storedDeadlineElapsedMillis: Long?,
        storedDeadlineWallMillis: Long?,
        storedBootCount: Int,
        currentBootCount: Int,
        nowElapsedMillis: Long,
        nowWallMillis: Long,
        label: String,
        keepScreenOn: Boolean,
        completionNotified: Boolean,
    ): CountdownTimerState {
        val total = totalMillis.coerceIn(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS)
        val storedRemaining = storedRemainingMillis.coerceIn(0L, total)
        if (status != CountdownStatus.RUNNING) {
            return CountdownTimerState(
                status, total, storedRemaining, null, null, label, keepScreenOn, completionNotified,
            )
        }
        val remaining = if (storedBootCount == currentBootCount && storedDeadlineElapsedMillis != null) {
            (storedDeadlineElapsedMillis - nowElapsedMillis).coerceAtLeast(0L)
        } else {
            storedDeadlineWallMillis?.let { (it - nowWallMillis).coerceAtLeast(0L) } ?: storedRemaining
        }
        return if (remaining <= 0L) {
            CountdownTimerState(
                CountdownStatus.FINISHED, total, 0L, null, null, label, keepScreenOn, completionNotified,
            )
        } else {
            CountdownTimerState(
                status = CountdownStatus.RUNNING,
                totalMillis = total,
                remainingMillis = remaining,
                deadlineElapsedMillis = nowElapsedMillis + remaining,
                deadlineWallMillis = nowWallMillis + remaining,
                label = label,
                keepScreenOn = keepScreenOn,
                completionNotified = completionNotified,
            )
        }
    }

    fun extend(current: CountdownTimerState, additionalMillis: Long, nowElapsedMillis: Long, nowWallMillis: Long): CountdownTimerState {
        if (current.status != CountdownStatus.RUNNING && current.status != CountdownStatus.PAUSED) return current
        val addition = additionalMillis.coerceAtLeast(1_000L)
        val total = current.totalMillis + addition
        return if (current.status == CountdownStatus.RUNNING) {
            current.copy(
                totalMillis = total,
                remainingMillis = current.remainingAt(nowElapsedMillis) + addition,
                deadlineElapsedMillis = (current.deadlineElapsedMillis ?: nowElapsedMillis) + addition,
                deadlineWallMillis = (current.deadlineWallMillis ?: nowWallMillis) + addition,
            )
        } else {
            current.copy(totalMillis = total, remainingMillis = current.remainingMillis + addition)
        }
    }

    fun normalizeLabel(label: String): String {
        return label.trim().replace(Regex("\\s+"), " ")
    }

    fun format(millis: Long): String {
        val totalSeconds = ceil(millis.coerceAtLeast(0L) / 1_000.0).toLong()
        val hours = totalSeconds / 3_600
        val minutes = totalSeconds / 60 % 60
        val seconds = totalSeconds % 60
        return if (hours > 0) String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun requiresForegroundService(state: CountdownTimerState): Boolean {
        return state.status == CountdownStatus.RUNNING || state.status == CountdownStatus.PAUSED
    }

    fun splitInput(millis: Long): Triple<String, String, String> {
        val totalSeconds = ceil(millis.coerceAtLeast(0L) / 1_000.0).toLong()
        return Triple(
            (totalSeconds / 3_600).toString(),
            (totalSeconds / 60 % 60).toString(),
            (totalSeconds % 60).toString(),
        )
    }
}
