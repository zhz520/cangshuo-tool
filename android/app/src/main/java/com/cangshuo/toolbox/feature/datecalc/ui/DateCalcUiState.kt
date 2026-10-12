package com.cangshuo.toolbox.feature.datecalc.ui

import com.cangshuo.toolbox.feature.datecalc.domain.DateDiffOutcome
import com.cangshuo.toolbox.feature.datecalc.domain.DateDirection
import com.cangshuo.toolbox.feature.datecalc.domain.DateShiftOutcome
import com.cangshuo.toolbox.feature.datecalc.domain.DateUnit

data class DateCalcUiState(
    val firstText: String = "",
    val secondText: String = "",
    val baseText: String = "",
    val amountText: String = "",
    val unit: DateUnit = DateUnit.DAYS,
    val direction: DateDirection = DateDirection.AFTER,
    /** Null while either date of the difference section is missing. */
    val diffOutcome: DateDiffOutcome? = null,
    /** Null while the base date or the amount of the shift section is missing. */
    val shiftOutcome: DateShiftOutcome? = null,
)
