package com.cangshuo.toolbox.feature.charconvert.ui

import com.cangshuo.toolbox.feature.charconvert.domain.CaseMode
import com.cangshuo.toolbox.feature.charconvert.domain.CharConvertOutcome
import com.cangshuo.toolbox.feature.charconvert.domain.WidthMode

data class CharConvertUiState(
    val input: String = "",
    val width: WidthMode = WidthMode.TO_HALF,
    val case: CaseMode = CaseMode.NONE,
    val collapseWhitespace: Boolean = false,
    val removeZeroWidth: Boolean = true,
    val unifyNewlines: Boolean = true,
    /** Null while the field is empty. */
    val outcome: CharConvertOutcome? = null,
)
