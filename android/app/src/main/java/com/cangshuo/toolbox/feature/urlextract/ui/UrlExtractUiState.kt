package com.cangshuo.toolbox.feature.urlextract.ui

import com.cangshuo.toolbox.feature.urlextract.domain.UrlExtractOutcome
import com.cangshuo.toolbox.feature.urlextract.domain.UrlOutputFormat

data class UrlExtractUiState(
    val text: String = "",
    val includeBareDomains: Boolean = false,
    val dedupe: Boolean = true,
    val format: UrlOutputFormat = UrlOutputFormat.LINES,
    /** Text the copy button puts on the clipboard (empty when there is nothing to copy). */
    val copyText: String = "",
    /** Null while the field is empty. */
    val outcome: UrlExtractOutcome? = null,
)
