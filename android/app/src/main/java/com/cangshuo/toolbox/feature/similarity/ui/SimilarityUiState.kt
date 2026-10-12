package com.cangshuo.toolbox.feature.similarity.ui

import com.cangshuo.toolbox.feature.similarity.domain.SimilarityOutcome

data class SimilarityUiState(
    val first: String = "",
    val second: String = "",
    val ignoreCase: Boolean = false,
    val ignoreWhitespace: Boolean = false,
    /** Null while either field is empty. */
    val outcome: SimilarityOutcome? = null,
)
