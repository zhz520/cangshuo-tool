package com.cangshuo.toolbox.feature.workvalue.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class WorkValueInput(
    val monthlySalary: BigDecimal,
    val paidMonths: Double,
    val annualBonus: BigDecimal,
    val monthlyBenefits: BigDecimal,
    val monthlyJobCost: BigDecimal,
    val workDaysPerWeek: Double,
    val officeDaysPerWeek: Double,
    val dailyAttendanceHours: Double,
    val unpaidBreakHours: Double,
    val commuteHoursOneWay: Double,
    val paidNonWorkingDays: Double,
)

data class WorkValueResult(
    val annualCash: BigDecimal,
    val annualNetCash: BigDecimal,
    val annualJobCost: BigDecimal,
    val paidWorkDays: Double,
    val workHoursPerYear: Double,
    val commuteHoursPerYear: Double,
    val totalWorkHoursPerYear: Double,
    val nominalHourly: BigDecimal,
    val netRealHourly: BigDecimal,
    val commuteSharePercent: Double,
    val minutesPerHundredCash: Double,
)

enum class WorkValueError { INVALID, OUT_OF_RANGE, NO_WORK_TIME }

enum class WorkPreset { STANDARD, SINGLE_DAY_OFF, BIG_SMALL_WEEK, REMOTE }

interface WorkValueRepository {
    fun calculate(input: WorkValueInput): WorkValueResult
}

class CalculateWorkValueUseCase(private val repository: WorkValueRepository) {
    fun calculate(input: WorkValueInput): WorkValueResult = repository.calculate(input)
}

object WorkValuePolicy {
    const val MAX_INPUT_LENGTH = 18
    const val WEEKS_PER_YEAR = 52.0

    fun validate(input: WorkValueInput): WorkValueError? {
        val money = listOf(input.annualBonus, input.monthlyBenefits, input.monthlyJobCost)
        if (input.monthlySalary <= BigDecimal.ZERO || input.monthlySalary > MAX_MONEY) return WorkValueError.OUT_OF_RANGE
        if (money.any { it < BigDecimal.ZERO || it > MAX_MONEY }) return WorkValueError.OUT_OF_RANGE
        if (input.paidMonths !in 0.0..24.0) return WorkValueError.OUT_OF_RANGE
        if (input.workDaysPerWeek !in 1.0..7.0) return WorkValueError.OUT_OF_RANGE
        if (input.officeDaysPerWeek !in 0.0..input.workDaysPerWeek) return WorkValueError.OUT_OF_RANGE
        if (input.dailyAttendanceHours !in 0.5..24.0) return WorkValueError.OUT_OF_RANGE
        if (input.unpaidBreakHours < 0.0 || input.unpaidBreakHours >= input.dailyAttendanceHours) {
            return WorkValueError.OUT_OF_RANGE
        }
        if (input.commuteHoursOneWay !in 0.0..12.0) return WorkValueError.OUT_OF_RANGE
        if (input.paidNonWorkingDays !in 0.0..365.0) return WorkValueError.OUT_OF_RANGE
        val paidDays = scheduledDays(input.workDaysPerWeek) - input.paidNonWorkingDays
        if (paidDays <= 0.0) return WorkValueError.NO_WORK_TIME
        if (input.dailyAttendanceHours - input.unpaidBreakHours <= 0.0) return WorkValueError.NO_WORK_TIME
        return null
    }

    fun scheduledDays(workDaysPerWeek: Double): Double = WEEKS_PER_YEAR * workDaysPerWeek

    private val MAX_MONEY = BigDecimal("1000000000000")
}

class LocalWorkValueRepository : WorkValueRepository {
    override fun calculate(input: WorkValueInput): WorkValueResult {
        WorkValuePolicy.validate(input)?.let { throw IllegalArgumentException(it.name) }
        val paidDays = WorkValuePolicy.scheduledDays(input.workDaysPerWeek) - input.paidNonWorkingDays
        val annualCash = input.monthlySalary
            .multiply(BigDecimal.valueOf(input.paidMonths).stripTrailingZeros())
            .add(input.annualBonus)
            .add(input.monthlyBenefits.multiply(BigDecimal.valueOf(12)))
        val annualCost = input.monthlyJobCost.multiply(BigDecimal.valueOf(12))
        val annualNet = annualCash.subtract(annualCost)
        val productiveHoursPerDay = input.dailyAttendanceHours - input.unpaidBreakHours
        val workHours = paidDays * productiveHoursPerDay
        val officeRatio = input.officeDaysPerWeek / input.workDaysPerWeek
        val commuteHours = paidDays * officeRatio * input.commuteHoursOneWay * 2
        val totalHours = workHours + commuteHours
        val nominal = divideMoney(annualCash, workHours)
        val real = divideMoney(annualNet, totalHours)
        return WorkValueResult(
            annualCash = annualCash,
            annualNetCash = annualNet,
            annualJobCost = annualCost,
            paidWorkDays = paidDays,
            workHoursPerYear = workHours,
            commuteHoursPerYear = commuteHours,
            totalWorkHoursPerYear = totalHours,
            nominalHourly = nominal,
            netRealHourly = real,
            commuteSharePercent = if (totalHours > 0) commuteHours / totalHours * 100 else 0.0,
            minutesPerHundredCash = if (annualCash > BigDecimal.ZERO) totalHours * 100 * 60 / annualCash.toDouble() else 0.0,
        )
    }

    private fun divideMoney(value: BigDecimal, hours: Double): BigDecimal =
        if (hours <= 0) BigDecimal.ZERO
        else value.divide(BigDecimal.valueOf(hours), MathContext(12, RoundingMode.HALF_UP))
}
