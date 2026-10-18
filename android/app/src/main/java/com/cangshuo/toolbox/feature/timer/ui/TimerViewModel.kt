package com.cangshuo.toolbox.feature.timer.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.timer.domain.CountdownPolicy
import com.cangshuo.toolbox.feature.timer.domain.CountdownStatus
import com.cangshuo.toolbox.feature.timer.domain.CountdownUseCases
import com.cangshuo.toolbox.feature.timer.domain.TimerDurationInput
import com.cangshuo.toolbox.feature.timer.domain.TimerError
import com.cangshuo.toolbox.feature.timer.domain.TimerValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerViewModel(
    private val useCases: CountdownUseCases,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private var foreground = false
    private var ticker: Job? = null
    private var lastStatus = CountdownStatus.IDLE
    private val initial = useCases.observe().value
    private val initialParts = CountdownPolicy.splitInput(initial.totalMillis)
    private val mutable = MutableStateFlow(
        TimerUiState(
            status = initial.status,
            totalMillis = initial.totalMillis,
            remainingMillis = initial.remainingAt(useCases.nowElapsed()),
            progress = initial.progress(useCases.nowElapsed()),
            finishAtWallMillis = initial.deadlineWallMillis,
            label = saved.get<String>("timerLabel") ?: initial.label,
            hours = saved.get<String>("timerHours") ?: initialParts.first,
            minutes = saved.get<String>("timerMinutes") ?: initialParts.second,
            seconds = saved.get<String>("timerSeconds") ?: initialParts.third,
            keepScreenOn = initial.keepScreenOn,
        ),
    )
    val state: StateFlow<TimerUiState> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                useCases.observe().collect { timer -> onTimerState(timer.status, timer.totalMillis, timer.label, timer.keepScreenOn) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emitMessage(TimerMessage.STORAGE_FAILED)
            }
        }
        refresh()
    }

    fun enterForeground() {
        foreground = true
        if (useCases.observe().value.status in setOf(CountdownStatus.RUNNING, CountdownStatus.PAUSED)) {
            useCases.ensureService()
        }
        refresh()
        startTicker()
    }

    fun leaveForeground() {
        foreground = false
        ticker?.cancel()
        ticker = null
    }

    fun setHours(value: String) = setDurationPart(value, "timerHours") { copy(hours = it) }
    fun setMinutes(value: String) = setDurationPart(value, "timerMinutes") { copy(minutes = it) }
    fun setSeconds(value: String) = setDurationPart(value, "timerSeconds") { copy(seconds = it) }
    fun setLabel(value: String) {
        saved["timerLabel"] = value
        mutable.value = mutable.value.copy(label = value, error = null)
    }

    fun choosePreset(minutes: Int) {
        if (!mutable.value.durationEditable) return
        val millis = minutes * 60_000L
        val (hours, mins, seconds) = CountdownPolicy.splitInput(millis)
        saved["timerHours"] = hours
        saved["timerMinutes"] = mins
        saved["timerSeconds"] = seconds
        mutable.value = mutable.value.copy(hours = hours, minutes = mins, seconds = seconds, error = null)
        useCases.prepare(millis)
        refresh()
    }

    fun start() {
        val current = mutable.value
        if (!current.durationEditable) return
        when (val validation = useCases.validate(durationInput(current))) {
            is TimerValidation.Invalid -> mutable.value = current.copy(error = validation.error)
            is TimerValidation.Valid -> {
                useCases.start(validation.durationMillis, validation.label)
                emitMessage(TimerMessage.STARTED)
                startTicker()
            }
        }
    }

    fun pause() {
        if (!mutable.value.running) return
        useCases.pause()
        refresh()
        if (mutable.value.paused) emitMessage(TimerMessage.PAUSED)
    }

    fun resume() {
        if (!mutable.value.paused) return
        useCases.resume()
        refresh()
        if (mutable.value.running) {
            emitMessage(TimerMessage.RESUMED)
            useCases.ensureService()
            startTicker()
        }
    }

    fun reset() {
        if (mutable.value.status == CountdownStatus.IDLE) return
        useCases.reset()
        mutable.value = mutable.value.copy(error = null)
        emitMessage(TimerMessage.RESET)
        refresh()
    }

    fun extend() {
        if (!mutable.value.running && !mutable.value.paused) return
        val before = useCases.observe().value
        useCases.extend()
        refresh()
        val after = useCases.observe().value
        if (after.status != CountdownStatus.FINISHED && after != before) {
            emitMessage(TimerMessage.EXTENDED)
            useCases.ensureService()
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        useCases.setKeepScreenOn(enabled)
        mutable.value = mutable.value.copy(keepScreenOn = enabled)
    }

    fun refreshNotification() {
        if (mutable.value.running || mutable.value.paused) useCases.ensureService()
    }

    fun refresh() {
        val timer = useCases.observe().value
        val now = useCases.nowElapsed()
        if (timer.status == CountdownStatus.RUNNING && timer.remainingAt(now) == 0L) {
            useCases.finish()
        }
        val latest = useCases.observe().value
        mutable.value = mutable.value.copy(
            loading = false,
            status = latest.status,
            totalMillis = if (latest.status == CountdownStatus.IDLE && mutable.value.durationEditable) {
                previewDuration(mutable.value) ?: latest.totalMillis
            } else latest.totalMillis,
            remainingMillis = latest.remainingAt(now),
            progress = latest.progress(now),
            finishAtWallMillis = latest.deadlineWallMillis,
            label = if (latest.status == CountdownStatus.IDLE) mutable.value.label else latest.label,
            keepScreenOn = latest.keepScreenOn,
        )
        if (latest.status == CountdownStatus.FINISHED && lastStatus != CountdownStatus.FINISHED) {
            emitMessage(TimerMessage.FINISHED)
        }
        lastStatus = latest.status
    }

    fun clearError() {
        if (mutable.value.error != null) mutable.value = mutable.value.copy(error = null)
    }

    fun consumeMessage() {
        if (mutable.value.message != null) mutable.value = mutable.value.copy(message = null)
    }

    private fun onTimerState(status: CountdownStatus, totalMillis: Long, label: String, keepScreenOn: Boolean) {
        val finishedNow = status == CountdownStatus.FINISHED && lastStatus != CountdownStatus.FINISHED
        if (status == CountdownStatus.IDLE && lastStatus != CountdownStatus.IDLE && mutable.value.durationEditable) {
            val restored = CountdownPolicy.splitInput(totalMillis)
            saved["timerHours"] = restored.first
            saved["timerMinutes"] = restored.second
            saved["timerSeconds"] = restored.third
            mutable.value = mutable.value.copy(hours = restored.first, minutes = restored.second, seconds = restored.third)
        }
        lastStatus = status
        mutable.value = mutable.value.copy(
            status = status,
            totalMillis = totalMillis,
            label = if (status == CountdownStatus.IDLE && mutable.value.durationEditable) mutable.value.label else label,
            keepScreenOn = keepScreenOn,
            finishAtWallMillis = useCases.observe().value.deadlineWallMillis,
        )
        if (finishedNow) emitMessage(TimerMessage.FINISHED)
        refresh()
        if (status == CountdownStatus.RUNNING) startTicker()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive && foreground && mutable.value.running) {
                refresh()
                delay(100L)
            }
        }
    }

    private fun setDurationPart(value: String, key: String, transform: TimerUiState.(String) -> TimerUiState) {
        if (!mutable.value.durationEditable) return
        val normalized = value.filter(Char::isDigit).take(2)
        saved[key] = normalized
        mutable.value = mutable.value.transform(normalized).copy(error = null)
        previewDuration(mutable.value)?.takeIf {
            it in CountdownPolicy.MIN_DURATION_MILLIS..CountdownPolicy.MAX_DURATION_MILLIS
        }?.let(useCases::prepare)
    }

    private fun previewDuration(state: TimerUiState): Long? {
        val hours = state.hours.toIntOrNull() ?: return null
        val minutes = state.minutes.toIntOrNull() ?: return null
        val seconds = state.seconds.toIntOrNull() ?: return null
        return (hours * 3_600L + minutes * 60L + seconds) * 1_000L
    }

    private fun durationInput(state: TimerUiState) = TimerDurationInput(
        hours = state.hours,
        minutes = state.minutes,
        seconds = state.seconds,
        label = state.label,
    )

    private fun emitMessage(message: TimerMessage) {
        mutable.value = mutable.value.copy(
            message = message,
            messageRevision = mutable.value.messageRevision + 1,
        )
    }

    companion object {
        fun factory(useCases: CountdownUseCases): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                TimerViewModel(useCases, extras.createSavedStateHandle()) as T
        }
    }
}
