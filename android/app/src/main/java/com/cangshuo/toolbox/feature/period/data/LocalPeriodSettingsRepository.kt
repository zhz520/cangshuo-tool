package com.cangshuo.toolbox.feature.period.data

import android.content.Context
import androidx.core.content.edit
import com.cangshuo.toolbox.feature.period.domain.PeriodPolicy
import com.cangshuo.toolbox.feature.period.domain.PeriodSettings
import com.cangshuo.toolbox.feature.period.domain.PeriodSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocalPeriodSettingsRepository(context: Context) : PeriodSettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    override val settings: StateFlow<PeriodSettings> = mutable.asStateFlow()

    override fun update(settings: PeriodSettings) {
        val normalized = settings.copy(
            cycleDays = settings.cycleDays.coerceIn(PeriodPolicy.MIN_CYCLE_DAYS, PeriodPolicy.MAX_CYCLE_DAYS),
            periodDays = settings.periodDays.coerceIn(PeriodPolicy.MIN_PERIOD_DAYS, PeriodPolicy.MAX_PERIOD_DAYS),
        )
        preferences.edit {
            putInt(KEY_CYCLE, normalized.cycleDays)
            putInt(KEY_PERIOD, normalized.periodDays)
        }
        mutable.value = normalized
    }

    private fun read() = PeriodSettings(
        cycleDays = preferences.getInt(KEY_CYCLE, PeriodSettings.DEFAULT_CYCLE_DAYS)
            .takeIf { it in PeriodPolicy.MIN_CYCLE_DAYS..PeriodPolicy.MAX_CYCLE_DAYS }
            ?: PeriodSettings.DEFAULT_CYCLE_DAYS,
        periodDays = preferences.getInt(KEY_PERIOD, PeriodSettings.DEFAULT_PERIOD_DAYS)
            .takeIf { it in PeriodPolicy.MIN_PERIOD_DAYS..PeriodPolicy.MAX_PERIOD_DAYS }
            ?: PeriodSettings.DEFAULT_PERIOD_DAYS,
    )

    private companion object {
        const val PREFERENCES = "period_tracker_v1"
        const val KEY_CYCLE = "cycle_days"
        const val KEY_PERIOD = "period_days"
    }
}
