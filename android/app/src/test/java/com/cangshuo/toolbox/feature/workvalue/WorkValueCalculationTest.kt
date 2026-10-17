package com.cangshuo.toolbox.feature.workvalue

import com.cangshuo.toolbox.feature.workvalue.domain.LocalWorkValueRepository
import com.cangshuo.toolbox.feature.workvalue.domain.WorkValueError
import com.cangshuo.toolbox.feature.workvalue.domain.WorkValueInput
import com.cangshuo.toolbox.feature.workvalue.domain.WorkValuePolicy
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkValueCalculationTest {
    private val repository = LocalWorkValueRepository()

    private fun input(
        salary: String = "10000",
        months: Double = 12.0,
        bonus: String = "0",
        benefits: String = "0",
        cost: String = "0",
        workDays: Double = 5.0,
        officeDays: Double = 5.0,
        dailyHours: Double = 9.0,
        breakHours: Double = 1.0,
        commute: Double = 0.5,
        leaveDays: Double = 10.0,
    ) = WorkValueInput(
        monthlySalary = BigDecimal(salary),
        paidMonths = months,
        annualBonus = BigDecimal(bonus),
        monthlyBenefits = BigDecimal(benefits),
        monthlyJobCost = BigDecimal(cost),
        workDaysPerWeek = workDays,
        officeDaysPerWeek = officeDays,
        dailyAttendanceHours = dailyHours,
        unpaidBreakHours = breakHours,
        commuteHoursOneWay = commute,
        paidNonWorkingDays = leaveDays,
    )

    @Test
    fun standardInputComputesAnnualCashAndRealHourlyValue() {
        val result = repository.calculate(input())
        assertEquals("120000", result.annualCash.toPlainString())
        assertEquals("120000", result.annualNetCash.toPlainString())
        assertEquals(250.0, result.paidWorkDays, 0.000001)
        assertEquals(2000.0, result.workHoursPerYear, 0.000001)
        assertEquals(250.0, result.commuteHoursPerYear, 0.000001)
        assertEquals(2250.0, result.totalWorkHoursPerYear, 0.000001)
        assertEquals(60.0, result.nominalHourly.toDouble(), 0.000001)
        assertEquals(53.3333333333, result.netRealHourly.toDouble(), 0.000001)
        assertEquals(11.1111111111, result.commuteSharePercent, 0.000001)
        assertEquals(112.5, result.minutesPerHundredCash, 0.000001)
    }

    @Test
    fun bonusBenefitsAndCostsChangeAnnualNetCash() {
        val result = repository.calculate(input(months = 13.0, bonus = "12000", benefits = "1000", cost = "500"))
        assertEquals("154000", result.annualCash.toPlainString())
        assertEquals("6000", result.annualJobCost.toPlainString())
        assertEquals("148000", result.annualNetCash.toPlainString())
        assertEquals(148000.0 / 2250.0, result.netRealHourly.toDouble(), 0.000001)
    }

    @Test
    fun remoteWorkRemovesCommuteAndPartialOfficeUsesTheRatio() {
        val remote = repository.calculate(input(officeDays = 0.0))
        assertEquals(0.0, remote.commuteHoursPerYear, 0.0)
        assertEquals(remote.nominalHourly.toDouble(), remote.netRealHourly.toDouble(), 0.000001)

        val hybrid = repository.calculate(input(officeDays = 2.5))
        assertEquals(125.0, hybrid.commuteHoursPerYear, 0.000001)
        assertEquals(2125.0, hybrid.totalWorkHoursPerYear, 0.000001)
    }

    @Test
    fun validationRejectsImpossibleDaysAndHours() {
        assertEquals(WorkValueError.OUT_OF_RANGE, WorkValuePolicy.validate(input(workDays = 0.0)))
        assertEquals(WorkValueError.OUT_OF_RANGE, WorkValuePolicy.validate(input(officeDays = 6.0)))
        assertEquals(WorkValueError.OUT_OF_RANGE, WorkValuePolicy.validate(input(breakHours = 9.0)))
        assertEquals(WorkValueError.NO_WORK_TIME, WorkValuePolicy.validate(input(leaveDays = 365.0)))
        assertEquals(WorkValueError.OUT_OF_RANGE, WorkValuePolicy.validate(input(salary = "-1")))
    }
}
