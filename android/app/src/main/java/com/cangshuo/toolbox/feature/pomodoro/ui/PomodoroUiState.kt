package com.cangshuo.toolbox.feature.pomodoro.ui

import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroError
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroSession
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroSettings
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroStats
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroTimerState

enum class PomodoroMessage { SAVED, RESET, SKIPPED, HISTORY_CLEARED, STORAGE_FAILED }

data class PomodoroUiState(
    val timer: PomodoroTimerState = PomodoroTimerState(),
    val settings: PomodoroSettings = PomodoroSettings(),
    val stats: PomodoroStats = PomodoroStats(),
    val history: List<PomodoroSession> = emptyList(),
    val todayEpochDay: Long = 0,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: PomodoroError? = null,
    val message: PomodoroMessage? = null,
    val saveRevision: Long = 0,
)
