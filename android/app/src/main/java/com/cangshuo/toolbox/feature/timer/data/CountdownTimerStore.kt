package com.cangshuo.toolbox.feature.timer.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import com.cangshuo.toolbox.feature.timer.domain.CountdownPolicy
import com.cangshuo.toolbox.feature.timer.domain.CountdownStatus
import com.cangshuo.toolbox.feature.timer.domain.CountdownTimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CountdownTimerStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val initial = restore()
    private val mutable = MutableStateFlow(initial)
    val state: StateFlow<CountdownTimerState> = mutable.asStateFlow()

    init {
        persist(initial)
    }

    fun save(state: CountdownTimerState) {
        persist(state)
        mutable.value = state
    }

    fun read(): CountdownTimerState = mutable.value
    fun nowElapsedMillis(): Long = SystemClock.elapsedRealtime()
    fun nowWallMillis(): Long = System.currentTimeMillis()
    fun bootCount(): Int = runCatching {
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrDefault(-1)

    private fun restore(): CountdownTimerState {
        val status = CountdownStatus.fromStorage(preferences.getInt(KEY_STATUS, CountdownStatus.IDLE.storage))
        val total = preferences.getLong(KEY_TOTAL, CountdownPolicy.DEFAULT_DURATION_MILLIS)
            .coerceIn(CountdownPolicy.MIN_DURATION_MILLIS, CountdownPolicy.MAX_DURATION_MILLIS)
        val storedRemaining = preferences.getLong(KEY_REMAINING, total).coerceIn(0L, total)
        val storedDeadlineElapsed = preferences.getLong(KEY_DEADLINE_ELAPSED, -1L).takeIf { it > 0L }
        val storedDeadlineWall = preferences.getLong(KEY_DEADLINE_WALL, -1L).takeIf { it > 0L }
        val storedBoot = preferences.getInt(KEY_BOOT_COUNT, -1)
        val label = preferences.getString(KEY_LABEL, "").orEmpty()
        val keepScreen = preferences.getBoolean(KEY_KEEP_SCREEN, true)
        val completionNotified = preferences.getBoolean(KEY_COMPLETION_NOTIFIED, false)
        return CountdownPolicy.restore(
            status = status,
            totalMillis = total,
            storedRemainingMillis = storedRemaining,
            storedDeadlineElapsedMillis = storedDeadlineElapsed,
            storedDeadlineWallMillis = storedDeadlineWall,
            storedBootCount = storedBoot,
            currentBootCount = bootCount(),
            nowElapsedMillis = nowElapsedMillis(),
            nowWallMillis = nowWallMillis(),
            label = label,
            keepScreenOn = keepScreen,
            completionNotified = completionNotified,
        )
    }

    private fun persist(state: CountdownTimerState) {
        preferences.edit(commit = true) {
            putInt(KEY_STATUS, state.status.storage)
            putLong(KEY_TOTAL, state.totalMillis)
            putLong(KEY_REMAINING, state.remainingAt(nowElapsedMillis()))
            val elapsed = state.deadlineElapsedMillis
            if (elapsed == null) remove(KEY_DEADLINE_ELAPSED) else putLong(KEY_DEADLINE_ELAPSED, elapsed)
            val wall = state.deadlineWallMillis
            if (wall == null) remove(KEY_DEADLINE_WALL) else putLong(KEY_DEADLINE_WALL, wall)
            if (state.status == CountdownStatus.RUNNING) putInt(KEY_BOOT_COUNT, bootCount())
            else remove(KEY_BOOT_COUNT)
            putString(KEY_LABEL, state.label)
            putBoolean(KEY_KEEP_SCREEN, state.keepScreenOn)
            putBoolean(KEY_COMPLETION_NOTIFIED, state.completionNotified)
        }
    }

    private companion object {
        const val PREFERENCES = "countdown_timer_v1"
        const val KEY_STATUS = "status"
        const val KEY_TOTAL = "total_millis"
        const val KEY_REMAINING = "remaining_millis"
        const val KEY_DEADLINE_ELAPSED = "deadline_elapsed_millis"
        const val KEY_DEADLINE_WALL = "deadline_wall_millis"
        const val KEY_BOOT_COUNT = "boot_count"
        const val KEY_LABEL = "label"
        const val KEY_KEEP_SCREEN = "keep_screen_on"
        const val KEY_COMPLETION_NOTIFIED = "completion_notified"
    }
}
