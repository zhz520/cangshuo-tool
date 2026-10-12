package com.cangshuo.toolbox.feature.mortgage.domain

import java.math.BigDecimal

/** 等额本息 (equal installment) or 等额本金 (equal principal). */
enum class MortgageMethod { EQUAL_INSTALLMENT, EQUAL_PRINCIPAL }

data class MortgageInput(
    val amountYuan: BigDecimal,
    val annualRatePercent: BigDecimal,
    val years: Int,
    val method: MortgageMethod,
)

data class MortgagePaymentRow(
    val period: Int,
    val payment: BigDecimal,
    val principalPart: BigDecimal,
    val interestPart: BigDecimal,
    val remaining: BigDecimal,
)

data class MortgageResult(
    val method: MortgageMethod,
    val months: Int,
    /** Fixed monthly payment for equal installment; null for equal principal. */
    val monthlyPayment: BigDecimal?,
    val firstPayment: BigDecimal,
    val lastPayment: BigDecimal,
    /** Monthly payment decrease for equal principal; null for equal installment. */
    val monthlyDecrease: BigDecimal?,
    val totalInterest: BigDecimal,
    val totalPayment: BigDecimal,
    val rows: List<MortgagePaymentRow>,
)

enum class MortgageError { INVALID, RANGE, AMOUNT_LIMIT, INPUT_TOO_LONG }

object MortgageLimits {
    val MAX_AMOUNT_YUAN: BigDecimal = BigDecimal("1000000000")
    val MAX_RATE: BigDecimal = BigDecimal("24")
    const val MIN_YEARS = 1
    const val MAX_YEARS = 40
    const val MAX_INPUT_LENGTH = 24
    const val SCHEDULE_PREVIEW_ROWS = 12
    val TERM_PRESETS = listOf(10, 15, 20, 25, 30)
}

sealed interface MortgageOutcome {
    data class Success(val result: MortgageResult) : MortgageOutcome
    data class Failure(val error: MortgageError) : MortgageOutcome
}
