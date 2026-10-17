package com.cangshuo.toolbox.feature.timescreen.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.timescreen.domain.DriftOffset
import com.cangshuo.toolbox.feature.timescreen.domain.ReadTimeScreenUseCase
import com.cangshuo.toolbox.feature.timescreen.domain.TimeScreenPolicy
import com.cangshuo.toolbox.feature.timescreen.domain.TimeScreenSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimeScreenViewModel(
    private val readTime: ReadTimeScreenUseCase,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private var settings = restoreSettings()
    private val initial = readTime.read(settings)
    private val mutable = MutableStateFlow(
        TimeScreenUiState(settings, initial, TimeScreenPolicy.driftOffset(initial.now.toEpochSecond(), settings.driftRadiusDp)),
    )
    val state: StateFlow<TimeScreenUiState> = mutable.asStateFlow()

    private var foreground = false
    private var ticker: Job? = null

    fun start() {
        foreground = true
        refresh()
        runTicker()
    }

    fun stop() {
        foreground = false
        ticker?.cancel()
        ticker = null
    }

    fun setUse24Hour(enabled: Boolean) = update(settings.copy(use24Hour = enabled))

    fun setShowSeconds(enabled: Boolean) = update(settings.copy(showSeconds = enabled))

    fun setShowDate(enabled: Boolean) = update(settings.copy(showDate = enabled))

    fun setShowZone(enabled: Boolean) = update(settings.copy(showZone = enabled))

    fun setAntiBurnIn(enabled: Boolean) = update(settings.copy(antiBurnIn = enabled))

    fun setDriftRadius(value: Int) = update(settings.copy(driftRadiusDp = value))

    fun setKeepScreenOn(enabled: Boolean) = update(settings.copy(keepScreenOn = enabled))

    fun reset() = update(TimeScreenSettings())

    private fun update(next: TimeScreenSettings) {
        settings = TimeScreenPolicy.normalize(next)
        persist()
        refresh()
        runTicker()
    }

    private fun refresh() {
        val snapshot = readTime.read(settings)
        val drift = if (settings.antiBurnIn) {
            TimeScreenPolicy.driftOffset(snapshot.now.toEpochSecond(), settings.driftRadiusDp)
        } else {
            DriftOffset(0f, 0f)
        }
        mutable.value = TimeScreenUiState(settings, snapshot, drift)
    }

    private fun runTicker() {
        ticker?.cancel()
        ticker = null
        if (!foreground) return
        ticker = viewModelScope.launch {
            while (isActive && foreground) {
                delay(readTime.nextDelayMillis(settings.showSeconds))
                refresh()
            }
        }
    }

    private fun restoreSettings(): TimeScreenSettings = TimeScreenPolicy.normalize(
        TimeScreenSettings(
            use24Hour = saved.get<Boolean>("ts24Hour") ?: true,
            showSeconds = saved.get<Boolean>("tsSeconds") ?: true,
            showDate = saved.get<Boolean>("tsDate") ?: true,
            showZone = saved.get<Boolean>("tsZone") ?: true,
            antiBurnIn = saved.get<Boolean>("tsBurnIn") ?: true,
            driftRadiusDp = (saved.get<Int>("tsDrift") ?: 8)
                .coerceIn(TimeScreenPolicy.MIN_DRIFT_RADIUS, TimeScreenPolicy.MAX_DRIFT_RADIUS),
            keepScreenOn = saved.get<Boolean>("tsKeepScreen") ?: true,
        ),
    )

    private fun persist() {
        saved["ts24Hour"] = settings.use24Hour
        saved["tsSeconds"] = settings.showSeconds
        saved["tsDate"] = settings.showDate
        saved["tsZone"] = settings.showZone
        saved["tsBurnIn"] = settings.antiBurnIn
        saved["tsDrift"] = settings.driftRadiusDp
        saved["tsKeepScreen"] = settings.keepScreenOn
    }

    companion object {
        fun factory(readTime: ReadTimeScreenUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    TimeScreenViewModel(readTime, extras.createSavedStateHandle()) as T
            }
    }
}
