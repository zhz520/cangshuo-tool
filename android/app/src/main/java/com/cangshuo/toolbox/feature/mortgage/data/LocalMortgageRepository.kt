package com.cangshuo.toolbox.feature.mortgage.data

import com.cangshuo.toolbox.feature.mortgage.domain.MortgageError
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageInput
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageLimits
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageMethod
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageOutcome
import com.cangshuo.toolbox.feature.mortgage.domain.MortgagePaymentRow
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageRepository
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageResult
import java.math.BigDecimal
import java.math.MathContext

/**
 * Standard amortization for the two Chinese repayment modes. The iteration
 * keeps 34 significant digits and lets the final period absorb residuals.
 * Discounted annuity payments avoid cancellation at near-zero interest rates.
 */
class LocalMortgageRepository : MortgageRepository {

    override fun calculate(input: MortgageInput): MortgageOutcome {
        val amount = input.amountYuan
        if (amount.signum() <= 0) return MortgageOutcome.Failure(MortgageError.INVALID)
        if (amount > MortgageLimits.MAX_AMOUNT_YUAN) return MortgageOutcome.Failure(MortgageError.AMOUNT_LIMIT)
        val rate = input.annualRatePercent
        if (rate.signum() < 0 || rate > MortgageLimits.MAX_RATE) return MortgageOutcome.Failure(MortgageError.RANGE)
        if (input.years < MortgageLimits.MIN_YEARS || input.years > MortgageLimits.MAX_YEARS) {
            return MortgageOutcome.Failure(MortgageError.RANGE)
        }

        val months = input.years * 12
        val precision = MathContext.DECIMAL128
        val monthlyRate = rate.divide(BigDecimal(1200), precision)
        val rows = ArrayList<MortgagePaymentRow>(months)
        var remaining = amount
        var totalInterest = BigDecimal.ZERO

        val equalInstallmentPayment = if (input.method == MortgageMethod.EQUAL_INSTALLMENT) {
            if (monthlyRate.signum() == 0) {
                amount.divide(BigDecimal(months), precision)
            } else {
                val discount = BigDecimal.ONE.divide(BigDecimal.ONE.add(monthlyRate, precision), precision)
                var discountPower = BigDecimal.ONE
                var annuityFactor = BigDecimal.ZERO
                repeat(months) {
                    discountPower = discountPower.multiply(discount, precision)
                    annuityFactor = annuityFactor.add(discountPower, precision)
                }
                amount.divide(annuityFactor, precision)
            }
        } else {
            null
        }
        val equalPrincipalPart = if (input.method == MortgageMethod.EQUAL_PRINCIPAL) {
            amount.divide(BigDecimal(months), precision)
        } else {
            null
        }

        for (period in 1..months) {
            val interest = remaining.multiply(monthlyRate, precision)
            val principalPart = when {
                period == months -> remaining
                input.method == MortgageMethod.EQUAL_INSTALLMENT -> equalInstallmentPayment!!.subtract(interest, precision)
                else -> equalPrincipalPart!!
            }
            val payment = principalPart.add(interest, precision)
            remaining = if (period == months) BigDecimal.ZERO else remaining.subtract(principalPart, precision)
            rows += MortgagePaymentRow(
                period = period,
                payment = payment,
                principalPart = principalPart,
                interestPart = interest,
                remaining = remaining,
            )
            totalInterest = totalInterest.add(interest, precision)
        }

        val totalPayment = amount.add(totalInterest, precision)
        return MortgageOutcome.Success(
            MortgageResult(
                method = input.method,
                months = months,
                monthlyPayment = equalInstallmentPayment,
                firstPayment = rows.first().payment,
                lastPayment = rows.last().payment,
                monthlyDecrease = equalPrincipalPart?.multiply(monthlyRate, precision),
                totalInterest = totalInterest,
                totalPayment = totalPayment,
                rows = rows,
            ),
        )
    }
}
