package com.cangshuo.toolbox.feature.workvalue.ui

import com.cangshuo.toolbox.feature.workvalue.domain.WorkValueError
import com.cangshuo.toolbox.feature.workvalue.domain.WorkValueResult

enum class WorkValueField(val storageKey: String) {
    SALARY("wvSalary"),
    PAID_MONTHS("wvMonths"),
    ANNUAL_BONUS("wvBonus"),
    MONTHLY_BENEFITS("wvBenefits"),
    MONTHLY_JOB_COST("wvCost"),
    WORK_DAYS_PER_WEEK("wvWorkDays"),
    OFFICE_DAYS_PER_WEEK("wvOfficeDays"),
    DAILY_ATTENDANCE_HOURS("wvDailyHours"),
    UNPAID_BREAK_HOURS("wvBreakHours"),
    COMMUTE_HOURS_ONE_WAY("wvCommute"),
    PAID_NON_WORKING_DAYS("wvLeaveDays"),
}

data class WorkValueUiState(
    val values: Map<WorkValueField, String> = emptyMap(),
    val result: WorkValueResult? = null,
    val error: WorkValueError? = null,
)
