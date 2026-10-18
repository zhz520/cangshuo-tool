package com.cangshuo.toolbox.feature.pomodoro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroError
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroPhase
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroPolicy
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroSettings
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroUseCases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PomodoroViewModel(
    private val useCases: PomodoroUseCases,
) : ViewModel() {
    private var timer = useCases.observeTimer().value
    private var settings = useCases.observeSettings().value
    private var history = emptyList<com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroSession>()
    private val mutable = MutableStateFlow(PomodoroUiState(timer = timer, settings = settings))
    val state: StateFlow<PomodoroUiState> = mutable.asStateFlow()
    private var foreground = false
    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            try {
                combine(
                    useCases.observeTimer(),
                    useCases.observeSettings(),
                    useCases.observeHistory(),
                ) { timer, settings, history -> Triple(timer, settings, history) }
                    .collect { (timer, settings, history) ->
                        this@PomodoroViewModel.timer = timer
                        this@PomodoroViewModel.settings = settings
                        this@PomodoroViewModel.history = history
                        publish(loading = false)
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(
                    loading = false,
                    message = PomodoroMessage.STORAGE_FAILED,
                )
            }
        }
    }

    fun start() {
        foreground = true
        runTicker()
    }

    fun stop() {
        foreground = false
        ticker?.cancel()
        ticker = null
    }

    fun toggleTimer() = action {
        if (timer.running) useCases.pause() else useCases.start()
    }

    fun skip() = action(message = PomodoroMessage.SKIPPED) {
        useCases.skip()
    }

    fun reset() = action(message = PomodoroMessage.RESET) {
        useCases.reset()
    }

    fun selectPhase(phase: PomodoroPhase) = action {
        useCases.switchPhase(phase)
    }

    fun setLabel(label: String) {
        val error = PomodoroPolicy.validateLabel(label)
        if (error != null) {
            mutable.value = mutable.value.copy(error = error)
            return
        }
        action {
            useCases.setLabel(PomodoroPolicy.normalizeLabel(label))
            mutable.value = mutable.value.copy(saveRevision = mutable.value.saveRevision + 1)
        }
    }

    fun saveSettings(settings: PomodoroSettings) = action(message = PomodoroMessage.SAVED) {
        useCases.updateSettings(settings)
        mutable.value = mutable.value.copy(saveRevision = mutable.value.saveRevision + 1)
    }

    fun clearHistory() = action(message = PomodoroMessage.HISTORY_CLEARED) {
        useCases.clearHistory()
    }

    fun clearError() {
        if (mutable.value.error != null) mutable.value = mutable.value.copy(error = null)
    }

    fun consumeMessage() {
        if (mutable.value.message != null) mutable.value = mutable.value.copy(message = null)
    }

    private fun action(
        message: PomodoroMessage? = null,
        block: suspend () -> Unit,
    ) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                block()
                timer = useCases.observeTimer().value
                settings = useCases.observeSettings().value
                publish(loading = false, message = message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalArgumentException) {
                mutable.value = mutable.value.copy(busy = false, error = PomodoroError.DURATION)
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(
                    busy = false,
                    message = PomodoroMessage.STORAGE_FAILED,
                )
            }
        }
    }

    private fun runTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive && foreground) {
                try {
                    useCases.tick()
                    timer = useCases.observeTimer().value
                    publish(mutable.value.loading)
                    delay(if (timer.running) PomodoroPolicy.TICK_MILLIS else 30_000L)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutable.value = mutable.value.copy(message = PomodoroMessage.STORAGE_FAILED)
                    delay(30_000L)
                }
            }
        }
    }

    private fun publish(loading: Boolean, message: PomodoroMessage? = null) {
        val today = useCases.todayEpochDay()
        val current = mutable.value
        mutable.value = current.copy(
            timer = timer,
            settings = settings,
            stats = useCases.stats(history),
            history = history,
            todayEpochDay = today,
            loading = loading,
            busy = false,
            error = null,
            message = message ?: current.message,
        )
    }

    companion object {
        fun factory(useCases: PomodoroUseCases): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                PomodoroViewModel(useCases) as T
        }
    }
}
