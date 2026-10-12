package com.cangshuo.toolbox.feature.numberchinese.domain

enum class ChineseNumberStyle { LOWER, UPPER, MONEY }

enum class NumberDirection { AUTO, TO_CHINESE, TO_NUMBER }

enum class NumberChineseError { EMPTY, INVALID_NUMBER, INVALID_CHINESE, TOO_LONG, MONEY_DECIMALS }

object NumberChineseLimits {
    const val MAX_INPUT_LENGTH = 60
    const val MAX_INTEGER_DIGITS = 24
    const val MAX_DECIMALS = 8
}

sealed interface NumberChineseOutcome {
    data class Success(val text: String) : NumberChineseOutcome

    data class Failure(val error: NumberChineseError) : NumberChineseOutcome
}
