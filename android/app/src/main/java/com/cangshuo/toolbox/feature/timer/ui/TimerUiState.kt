package com.cangshuo.toolbox.feature.timer.ui

import com.cangshuo.toolbox.feature.timer.domain.CountdownStatus
import com.cangshuo.toolbox.feature.timer.domain.TimerError

data class TimerUiState(
    val loading: Boolean = true,
    val status: CountdownStatus = CountdownStatus.IDLE,
    val totalMillis: Long = 5 * 60_000L,
    val remainingMillis: Long = 5 * 60_000L,
    val progress: Float = 0f,
    val finishAtWallMillis: Long? = null,
    val label: String = "",
    val hours: String = "0",
    val minutes: String = "5",
    val seconds: String = "0",
    val keepScreenOn: Boolean = true,
    val error: TimerError? = null,
    val message: TimerMessage? = null,
    val messageRevision: Int = 0,
) {
    val running: Boolean get() = status == CountdownStatus.RUNNING
    val paused: Boolean get() = status == CountdownStatus.PAUSED
    val finished: Boolean get() = status == CountdownStatus.FINISHED
    val durationEditable: Boolean get() = status == CountdownStatus.IDLE || status == CountdownStatus.FINISHED
}

enum class TimerMessage { STARTED, PAUSED, RESUMED, RESET, EXTENDED, FINISHED, STORAGE_FAILED }
