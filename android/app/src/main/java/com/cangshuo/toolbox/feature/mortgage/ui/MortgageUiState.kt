package com.cangshuo.toolbox.feature.mortgage.ui

import com.cangshuo.toolbox.feature.mortgage.domain.MortgageMethod
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageOutcome

data class MortgageUiState(
    val amountText: String = "",
    val rateText: String = "",
    val yearsText: String = "",
    val method: MortgageMethod = MortgageMethod.EQUAL_INSTALLMENT,
    val showAllRows: Boolean = false,
    /** Null while the form is incomplete. */
    val outcome: MortgageOutcome? = null,
)
