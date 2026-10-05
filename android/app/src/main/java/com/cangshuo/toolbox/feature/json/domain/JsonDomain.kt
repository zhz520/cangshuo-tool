package com.cangshuo.toolbox.feature.json.domain

enum class JsonIndent(val spaces: Int) { TWO_SPACES(2), FOUR_SPACES(4) }
enum class JsonStringInput { QUOTED_LITERAL, ESCAPED_CONTENT }

enum class JsonFailure {
    INPUT_LIMIT, OUTPUT_LIMIT, DEPTH_LIMIT, TOKEN_LIMIT, INVALID_UNICODE,
    EXPECTED_VALUE, EXPECTED_KEY, EXPECTED_COLON, EXPECTED_SEPARATOR,
    INVALID_NUMBER, INVALID_STRING, INVALID_ESCAPE, TRAILING_CONTENT,
    TIME_BUDGET, MEMORY_LIMIT, PROCESSING_FAILED,
}

sealed interface JsonProcessResult {
    val inputByteCount: Int?

    data class Success(
        val output: String,
        val lineCount: Int,
        val byteCount: Int,
        override val inputByteCount: Int,
        val duplicateKeyCount: Int = 0,
    ) : JsonProcessResult

    data class Error(
        val reason: JsonFailure,
        val line: Int? = null,
        val column: Int? = null,
        override val inputByteCount: Int? = null,
    ) : JsonProcessResult

    data class Empty(override val inputByteCount: Int = 0) : JsonProcessResult
}

class JsonProcessingException(val reason: JsonFailure, val offset: Int? = null) : Exception()

object JsonProcessingPolicy {
    const val MAX_INPUT_LENGTH = 200_000
    const val MAX_OUTPUT_LENGTH = 200_000
    const val MAX_DEPTH = 64
    const val MAX_TOKENS = 50_000
    const val WORK_BUDGET_NANOS = 2_000_000_000L
}
