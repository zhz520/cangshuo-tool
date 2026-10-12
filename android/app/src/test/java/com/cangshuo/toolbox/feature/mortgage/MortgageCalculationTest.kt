package com.cangshuo.toolbox.feature.mortgage

import com.cangshuo.toolbox.feature.mortgage.data.LocalMortgageRepository
import com.cangshuo.toolbox.feature.mortgage.domain.CalculateMortgageUseCase
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageError
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageInput
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageMethod
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageOutcome
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MortgageCalculationTest {
    private val calculate = CalculateMortgageUseCase(LocalMortgageRepository())

    private fun success(
        amount: String,
        rate: String,
        years: Int,
        method: MortgageMethod = MortgageMethod.EQUAL_INSTALLMENT,
    ) = (calculate(MortgageInput(BigDecimal(amount), BigDecimal(rate), years, method)) as MortgageOutcome.Success).result

    @Test
    fun equalInstallmentMatchesTheClosedFormPayment() {
        val result = success("1000000", "4.9", 30)
        assertEquals(360, result.rows.size)
        assertEquals(5307.27, result.monthlyPayment!!.toDouble(), 0.01)
        assertEquals(5307.27, result.firstPayment.toDouble(), 0.01)
        assertEquals(0.0, result.rows.last().remaining.toDouble(), 1e-6)
        assertEquals(result.totalPayment.toDouble() - 1000000.0, result.totalInterest.toDouble(), 0.01)
    }

    @Test
    fun equalPrincipalDecreasesEveryMonth() {
        val result = success("1000000", "4.9", 30, MortgageMethod.EQUAL_PRINCIPAL)
        assertNull(result.monthlyPayment)
        assertEquals(6861.11, result.firstPayment.toDouble(), 0.01)
        assertEquals(2789.12, result.lastPayment.toDouble(), 0.01)
        assertEquals(11.34, result.monthlyDecrease!!.toDouble(), 0.01)
        assertEquals(
            result.firstPayment.toDouble() - result.lastPayment.toDouble(),
            result.monthlyDecrease!!.toDouble() * 359,
            0.01,
        )
    }

    @Test
    fun zeroRateLoanSplitsThePrincipalEvenly() {
        val result = success("360000", "0", 30)
        assertEquals(1000.0, result.monthlyPayment!!.toDouble(), 1e-6)
        assertEquals(0.0, result.totalInterest.toDouble(), 1e-6)
        assertEquals(360000.0, result.totalPayment.toDouble(), 1e-6)
    }

    @Test
    fun tinyPositiveRateDoesNotDivideByZeroOrDistortPayments() {
        val result = success("1000000", "0.00000000000000001", 30)
        assertEquals(1000000.0 / 360, result.monthlyPayment!!.toDouble(), 1e-8)
        assertTrue(result.totalInterest.signum() > 0)
        assertEquals(0, result.rows.last().remaining.signum())
        assertTrue(result.rows.all { it.principalPart.signum() > 0 && it.remaining.signum() >= 0 })
    }

    @Test
    fun maximumLoanAndRateStillAmortizeWithoutNegativeBalances() {
        for (method in MortgageMethod.entries) {
            val result = success("1000000000", "24", 40, method)
            assertEquals(480, result.rows.size)
            assertEquals(0, result.rows.last().remaining.signum())
            assertTrue(result.rows.all { it.principalPart.signum() > 0 && it.interestPart.signum() >= 0 && it.remaining.signum() >= 0 })
            assertEquals(1000000000.0, result.rows.sumOf { it.principalPart.toDouble() }, 0.001)
            assertEquals(result.totalPayment.toDouble(), result.rows.sumOf { it.payment.toDouble() }, 0.001)
        }
    }

    @Test
    fun zeroRateTotalsAreExactWhenPrincipalDoesNotDivideEvenly() {
        for (method in MortgageMethod.entries) {
            val result = success("1000000", "0", 40, method)
            assertEquals(0, result.totalInterest.compareTo(BigDecimal.ZERO))
            assertEquals(0, result.totalPayment.compareTo(BigDecimal("1000000")))
            assertEquals(0, result.rows.last().remaining.signum())
        }
    }

    @Test
    fun invalidInputsAreRejected() {
        assertEquals(
            MortgageError.INVALID,
            (calculate(MortgageInput(BigDecimal("0"), BigDecimal("4"), 30, MortgageMethod.EQUAL_INSTALLMENT)) as MortgageOutcome.Failure).error,
        )
        assertEquals(
            MortgageError.RANGE,
            (calculate(MortgageInput(BigDecimal("100000"), BigDecimal("30"), 30, MortgageMethod.EQUAL_INSTALLMENT)) as MortgageOutcome.Failure).error,
        )
        assertEquals(
            MortgageError.RANGE,
            (calculate(MortgageInput(BigDecimal("100000"), BigDecimal("4"), 41, MortgageMethod.EQUAL_INSTALLMENT)) as MortgageOutcome.Failure).error,
        )
        assertEquals(
            MortgageError.AMOUNT_LIMIT,
            (calculate(MortgageInput(BigDecimal("1000000001"), BigDecimal("4"), 30, MortgageMethod.EQUAL_INSTALLMENT)) as MortgageOutcome.Failure).error,
        )
    }
}
