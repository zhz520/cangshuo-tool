package com.cangshuo.toolbox.feature.datecalc.domain

import java.time.LocalDate

class CalculateDateUseCase(private val repository: DateCalcRepository) {
    fun difference(first: LocalDate, second: LocalDate): DateDifference =
        repository.difference(first, second)

    fun shift(base: LocalDate, amount: Long, unit: DateUnit, direction: DateDirection): DateShiftOutcome =
        repository.shift(base, amount, unit, direction)
}
