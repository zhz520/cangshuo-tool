package com.cangshuo.toolbox.feature.text.ui

import com.cangshuo.toolbox.feature.text.domain.TextCategoryGroup
import com.cangshuo.toolbox.feature.text.domain.TextOperation
import com.cangshuo.toolbox.feature.text.domain.TextStatistics

data class TextUiState(
    val selectedGroup: TextCategoryGroup = TextCategoryGroup.CASE,
    val selectedOperation: TextOperation = TextOperation.UPPERCASE,
    val inputText: String = "",
    val outputText: String = "",
    val stats: TextStatistics? = TextStatistics(),
    val findText: String = "",
    val replaceText: String = "",
    val matchCase: Boolean = true,
    val useRegex: Boolean = false,
    val expandGroups: Boolean = true,
    val multiline: Boolean = false,
    val dotAll: Boolean = false,
    val matchCount: Int? = null,
    val processingErrorRes: Int? = null,
    val inputErrorRes: Int? = null,
    val statusMessageRes: Int? = null,
    val isProcessing: Boolean = false,
    val hasResult: Boolean = false,
)
