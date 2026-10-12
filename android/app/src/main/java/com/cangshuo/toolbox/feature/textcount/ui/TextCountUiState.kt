package com.cangshuo.toolbox.feature.textcount.ui

import com.cangshuo.toolbox.feature.textcount.domain.TextCountOutcome

data class TextCountUiState(
    val text: String = "",
    /** Null while the field is empty. */
    val outcome: TextCountOutcome? = null,
)
