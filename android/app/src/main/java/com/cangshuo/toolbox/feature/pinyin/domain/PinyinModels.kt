package com.cangshuo.toolbox.feature.pinyin.domain

/** Output tone style. */
enum class PinyinToneStyle { TONE_MARK, TONE_NUMBER, NO_TONE }

/** Letter case for the pinyin output. */
enum class PinyinCaseMode { LOWER, UPPER, CAPITALIZE }

/** Separator between syllables. */
enum class PinyinSeparator(val value: String) { SPACE(" "), HYPHEN("-"), NONE("") }

/** ü representation. The tone-mark style always writes ü and ignores this option. */
enum class PinyinVCharType { UNICODE, V, U_COLON }

/** Conversion mode. */
enum class PinyinMode { FULL, INITIALS, POLYPHONE }

data class PinyinOptions(
    val mode: PinyinMode = PinyinMode.FULL,
    val toneStyle: PinyinToneStyle = PinyinToneStyle.TONE_MARK,
    val caseMode: PinyinCaseMode = PinyinCaseMode.LOWER,
    val separator: PinyinSeparator = PinyinSeparator.SPACE,
    val vCharType: PinyinVCharType = PinyinVCharType.UNICODE,
    val keepNonChinese: Boolean = true,
)

data class PinyinStatistics(
    val totalChars: Int = 0,
    val chineseChars: Int = 0,
    val nonChineseChars: Int = 0,
    val unsupportedChineseChars: Int = 0,
)

enum class PinyinError { INPUT_TOO_LONG, INVALID_UNICODE, OUTPUT_TOO_LONG, TIME_LIMIT, PROCESSING_FAILED }

object PinyinLimits {
    const val MAX_INPUT_LENGTH = 100_000
    const val MAX_OUTPUT_LENGTH = 1_000_000
    const val PREVIEW_LENGTH = 12_000
    const val WORK_BUDGET_NANOS = 3_000_000_000L
}

/** Called repeatedly while converting; it may throw to stop the work. */
fun interface PinyinCheckpoint {
    fun check()
}

/** Raised when the conversion hits one of the documented resource limits. */
class PinyinProcessingException(val error: PinyinError) : RuntimeException(error.name)

sealed interface PinyinOutcome {
    data class Success(
        val text: String,
        val statistics: PinyinStatistics,
    ) : PinyinOutcome

    data class Failure(val error: PinyinError) : PinyinOutcome
}
