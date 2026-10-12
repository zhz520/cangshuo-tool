package com.cangshuo.toolbox.feature.textcount.domain

data class TextStats(
    val charactersWithSpaces: Int,
    val charactersWithoutSpaces: Int,
    val cjkCharacters: Int,
    val latinWords: Int,
    val wordCount: Int,
    val lines: Int,
    val nonEmptyLines: Int,
    val paragraphs: Int,
    val utf8Bytes: Int,
    val readingSeconds: Int,
)

enum class TextCountError { INPUT_TOO_LONG }

sealed interface TextCountOutcome {
    data class Success(val stats: TextStats) : TextCountOutcome

    data class Failure(val error: TextCountError) : TextCountOutcome
}

object TextCountLimits {
    const val MAX_INPUT_LENGTH = 200_000
}
