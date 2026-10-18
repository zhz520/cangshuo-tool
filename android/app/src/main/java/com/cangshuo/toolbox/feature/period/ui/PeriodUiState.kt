package com.cangshuo.toolbox.feature.period.ui

import com.cangshuo.toolbox.feature.period.domain.PeriodCalendarDay
import com.cangshuo.toolbox.feature.period.domain.PeriodCycle
import com.cangshuo.toolbox.feature.period.domain.PeriodError
import com.cangshuo.toolbox.feature.period.domain.PeriodFlow
import com.cangshuo.toolbox.feature.period.domain.PeriodInsights
import com.cangshuo.toolbox.feature.period.domain.PeriodMood
import com.cangshuo.toolbox.feature.period.domain.PeriodPolicy
import com.cangshuo.toolbox.feature.period.domain.PeriodSettings
import com.cangshuo.toolbox.feature.period.domain.PeriodSymptom
import java.time.YearMonth

data class PeriodEditorState(
    val cycleId: Long?,
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val flow: PeriodFlow,
    val symptoms: Set<PeriodSymptom> = emptySet(),
    val mood: PeriodMood? = null,
    val notes: String = "",
    val error: PeriodError? = null,
)

data class PeriodSettingsEditor(
    val cycleDays: String,
    val periodDays: String,
    val error: PeriodError? = null,
)

data class PeriodUiState(
    val loading: Boolean = true,
    val todayEpochDay: Long = 0L,
    val visibleMonth: YearMonth = PeriodPolicy.clampMonth(YearMonth.now()),
    val selectedEpochDay: Long? = null,
    val cycles: List<PeriodCycle> = emptyList(),
    val settings: PeriodSettings = PeriodSettings(),
    val insights: PeriodInsights = PeriodPolicy.insights(emptyList(), PeriodSettings(), 0L),
    val calendarDays: List<PeriodCalendarDay> = emptyList(),
    val selectedCycle: PeriodCycle? = null,
    val editor: PeriodEditorState? = null,
    val settingsEditor: PeriodSettingsEditor? = null,
    val saving: Boolean = false,
    val message: PeriodMessage? = null,
    val messageRevision: Int = 0,
)

enum class PeriodMessage {
    SAVED, DELETED, SETTINGS_SAVED, FUTURE_DATE, STORAGE_FAILED,
}
