package com.cangshuo.toolbox.feature.urlextract.domain

data class UrlExtractOptions(val includeBareDomains: Boolean, val dedupe: Boolean)

enum class UrlOutputFormat { LINES, MARKDOWN }

data class UrlExtractResult(val links: List<String>, val truncated: Boolean)

enum class UrlExtractError { INPUT_TOO_LONG }

object UrlExtractLimits {
    const val MAX_INPUT_LENGTH = 200_000
    const val MAX_RESULTS = 1000
    const val MAX_DISPLAYED = 50
}

sealed interface UrlExtractOutcome {
    data class Success(val result: UrlExtractResult) : UrlExtractOutcome

    data class Failure(val error: UrlExtractError) : UrlExtractOutcome
}
