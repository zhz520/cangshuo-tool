package com.cangshuo.toolbox.feature.uuid.ui

data class UuidUiState(
    val count: Int = 1,
    val isUppercase: Boolean = false,
    val hasHyphens: Boolean = true,
    val isBraced: Boolean = false,
    val generatedUuids: List<String> = emptyList(),
)
