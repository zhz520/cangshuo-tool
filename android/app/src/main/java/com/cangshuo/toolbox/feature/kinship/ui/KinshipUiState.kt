package com.cangshuo.toolbox.feature.kinship.ui

import com.cangshuo.toolbox.feature.kinship.domain.KinshipError
import com.cangshuo.toolbox.feature.kinship.domain.KinshipOutcome
import com.cangshuo.toolbox.feature.kinship.domain.KinshipSex
import com.cangshuo.toolbox.feature.kinship.domain.KinshipType

data class KinshipUiState(
    val text: String = "",
    val target: String = "",
    val sex: KinshipSex = KinshipSex.UNSPECIFIED,
    val type: KinshipType = KinshipType.TITLE,
    val reverse: Boolean = false,
    val optimal: Boolean = false,
    /** Null while both inputs are empty. */
    val outcome: KinshipOutcome? = null,
)

val KinshipError.messageKey: String
    get() = name
