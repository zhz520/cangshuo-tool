package com.cangshuo.toolbox.feature.charconvert.domain

enum class WidthMode { NONE, TO_HALF, TO_FULL }

enum class CaseMode { NONE, UPPER, LOWER }

data class CharConvertOptions(
    val width: WidthMode = WidthMode.TO_HALF,
    val case: CaseMode = CaseMode.NONE,
    val collapseWhitespace: Boolean = false,
    val removeZeroWidth: Boolean = true,
    val unifyNewlines: Boolean = true,
)

enum class CharConvertError { INPUT_TOO_LONG }

object CharConvertLimits {
    const val MAX_INPUT_LENGTH = 200_000
}

sealed interface CharConvertOutcome {
    data class Success(val text: String) : CharConvertOutcome

    data class Failure(val error: CharConvertError) : CharConvertOutcome
}
