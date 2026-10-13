package com.cangshuo.toolbox.feature.stopwatch.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.stopwatch.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class StopwatchError { LAP_LIMIT, NOT_RUNNING, LAP_TOO_SOON }

data class StopwatchUiState(
    val displayMs: Long = 0,
    val running: Boolean = false,
    val started: Boolean = false,
    val laps: List<StopwatchLap> = emptyList(),
    val keepScreenOn: Boolean = true,
    val csvReport: Boolean = false,
    val showAll: Boolean = false,
    val error: StopwatchError? = null,
)

class StopwatchViewModel(private val useCases: StopwatchUseCases, private val saved: SavedStateHandle) : ViewModel() {
    private var session: StopwatchSession = StopwatchPolicy.restore(
        running = saved.get<Boolean>("swRunning") ?: false,
        accumulatedMs = saved.get<Long>("swAccum") ?: 0L,
        startedAtMs = saved.get<Long>("swStarted"),
        laps = StopwatchPolicy.decode(saved.get<ArrayList<String>>("swLaps").orEmpty()),
        nowMs = useCases.now(),
    )
    private val mutable = MutableStateFlow(StopwatchUiState(
        displayMs = session.elapsedMs(useCases.now()),
        running = session.running,
        started = session.elapsedMs(useCases.now()) > 0 || session.laps.isNotEmpty(),
        laps = session.laps,
        keepScreenOn = saved.get<Boolean>("swKeepScreen") ?: true,
        csvReport = saved.get<Boolean>("swCsv") ?: false,
    ))
    val state = mutable.asStateFlow()
    private var foreground = false
    private var ticker: Job? = null

    // The ticker only refreshes the display; every value is recomputed from the monotonic clock.
    fun start() { foreground = true; refresh(); runTicker() }

    fun stop() {
        foreground = false
        ticker?.cancel(); ticker = null
        refresh()
    }

    fun toggleRun() {
        val now = useCases.now()
        session = if (session.running) StopwatchPolicy.pause(session, now) else StopwatchPolicy.start(session, now)
        persist()
        runTicker()
    }

    fun lap() {
        if (!session.running) { mutable.update { it.copy(error = StopwatchError.NOT_RUNNING) }; return }
        val next = StopwatchPolicy.lap(session, useCases.now())
        if (next == null) {
            mutable.update { it.copy(error = if (session.laps.size >= StopwatchPolicy.MAX_LAPS)
                StopwatchError.LAP_LIMIT else StopwatchError.LAP_TOO_SOON) }
            return
        }
        session = next
        persist()
    }

    fun reset() {
        session = StopwatchSession()
        persist()
        runTicker()
    }

    fun setKeepScreenOn(enabled: Boolean) {
        saved["swKeepScreen"] = enabled
        mutable.update { it.copy(keepScreenOn = enabled) }
    }

    fun selectReport(csv: Boolean) {
        saved["swCsv"] = csv
        mutable.update { it.copy(csvReport = csv) }
    }

    fun showAll(enabled: Boolean) { mutable.update { it.copy(showAll = enabled) } }

    private fun refresh() {
        val elapsed = session.elapsedMs(useCases.now())
        mutable.update {
            it.copy(displayMs = elapsed, running = session.running,
                started = elapsed > 0 || session.laps.isNotEmpty(), laps = session.laps)
        }
    }

    private fun persist() {
        mutable.update { it.copy(error = null) }
        saved["swRunning"] = session.running
        saved["swAccum"] = session.accumulatedMs
        saved["swStarted"] = session.startedAtMs
        saved["swLaps"] = StopwatchPolicy.encode(session.laps)
        refresh()
    }

    private fun runTicker() {
        ticker?.cancel(); ticker = null
        if (!foreground || !session.running) return
        ticker = viewModelScope.launch {
            while (isActive && foreground && session.running) {
                refresh()
                delay(StopwatchPolicy.TICK_MS)
            }
        }
    }

    companion object {
        fun factory(useCases: StopwatchUseCases): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                StopwatchViewModel(useCases, extras.createSavedStateHandle()) as T
        }
    }
}
