package com.cangshuo.toolbox.feature.color.ui

import com.cangshuo.toolbox.feature.color.domain.ColorDescription
import com.cangshuo.toolbox.feature.color.domain.ColorError

sealed interface ColorOutcome {
    data class Success(val description: ColorDescription) : ColorOutcome

    data class Failure(val error: ColorError) : ColorOutcome
}

data class ColorUiState(
    val inputText: String = "",
    /** Null while the field is empty. */
    val outcome: ColorOutcome? = null,
)
