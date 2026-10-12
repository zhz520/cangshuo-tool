package com.cangshuo.toolbox.feature.numberchinese.ui

import com.cangshuo.toolbox.feature.numberchinese.domain.ChineseNumberStyle
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberChineseError
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberChineseOutcome
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberDirection

data class NumberChineseUiState(
    val input: String = "",
    val style: ChineseNumberStyle = ChineseNumberStyle.LOWER,
    val direction: NumberDirection = NumberDirection.AUTO,
    val colloquialTen: Boolean = true,
    /** Null while the field is empty. */
    val outcome: NumberChineseOutcome? = null,
)
