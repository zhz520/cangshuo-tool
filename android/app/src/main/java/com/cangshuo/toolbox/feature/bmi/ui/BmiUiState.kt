package com.cangshuo.toolbox.feature.bmi.ui

import com.cangshuo.toolbox.feature.bmi.domain.BmiOutcome
import com.cangshuo.toolbox.feature.bmi.domain.BmiStandard

data class BmiUiState(
    val heightText: String = "",
    val weightText: String = "",
    val imperial: Boolean = false,
    val standard: BmiStandard = BmiStandard.CHINA,
    /** Null while the form is incomplete. */
    val outcome: BmiOutcome? = null,
)
