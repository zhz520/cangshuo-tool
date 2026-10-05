package com.cangshuo.toolbox.feature.uuid.domain

data class UuidConfig(
    val count: Int = 1,
    val isUppercase: Boolean = false,
    val hasHyphens: Boolean = true,
    val isBraced: Boolean = false,
) {
    init {
        require(count in 1..100) { "Count must be between 1 and 100" }
    }
}
