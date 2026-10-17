package com.cangshuo.toolbox.feature.scoreboard.ui

import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreboardSession

data class ScoreboardUiState(
    val session: ScoreboardSession = ScoreboardSession(),
    val displayMs: Long = 0L,
    val keepScreenOn: Boolean = true,
    val showAllHistory: Boolean = false,
)
