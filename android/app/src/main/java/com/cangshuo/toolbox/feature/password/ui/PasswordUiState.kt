package com.cangshuo.toolbox.feature.password.ui

import com.cangshuo.toolbox.feature.password.domain.PasswordOutcome

data class PasswordUiState(
    val lengthText: String = "16",
    val countText: String = "1",
    val uppercase: Boolean = true,
    val lowercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val excludeSimilar: Boolean = true,
    val requireEachClass: Boolean = true,
    val uniqueCharacters: Boolean = false,
    val excludeText: String = "",
    /** Null only before the first evaluation; every option change regenerates. */
    val outcome: PasswordOutcome? = null,
)
