package com.cangshuo.toolbox.feature.workvalue.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.core.model.parseDecimalAmount
import com.cangshuo.toolbox.core.model.parseDecimalText
import com.cangshuo.toolbox.feature.workvalue.domain.CalculateWorkValueUseCase
import com.cangshuo.toolbox.feature.workvalue.domain.WorkPreset
import com.cangshuo.toolbox.feature.workvalue.domain.WorkValueError
import com.cangshuo.toolbox.feature.workvalue.domain.WorkValueInput
import com.cangshuo.toolbox.feature.workvalue.domain.WorkValuePolicy
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WorkValueViewModel(
    private val calculate: CalculateWorkValueUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mutable = MutableStateFlow(WorkValueUiState())
    val state: StateFlow<WorkValueUiState> = mutable.asStateFlow()

    init {
        rebuild()
    }

    fun update(field: WorkValueField, value: String) {
        if (value.length > WorkValuePolicy.MAX_INPUT_LENGTH) return
        savedStateHandle[field.storageKey] = value
        rebuild()
    }

    fun applyPreset(preset: WorkPreset) {
        val values = when (preset) {
            WorkPreset.STANDARD -> listOf("5", "5", "9", "1", "0.5")
            WorkPreset.SINGLE_DAY_OFF -> listOf("6", "6", "9", "1", "0.5")
            WorkPreset.BIG_SMALL_WEEK -> listOf("5.5", "5.5", "9", "1", "0.5")
            WorkPreset.REMOTE -> listOf("5", "0", "9", "1", "0")
        }
        WorkValueField.entries
            .filter {
                it in setOf(
                    WorkValueField.WORK_DAYS_PER_WEEK,
                    WorkValueField.OFFICE_DAYS_PER_WEEK,
                    WorkValueField.DAILY_ATTENDANCE_HOURS,
                    WorkValueField.UNPAID_BREAK_HOURS,
                    WorkValueField.COMMUTE_HOURS_ONE_WAY,
                )
            }
            .zip(values)
            .forEach { (field, value) -> savedStateHandle[field.storageKey] = value }
        rebuild()
    }

    fun clear() {
        defaults().forEach { (field, value) -> savedStateHandle[field.storageKey] = value }
        rebuild()
    }

    private fun rebuild() {
        val values = WorkValueField.entries.associateWith { field ->
            savedStateHandle.get<String>(field.storageKey) ?: defaults()[field].orEmpty()
        }
        if (values.getValue(WorkValueField.SALARY).isBlank()) {
            mutable.value = WorkValueUiState(values)
            return
        }
        val input = parseInput(values)
        if (input == null) {
            mutable.value = WorkValueUiState(values, error = WorkValueError.INVALID)
            return
        }
        val validation = WorkValuePolicy.validate(input)
        if (validation != null) {
            mutable.value = WorkValueUiState(values, error = validation)
            return
        }
        mutable.value = runCatching { WorkValueUiState(values, calculate.calculate(input)) }
            .getOrElse { WorkValueUiState(values, error = WorkValueError.INVALID) }
    }

    private fun parseInput(values: Map<WorkValueField, String>): WorkValueInput? {
        val salary = parseDecimalAmount(values.getValue(WorkValueField.SALARY), WorkValuePolicy.MAX_INPUT_LENGTH)
            ?: return null
        val months = parseDecimalText(values.getValue(WorkValueField.PAID_MONTHS), WorkValuePolicy.MAX_INPUT_LENGTH)
            ?: return null
        val bonus = optionalMoney(values, WorkValueField.ANNUAL_BONUS) ?: return null
        val benefits = optionalMoney(values, WorkValueField.MONTHLY_BENEFITS) ?: return null
        val cost = optionalMoney(values, WorkValueField.MONTHLY_JOB_COST) ?: return null
        val workDays = parseDecimalText(values.getValue(WorkValueField.WORK_DAYS_PER_WEEK), WorkValuePolicy.MAX_INPUT_LENGTH)
            ?: return null
        val officeDays = parseDecimalText(values.getValue(WorkValueField.OFFICE_DAYS_PER_WEEK), WorkValuePolicy.MAX_INPUT_LENGTH)
            ?: return null
        val dailyHours = parseDecimalText(values.getValue(WorkValueField.DAILY_ATTENDANCE_HOURS), WorkValuePolicy.MAX_INPUT_LENGTH)
            ?: return null
        val breakHours = parseDecimalText(values.getValue(WorkValueField.UNPAID_BREAK_HOURS), WorkValuePolicy.MAX_INPUT_LENGTH)
            ?: return null
        val commute = parseDecimalText(values.getValue(WorkValueField.COMMUTE_HOURS_ONE_WAY), WorkValuePolicy.MAX_INPUT_LENGTH)
            ?: return null
        val leaveDays = parseDecimalText(values.getValue(WorkValueField.PAID_NON_WORKING_DAYS), WorkValuePolicy.MAX_INPUT_LENGTH)
            ?: return null
        return WorkValueInput(
            monthlySalary = salary,
            paidMonths = months,
            annualBonus = bonus,
            monthlyBenefits = benefits,
            monthlyJobCost = cost,
            workDaysPerWeek = workDays,
            officeDaysPerWeek = officeDays,
            dailyAttendanceHours = dailyHours,
            unpaidBreakHours = breakHours,
            commuteHoursOneWay = commute,
            paidNonWorkingDays = leaveDays,
        )
    }

    private fun optionalMoney(values: Map<WorkValueField, String>, field: WorkValueField): BigDecimal? {
        val raw = values.getValue(field)
        return if (raw.isBlank()) BigDecimal.ZERO
        else parseDecimalAmount(raw, WorkValuePolicy.MAX_INPUT_LENGTH)
    }

    private fun defaults(): Map<WorkValueField, String> = mapOf(
        WorkValueField.SALARY to "",
        WorkValueField.PAID_MONTHS to "12",
        WorkValueField.ANNUAL_BONUS to "0",
        WorkValueField.MONTHLY_BENEFITS to "0",
        WorkValueField.MONTHLY_JOB_COST to "0",
        WorkValueField.WORK_DAYS_PER_WEEK to "5",
        WorkValueField.OFFICE_DAYS_PER_WEEK to "5",
        WorkValueField.DAILY_ATTENDANCE_HOURS to "9",
        WorkValueField.UNPAID_BREAK_HOURS to "1",
        WorkValueField.COMMUTE_HOURS_ONE_WAY to "0.5",
        WorkValueField.PAID_NON_WORKING_DAYS to "10",
    )

    companion object {
        fun factory(calculate: CalculateWorkValueUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    WorkValueViewModel(calculate, extras.createSavedStateHandle()) as T
            }
    }
}
