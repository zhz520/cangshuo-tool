package com.cangshuo.toolbox.feature.timer

import com.cangshuo.toolbox.feature.timer.domain.CountdownPolicy
import com.cangshuo.toolbox.feature.timer.domain.CountdownStatus
import com.cangshuo.toolbox.feature.timer.domain.CountdownTimerState
import com.cangshuo.toolbox.feature.timer.domain.TimerDurationInput
import com.cangshuo.toolbox.feature.timer.domain.TimerError
import com.cangshuo.toolbox.feature.timer.domain.TimerValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerPolicyTest {
    private fun startSeconds(seconds: Int): CountdownTimerState = CountdownPolicy.start(
        CountdownTimerState(),
        seconds * 1_000L,
        "Focus",
        nowElapsedMillis = 10_000L,
        nowWallMillis = 1_000_000L,
    )

    @Test fun durationAndLabelBudgetsAreStrict() {
        fun validate(hours: String, minutes: String, seconds: String, label: String = "") =
            CountdownUseCasesForTest.validate(hours, minutes, seconds, label)
        assertTrue(validate("0", "0", "1") is TimerValidation.Valid)
        assertTrue(validate("23", "59", "59") is TimerValidation.Valid)
        assertEquals(TimerError.DURATION, (validate("0", "0", "0") as TimerValidation.Invalid).error)
        assertEquals(TimerError.DURATION, (validate("24", "0", "0") as TimerValidation.Invalid).error)
        assertEquals(TimerError.DURATION, (validate("x", "5", "0") as TimerValidation.Invalid).error)
        assertTrue(validate("0", "5", "0", "\uD83D\uDE00".repeat(60)) is TimerValidation.Valid)
        assertEquals(
            TimerError.LABEL,
            (validate("0", "5", "0", "\uD83D\uDE00".repeat(61)) as TimerValidation.Invalid).error,
        )
    }

    @Test fun startPauseResumeAndAddTimeUseMonotonicDeadlines() {
        val running = startSeconds(60)
        assertEquals(CountdownStatus.RUNNING, running.status)
        assertEquals(60_000L, running.remainingAt(10_000L))
        assertEquals(30_000L, running.remainingAt(40_000L))
        assertEquals(0.5f, running.progress(40_000L))

        val paused = CountdownPolicy.pause(running, 40_000L)
        assertEquals(CountdownStatus.PAUSED, paused.status)
        assertEquals(30_000L, paused.remainingMillis)
        val resumed = CountdownPolicy.resume(paused, 50_000L, 1_100_000L)
        assertEquals(80_000L, resumed.deadlineElapsedMillis)

        val extended = CountdownPolicy.extend(resumed, 60_000L, 55_000L, 1_105_000L)
        assertEquals(120_000L, extended.totalMillis)
        assertEquals(85_000L, extended.remainingAt(55_000L))
        assertEquals(140_000L, extended.deadlineElapsedMillis)
        assertEquals(1_190_000L, extended.deadlineWallMillis)

        val finished = CountdownPolicy.finish(extended)
        assertEquals(CountdownStatus.FINISHED, finished.status)
        assertEquals(0L, finished.remainingAt(999_999L))
    }

    @Test fun pauseAtDeadlineBecomesFinishedAndFreshRestoresConfiguredDuration() {
        val pausedAtZero = CountdownPolicy.pause(startSeconds(1), 11_000L)
        assertEquals(CountdownStatus.FINISHED, pausedAtZero.status)
        val fresh = CountdownPolicy.fresh(90_000L, pausedAtZero.copy(label = "Exercise"))
        assertEquals(CountdownStatus.IDLE, fresh.status)
        assertEquals(90_000L, fresh.remainingMillis)
        assertEquals("Exercise", fresh.label)
        assertEquals("01:30", CountdownPolicy.format(90_000L))
        assertEquals("01:00:00", CountdownPolicy.format(3_600_000L))
    }

    @Test fun onlyRunningOrPausedTimersRequireForegroundService() {
        assertFalse(CountdownPolicy.requiresForegroundService(CountdownTimerState()))
        assertTrue(CountdownPolicy.requiresForegroundService(startSeconds(30)))
        assertTrue(
            CountdownPolicy.requiresForegroundService(
                CountdownPolicy.pause(startSeconds(30), 20_000L),
            ),
        )
        assertFalse(
            CountdownPolicy.requiresForegroundService(
                CountdownPolicy.finish(startSeconds(30)),
            ),
        )
    }

    @Test fun expiredTimerCannotResumeOrExtend() {
        val expiredPaused = CountdownTimerState(
            status = CountdownStatus.PAUSED,
            totalMillis = 60_000L,
            remainingMillis = 0L,
        )
        assertEquals(
            CountdownStatus.FINISHED,
            CountdownPolicy.resume(expiredPaused, 20_000L, 2_000_000L).status,
        )
        assertEquals(
            CountdownStatus.FINISHED,
            CountdownPolicy.extend(startSeconds(1), 60_000L, 11_000L, 1_001_000L).status,
        )
        assertEquals(
            CountdownStatus.FINISHED,
            CountdownPolicy.extend(expiredPaused, 60_000L, 20_000L, 2_000_000L).status,
        )
    }

    @Test fun extendCapsAtMaximumAndKeepsPersistentTotalInBounds() {
        val nearMaximum = CountdownPolicy.start(
            CountdownTimerState(),
            CountdownPolicy.MAX_DURATION_MILLIS - 30_000L,
            "Long",
            nowElapsedMillis = 10_000L,
            nowWallMillis = 1_000_000L,
        )
        val extended = CountdownPolicy.extend(nearMaximum, 60_000L, 10_000L, 1_000_000L)
        assertEquals(CountdownPolicy.MAX_DURATION_MILLIS, extended.totalMillis)
        assertEquals(CountdownPolicy.MAX_DURATION_MILLIS, extended.remainingAt(10_000L))
        assertEquals(
            10_000L + CountdownPolicy.MAX_DURATION_MILLIS,
            extended.deadlineElapsedMillis,
        )
        assertEquals(extended, CountdownPolicy.extend(extended, 60_000L, 10_000L, 1_000_000L))
    }

    @Test fun restoreClampsWallClockRollbackToConfiguredTotal() {
        val restored = CountdownPolicy.restore(
            status = CountdownStatus.RUNNING,
            totalMillis = 60_000L,
            storedRemainingMillis = 60_000L,
            storedDeadlineElapsedMillis = null,
            storedDeadlineWallMillis = 5_000_000L,
            storedBootCount = 7,
            currentBootCount = 8,
            nowElapsedMillis = 2_000L,
            nowWallMillis = 4_000_000L,
            label = "Clock",
            keepScreenOn = true,
            completionNotified = false,
        )
        assertEquals(60_000L, restored.remainingMillis)
        assertEquals(62_000L, restored.deadlineElapsedMillis)
    }

    @Test fun restoreSelectsMonotonicWithinBootAndWallClockAfterReboot() {
        val sameBoot = CountdownPolicy.restore(
            status = CountdownStatus.RUNNING,
            totalMillis = 60_000L,
            storedRemainingMillis = 1L,
            storedDeadlineElapsedMillis = 50_000L,
            storedDeadlineWallMillis = 5_000_000L,
            storedBootCount = 7,
            currentBootCount = 7,
            nowElapsedMillis = 20_000L,
            nowWallMillis = 4_000_000L,
            label = "Tea",
            keepScreenOn = true,
            completionNotified = false,
        )
        assertEquals(30_000L, sameBoot.remainingMillis)
        assertEquals(50_000L, sameBoot.deadlineElapsedMillis)

        val afterReboot = CountdownPolicy.restore(
            status = CountdownStatus.RUNNING,
            totalMillis = 60_000L,
            storedRemainingMillis = 60_000L,
            storedDeadlineElapsedMillis = 50_000L,
            storedDeadlineWallMillis = 5_000_000L,
            storedBootCount = 7,
            currentBootCount = 8,
            nowElapsedMillis = 2_000L,
            nowWallMillis = 4_970_000L,
            label = "Tea",
            keepScreenOn = true,
            completionNotified = false,
        )
        assertEquals(30_000L, afterReboot.remainingMillis)
        assertEquals(32_000L, afterReboot.deadlineElapsedMillis)

        val expired = CountdownPolicy.restore(
            status = CountdownStatus.RUNNING,
            totalMillis = 60_000L,
            storedRemainingMillis = 60_000L,
            storedDeadlineElapsedMillis = 50_000L,
            storedDeadlineWallMillis = 5_000_000L,
            storedBootCount = 7,
            currentBootCount = 8,
            nowElapsedMillis = 2_000L,
            nowWallMillis = 5_100_000L,
            label = "Tea",
            keepScreenOn = true,
            completionNotified = false,
        )
        assertEquals(CountdownStatus.FINISHED, expired.status)
        assertEquals(0L, expired.remainingMillis)
    }

    private object CountdownUseCasesForTest {
        fun validate(hours: String, minutes: String, seconds: String, label: String): TimerValidation {
            val repository = object : com.cangshuo.toolbox.feature.timer.domain.CountdownRepository {
                override val state = kotlinx.coroutines.flow.MutableStateFlow(CountdownTimerState())
                override fun prepare(durationMillis: Long) = Unit
                override fun start(durationMillis: Long, label: String) = Unit
                override fun pause() = Unit
                override fun resume() = Unit
                override fun reset() = Unit
                override fun extend(additionalMillis: Long) = Unit
                override fun finish() = false
                override fun markCompletionNotified() = Unit
                override fun setKeepScreenOn(enabled: Boolean) = Unit
                override fun ensureService() = Unit
                override fun nowElapsedMillis() = 0L
                override fun nowWallMillis() = 0L
            }
            return com.cangshuo.toolbox.feature.timer.domain.CountdownUseCases(repository).validate(
                TimerDurationInput(hours, minutes, seconds, label),
            )
        }
    }
}
