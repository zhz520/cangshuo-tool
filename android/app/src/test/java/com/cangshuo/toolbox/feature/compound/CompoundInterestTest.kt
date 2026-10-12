package com.cangshuo.toolbox.feature.compound

import com.cangshuo.toolbox.feature.compound.data.LocalCompoundRepository
import com.cangshuo.toolbox.feature.compound.domain.CalculateCompoundUseCase
import com.cangshuo.toolbox.feature.compound.domain.CompoundError
import com.cangshuo.toolbox.feature.compound.domain.CompoundInput
import com.cangshuo.toolbox.feature.compound.domain.CompoundLimits
import com.cangshuo.toolbox.feature.compound.domain.CompoundOutcome
import com.cangshuo.toolbox.feature.compound.domain.CompoundPeriod
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompoundInterestTest {
    private val calculate = CalculateCompoundUseCase(LocalCompoundRepository())

    private fun success(input: CompoundInput) = (calculate(input) as CompoundOutcome.Success).result

    private fun input(
        principal: String,
        contribution: String = "0",
        rate: String = "0",
        years: Int = 1,
        period: CompoundPeriod = CompoundPeriod.MONTHLY,
    ) = CompoundInput(BigDecimal(principal), BigDecimal(contribution), BigDecimal(rate), years, period)

    @Test
    fun zeroRateOnlyAddsContributions() {
        val result = success(input(principal = "1000", contribution = "100"))
        assertEquals(0, result.futureValue.compareTo(BigDecimal("2200")))
        assertEquals(0, result.totalContributed.compareTo(BigDecimal("2200")))
        assertEquals(0, result.totalInterest.compareTo(BigDecimal.ZERO))
        assertEquals(0, result.multiplier.compareTo(BigDecimal.ONE))
    }

    @Test
    fun monthlyCompoundingMatchesTheAnnualGrowth() {
        val result = success(input(principal = "10000", rate = "12"))
        // 10000 x (1.01)^12
        assertEquals(11268.25, result.futureValue.toDouble(), 0.01)
        assertEquals(0.0, result.totalInterest.toDouble() - 1268.25, 0.01)
    }

    @Test
    fun annualPeriodAddsOneYearAtATime() {
        val monthly = success(input(principal = "10000", contribution = "1000", rate = "6", years = 2))
        val annual = success(
            input(principal = "10000", contribution = "1000", rate = "6", years = 2, period = CompoundPeriod.ANNUAL),
        )
        assertEquals(2, annual.rows.size)
        assertEquals(0, annual.futureValue.compareTo(annual.rows.last().balance))
        // Monthly compounding with the same nominal rate earns slightly more.
        assertEquals(true, monthly.futureValue > annual.futureValue)
    }

    @Test
    fun yearlyRowsExplainBalanceAndGrowth() {
        val result = success(input(principal = "5000", contribution = "500", rate = "8", years = 3))
        assertEquals(3, result.rows.size)
        result.rows.forEach { row ->
            assertEquals(0, row.balance.subtract(row.contributed).compareTo(row.interest))
        }
        assertEquals(0, result.rows.last().balance.compareTo(result.futureValue))
    }

    @Test
    fun negativeRatesDiscountEachEndOfPeriodContribution() {
        val result = success(input("1000", contribution = "100", rate = "-50", years = 2, period = CompoundPeriod.ANNUAL))
        assertEquals(0, result.futureValue.compareTo(BigDecimal("400")))
        assertEquals(0, result.totalContributed.compareTo(BigDecimal("1200")))
        assertEquals(0, result.totalInterest.compareTo(BigDecimal("-800")))
    }

    @Test
    fun extremeGrowthReturnsAnActionableFailureInsteadOfOverflow() {
        assertEquals(
            CompoundError.RESULT_LIMIT,
            (calculate(input("1000000000000", rate = "1000", years = 100)) as CompoundOutcome.Failure).error,
        )
    }

    @Test
    fun tinyPrincipalKeepsGrowthMultipleAsAValidDecimal() {
        val result = success(input("0.0000000000000000000001", rate = "10", years = 100))
        assertTrue(result.multiplier.signum() > 0)
        assertEquals(100, result.rows.size)
        assertTrue(result.futureValue <= CompoundLimits.MAX_RESULT)
    }

    @Test
    fun invalidInputsAreRejected() {
        assertEquals(
            CompoundError.INVALID,
            (calculate(input(principal = "-1")) as CompoundOutcome.Failure).error,
        )
        assertEquals(
            CompoundError.INVALID,
            (calculate(input(principal = "0", contribution = "0")) as CompoundOutcome.Failure).error,
        )
        assertEquals(
            CompoundError.RANGE,
            (calculate(input(principal = "100", rate = "2000")) as CompoundOutcome.Failure).error,
        )
        assertEquals(
            CompoundError.RANGE,
            (calculate(input(principal = "100", rate = "5", years = 0)) as CompoundOutcome.Failure).error,
        )
        assertEquals(
            CompoundError.AMOUNT_LIMIT,
            (calculate(input(principal = CompoundLimits.MAX_PRINCIPAL.add(BigDecimal.ONE).toPlainString())) as CompoundOutcome.Failure).error,
        )
    }
}
