package com.cangshuo.toolbox.feature.datecalc.domain

import java.time.LocalDate

interface DateCalcRepository {
    /** Difference between two dates; the result is order-independent. */
    fun difference(first: LocalDate, second: LocalDate): DateDifference

    /** Moves [base] by [amount] whole [unit]s and rejects results outside the supported years. */
    fun shift(base: LocalDate, amount: Long, unit: DateUnit, direction: DateDirection): DateShiftOutcome
}
