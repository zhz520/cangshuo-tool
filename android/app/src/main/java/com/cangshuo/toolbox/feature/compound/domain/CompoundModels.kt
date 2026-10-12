package com.cangshuo.toolbox.feature.compound.domain

import java.math.BigDecimal

/** Compounding frequency; contributions follow the same period. */
enum class CompoundPeriod(val periodsPerYear: Int) { MONTHLY(12), ANNUAL(1) }

data class CompoundInput(
    val principal: BigDecimal,
    val contributionPerPeriod: BigDecimal,
    val annualRatePercent: BigDecimal,
    val years: Int,
    val period: CompoundPeriod,
)

data class CompoundYearRow(
    val year: Int,
    val balance: BigDecimal,
    val contributed: BigDecimal,
    val interest: BigDecimal,
)

data class CompoundResult(
    val futureValue: BigDecimal,
    val totalContributed: BigDecimal,
    val totalInterest: BigDecimal,
    val multiplier: BigDecimal,
    val rows: List<CompoundYearRow>,
)

enum class CompoundError { INVALID, RANGE, AMOUNT_LIMIT, RESULT_LIMIT, INPUT_TOO_LONG }

object CompoundLimits {
    val MAX_PRINCIPAL: BigDecimal = BigDecimal("1000000000000")
    val MAX_CONTRIBUTION: BigDecimal = BigDecimal("1000000000")
    val MAX_RESULT: BigDecimal = BigDecimal("1000000000000000000")
    val MIN_RATE: BigDecimal = BigDecimal("-99")
    val MAX_RATE: BigDecimal = BigDecimal("1000")
    const val MIN_YEARS = 1
    const val MAX_YEARS = 100
    const val MAX_INPUT_LENGTH = 24
}

sealed interface CompoundOutcome {
    data class Success(val result: CompoundResult) : CompoundOutcome
    data class Failure(val error: CompoundError) : CompoundOutcome
}
