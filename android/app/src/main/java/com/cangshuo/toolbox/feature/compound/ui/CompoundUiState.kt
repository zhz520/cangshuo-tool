package com.cangshuo.toolbox.feature.compound.ui

import com.cangshuo.toolbox.feature.compound.domain.CompoundOutcome
import com.cangshuo.toolbox.feature.compound.domain.CompoundPeriod

data class CompoundUiState(
    val principalText: String = "",
    val contributionText: String = "",
    val rateText: String = "",
    val yearsText: String = "",
    val period: CompoundPeriod = CompoundPeriod.MONTHLY,
    /** Null while the form is incomplete. */
    val outcome: CompoundOutcome? = null,
)
