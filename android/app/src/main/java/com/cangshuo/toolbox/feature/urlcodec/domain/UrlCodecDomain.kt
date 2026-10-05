package com.cangshuo.toolbox.feature.urlcodec.domain

enum class UrlCodecMode { ENCODE, DECODE }

/** Profiles apply to both encoding and decoding; FORM_VALUE processes one value. */
enum class UrlEncodeType { COMPONENT, FULL_URL, FORM_VALUE }

enum class UrlCodecFailure {
    INPUT_LIMIT, OUTPUT_LIMIT, INVALID_UNICODE, INVALID_PERCENT, INVALID_UTF8,
    TIME_BUDGET, MEMORY_LIMIT, PROCESSING_FAILED,
}

sealed interface UrlCodecResult {
    data class Success(val text: String) : UrlCodecResult
    data object Empty : UrlCodecResult
    /** Zero-based offset in the original UTF-16 input, when a location is available. */
    data class Error(val reason: UrlCodecFailure, val offset: Int? = null) : UrlCodecResult
}

object UrlCodecPolicy {
    const val MAX_INPUT_LENGTH = 100_000
    const val MAX_OUTPUT_LENGTH = 100_000
    const val WORK_BUDGET_NANOS = 2_000_000_000L
}
