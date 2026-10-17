package com.cangshuo.toolbox.feature.decisionwheel.ui

import com.cangshuo.toolbox.feature.decisionwheel.domain.DecisionWheelError
import com.cangshuo.toolbox.feature.decisionwheel.domain.WheelResult
import com.cangshuo.toolbox.feature.decisionwheel.domain.WheelSettings
import com.cangshuo.toolbox.feature.decisionwheel.domain.WheelSpin

data class DecisionWheelUiState(
    val settings: WheelSettings,
    val spin: WheelSpin? = null,
    val lastResult: WheelResult? = null,
    val resultVisible: Boolean = false,
    val rotation: Float = 0f,
    val history: List<WheelResult> = emptyList(),
    val editError: DecisionWheelError? = null,
) {
    val spinning: Boolean get() = spin != null
}
