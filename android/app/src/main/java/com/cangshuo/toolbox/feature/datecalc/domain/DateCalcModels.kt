package com.cangshuo.toolbox.feature.datecalc.domain

import java.time.LocalDate

/** Shift unit of the date calculator. */
enum class DateUnit { DAYS, WEEKS, MONTHS, YEARS }

/** Whether a shift moves forward or backward from the base date. */
enum class DateDirection { AFTER, BEFORE }

/** Normalized difference; the earlier/later input order does not matter. */
data class DateDifference(
    val totalDays: Long,
    val weeks: Long,
    val remainderDays: Long,
    val years: Long,
    val months: Long,
    val days: Long,
    val businessDays: Long,
    val weekendDays: Long,
)

data class DateShift(
    val base: LocalDate,
    val result: LocalDate,
    val signedDays: Long,
)

enum class DateCalcError {
    INVALID_DATE,
    OUT_OF_RANGE,
    INVALID_AMOUNT,
    AMOUNT_OUT_OF_RANGE,
    RESULT_OUT_OF_RANGE,
    INPUT_TOO_LONG,
}

object DateCalcLimits {
    const val MIN_YEAR = 1900
    const val MAX_YEAR = 2999
    const val MAX_DATE_LENGTH = 12
    const val MAX_AMOUNT_LENGTH = 6
    const val MAX_AMOUNT = 100_000L
}

sealed interface DateDiffOutcome {
    data class Success(val result: DateDifference) : DateDiffOutcome

    data class Failure(val error: DateCalcError) : DateDiffOutcome
}

sealed interface DateShiftOutcome {
    data class Success(val result: DateShift) : DateShiftOutcome

    data class Failure(val error: DateCalcError) : DateShiftOutcome
}
