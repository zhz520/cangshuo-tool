package com.cangshuo.toolbox.feature.chineseconvert.domain

enum class ChineseConversionMode {
    S2T, T2S, S2TW, TW2S, S2TWP, TW2SP, S2HK, HK2S;

    val reverse: ChineseConversionMode
        get() = when (this) {
            S2T -> T2S
            T2S -> S2T
            S2TW -> TW2S
            TW2S -> S2TW
            S2TWP -> TW2SP
            TW2SP -> S2TWP
            S2HK -> HK2S
            HK2S -> S2HK
        }
}

object ChineseConversionLimits {
    const val MAX_INPUT = 100_000
    const val MAX_OUTPUT = 300_000
    const val PREVIEW = 12_000
    const val BUDGET_NANOS = 3_000_000_000L
}

enum class ChineseConversionError { INPUT_TOO_LONG, INVALID_UNICODE, OUTPUT_TOO_LONG, TIME_LIMIT, FAILED }

fun interface ChineseConversionCheckpoint { fun check() }

class ChineseConversionException(val error: ChineseConversionError) : RuntimeException(error.name)

data class ChineseConversionStatistics(
    val inputCharacters: Int,
    val outputCharacters: Int,
    val changedSegments: Int,
)

sealed interface ChineseConversionOutcome {
    data class Success(val text: String, val statistics: ChineseConversionStatistics) : ChineseConversionOutcome
    data class Failure(val error: ChineseConversionError) : ChineseConversionOutcome
}

fun interface ChineseConversionRepository {
    fun convert(text: String, mode: ChineseConversionMode, checkpoint: ChineseConversionCheckpoint): ChineseConversionOutcome.Success
}
