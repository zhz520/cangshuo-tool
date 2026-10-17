package com.cangshuo.toolbox.feature.timescreen.ui

import com.cangshuo.toolbox.feature.timescreen.domain.DriftOffset
import com.cangshuo.toolbox.feature.timescreen.domain.TimeScreenSettings
import com.cangshuo.toolbox.feature.timescreen.domain.TimeScreenSnapshot

data class TimeScreenUiState(
    val settings: TimeScreenSettings = TimeScreenSettings(),
    val snapshot: TimeScreenSnapshot,
    val drift: DriftOffset = DriftOffset(0f, 0f),
)
