package com.cangshuo.toolbox.feature.radix.ui

import com.cangshuo.toolbox.feature.radix.domain.RadixResult

data class RadixUiState(
    val inputText: String = "",
    /** null means auto detection. */
    val selectedRadix: Int? = null,
    val customMode: Boolean = false,
    val customRadix: Int = 12,
    val uppercase: Boolean = false,
    val result: RadixResult? = null,
)
