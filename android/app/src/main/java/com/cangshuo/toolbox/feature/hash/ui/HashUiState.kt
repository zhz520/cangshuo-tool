package com.cangshuo.toolbox.feature.hash.ui

import com.cangshuo.toolbox.feature.hash.domain.HashResult

data class HashUiState(
    val inputText: String = "",
    val uppercase: Boolean = false,
    val hashes: List<HashResult> = emptyList(),
)
