package com.cangshuo.toolbox.feature.fish.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.fish.domain.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FishUiState(val stored: FishState? = null, val audioReady: Boolean = false,
    val preparing: Boolean = true, val audioError: Boolean = false, val autoEnabled: Boolean = false,
    val autoRemainingMs: Long = 0, val pulse: Long = 0, val newAchievements: List<FishAchievement> = emptyList(),
    val limitReached: Boolean = false)

class FishViewModel(private val cases: FishUseCases, private val saved: SavedStateHandle,
    private val today: () -> String = { LocalDate.now().toString() }) : ViewModel() {
    private val mutable = MutableStateFlow(FishUiState())
    val state = mutable.asStateFlow()
    private var foreground = false
    private var autoJob: Job? = null
    private var autoGeneration = 0L

    init {
        mutable.value = mutable.value.copy(stored = cases.roll(today()))
        viewModelScope.launch {
            val ready = runCatching { cases.prepare() }.getOrDefault(false)
            mutable.value = mutable.value.copy(preparing = false, audioReady = ready, audioError = !ready)
        }
    }
    fun foreground() {
        foreground = true
        val rolled = cases.roll(today())
        mutable.value = mutable.value.copy(stored = rolled,
            limitReached = rolled.total >= FishPolicy.MAX_TOTAL || rolled.today >= FishPolicy.MAX_TODAY)
    }
    fun background() { foreground = false; stopAuto(); cases.flush() }
    fun tap() {
        if (!foreground || mutable.value.stored == null || mutable.value.limitReached) return
        val before = cases.roll(today())
        val after = try { cases.tap(today()) } catch (_: IllegalArgumentException) {
            mutable.value = mutable.value.copy(limitReached = true, autoEnabled = false); stopAuto(); return
        }
        val unlocked = FishPolicy.newlyUnlocked(before, after)
        cases.feedback(after.settings.sound, after.settings.haptic)
        mutable.value = mutable.value.copy(stored = after, pulse = mutable.value.pulse + 1,
            newAchievements = unlocked.ifEmpty { mutable.value.newAchievements })
    }
    fun dismissAchievements() { mutable.value = mutable.value.copy(newAchievements = emptyList()) }
    fun setSound(enabled: Boolean) = settings { it.copy(sound = enabled) }
    fun setHaptic(enabled: Boolean) = settings { it.copy(haptic = enabled) }
    fun setMotion(enabled: Boolean) = settings { it.copy(motion = enabled) }
    fun setGoal(goal: Int) { if (goal in FishPolicy.goals) settings { it.copy(goal = goal) }; mutable.value = mutable.value.copy(limitReached = false) }
    fun setInterval(ms: Long) { if (ms in FishPolicy.intervals && !mutable.value.autoEnabled) settings { it.copy(autoIntervalMs = ms) } }
    private fun settings(change: (FishSettings) -> FishSettings) {
        val state = mutable.value.stored ?: return
        mutable.value = mutable.value.copy(stored = cases.settings(change(state.settings)))
    }
    fun toggleAuto() {
        if (mutable.value.autoEnabled) stopAuto() else startAuto()
    }
    private fun startAuto() {
        val current = mutable.value.stored ?: return
        if (!foreground || current.settings.autoIntervalMs !in FishPolicy.intervals) return
        autoGeneration++
        val token = autoGeneration
        val interval = current.settings.autoIntervalMs
        val maxTicks = (FishPolicy.AUTO_MAX_MS / interval).toInt()
        mutable.value = mutable.value.copy(autoEnabled = true, autoRemainingMs = FishPolicy.AUTO_MAX_MS, limitReached = false)
        autoJob = viewModelScope.launch {
            repeat(maxTicks) { index ->
                delay(interval)
                if (!foreground || token != autoGeneration || !mutable.value.autoEnabled) return@launch
                tap()
                mutable.value = mutable.value.copy(autoRemainingMs = (FishPolicy.AUTO_MAX_MS - interval * (index + 1)).coerceAtLeast(0))
            }
            if (token == autoGeneration) stopAuto()
        }
    }
    private fun stopAuto() {
        autoGeneration++
        autoJob?.cancel(); autoJob = null
        mutable.value = mutable.value.copy(autoEnabled = false, autoRemainingMs = 0)
    }
    fun resetAll() {
        stopAuto(); mutable.value = mutable.value.copy(stored = cases.reset(), newAchievements = emptyList(), pulse = 0, limitReached = false)
    }
    fun flush() { cases.flush() }
    override fun onCleared() { autoJob?.cancel(); cases.flush(); cases.release(); super.onCleared() }

    companion object {
        fun factory(cases: FishUseCases): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                FishViewModel(cases, extras.createSavedStateHandle()) as T
        }
    }
}
