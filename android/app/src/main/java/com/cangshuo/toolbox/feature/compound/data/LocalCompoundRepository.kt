package com.cangshuo.toolbox.feature.compound.data

import com.cangshuo.toolbox.feature.compound.domain.CompoundError
import com.cangshuo.toolbox.feature.compound.domain.CompoundInput
import com.cangshuo.toolbox.feature.compound.domain.CompoundLimits
import com.cangshuo.toolbox.feature.compound.domain.CompoundOutcome
import com.cangshuo.toolbox.feature.compound.domain.CompoundRepository
import com.cangshuo.toolbox.feature.compound.domain.CompoundResult
import com.cangshuo.toolbox.feature.compound.domain.CompoundYearRow
import java.math.BigDecimal
import java.math.MathContext

class LocalCompoundRepository : CompoundRepository {

    override fun calculate(input: CompoundInput): CompoundOutcome {
        val principal = input.principal
        val contribution = input.contributionPerPeriod
        if (principal.signum() < 0 || contribution.signum() < 0) return CompoundOutcome.Failure(CompoundError.INVALID)
        if (principal > CompoundLimits.MAX_PRINCIPAL || contribution > CompoundLimits.MAX_CONTRIBUTION) {
            return CompoundOutcome.Failure(CompoundError.AMOUNT_LIMIT)
        }
        if (principal.signum() == 0 && contribution.signum() == 0) {
            return CompoundOutcome.Failure(CompoundError.INVALID)
        }
        if (input.annualRatePercent < CompoundLimits.MIN_RATE || input.annualRatePercent > CompoundLimits.MAX_RATE) {
            return CompoundOutcome.Failure(CompoundError.RANGE)
        }
        if (input.years < CompoundLimits.MIN_YEARS || input.years > CompoundLimits.MAX_YEARS) {
            return CompoundOutcome.Failure(CompoundError.RANGE)
        }

        val periodsPerYear = input.period.periodsPerYear
        val periods = input.years * periodsPerYear
        val ratePerPeriod = input.annualRatePercent
            .divide(BigDecimal(100), MathContext.DECIMAL128)
            .divide(BigDecimal(periodsPerYear), MathContext.DECIMAL128)
        val growth = BigDecimal.ONE.add(ratePerPeriod, MathContext.DECIMAL128)

        var balance = principal
        var contributed = principal
        val rows = ArrayList<CompoundYearRow>(input.years)
        for (index in 1..periods) {
            balance = balance.multiply(growth, MathContext.DECIMAL128).add(contribution, MathContext.DECIMAL128)
            if (balance > CompoundLimits.MAX_RESULT) return CompoundOutcome.Failure(CompoundError.RESULT_LIMIT)
            contributed = contributed.add(contribution, MathContext.DECIMAL128)
            if (index % periodsPerYear == 0) {
                rows += CompoundYearRow(
                    year = index / periodsPerYear,
                    balance = balance,
                    contributed = contributed,
                    interest = balance.subtract(contributed, MathContext.DECIMAL128),
                )
            }
        }
        val totalInterest = balance.subtract(contributed, MathContext.DECIMAL128)
        val multiplier = if (contributed.signum() == 0) {
            BigDecimal.ZERO
        } else {
            balance.divide(contributed, MathContext.DECIMAL128)
        }
        return CompoundOutcome.Success(
            CompoundResult(
                futureValue = balance,
                totalContributed = contributed,
                totalInterest = totalInterest,
                multiplier = multiplier,
                rows = rows,
            ),
        )
    }
}
