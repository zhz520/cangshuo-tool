package com.cangshuo.toolbox.feature.tapspeed.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.tapspeed.domain.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TapStorageError { LOAD, SAVE, CLEAR }
data class TapUiState(val session: TapSession = TapSession(), val history: List<TapRecord> = emptyList(),
    val busy: Boolean = false, val error: TapStorageError? = null, val csv: Boolean = false)

class TapViewModel(private val cases: TapUseCases, private val saved: SavedStateHandle) : ViewModel() {
    private var id = saved.get<String>("tapId") ?: UUID.randomUUID().toString()
    private var completedAt = saved.get<Long>("tapAt") ?: 0L
    private var stored = saved.get<Boolean>("tapStored") ?: false
    private val seconds = saved.get<Int>("tapSeconds")?.takeIf { it in TapPolicy.durations } ?: 10
    private val bins = saved.get<IntArray>("tapBins")?.toList()?.takeIf { TapPolicy.validBins(seconds, it) } ?: List(seconds) { 0 }
    private val mutable = MutableStateFlow(TapUiState(TapSession(
        stage = when { saved.get<Boolean>("tapComplete") == true && bins.sum() > 0 -> TapStage.COMPLETE
            saved.get<Boolean>("tapStarted") == true -> TapStage.INTERRUPTED; else -> TapStage.IDLE },
        seconds = seconds, multiTouch = saved.get<Boolean>("tapMulti") ?: false, bins = bins,
        elapsedMs = (saved.get<Long>("tapElapsed") ?: 0).coerceIn(0, seconds * 1000L),
        sources = (saved.get<Int>("tapSources") ?: 0).coerceIn(0, 7),
    )))
    val state = mutable.asStateFlow()
    private var foreground = false
    private var ticker: Job? = null
    private var armedAt = 0L

    init { loadHistory() }
    fun foreground() { foreground = true }
    fun background() {
        foreground = false
        settle()
        interrupt()
    }
    fun interrupt() {
        ticker?.cancel(); ticker = null
        if (mutable.value.session.stage in ACTIVE) update(mutable.value.session.copy(stage = TapStage.INTERRUPTED))
    }
    fun selectSeconds(value: Int) {
        if (value in TapPolicy.durations && mutable.value.session.stage == TapStage.IDLE && !mutable.value.busy)
            update(mutable.value.session.copy(seconds = value, bins = List(value) { 0 }))
    }
    fun selectMulti(enabled: Boolean) {
        if (mutable.value.session.stage == TapStage.IDLE && !mutable.value.busy) update(mutable.value.session.copy(multiTouch = enabled))
    }
    fun arm() {
        if (!foreground || mutable.value.busy || mutable.value.error != null || mutable.value.session.stage != TapStage.IDLE) return
        armedAt = cases.now()
        update(mutable.value.session.copy(stage = TapStage.ARMED))
    }
    fun tap(eventTime: Long = cases.now(), source: Int = TapPolicy.TOUCH) {
        if (!foreground || eventTime < armedAt) return
        val current = mutable.value.session
        val next = TapPolicy.hit(current, eventTime, cases.now(), source)
        update(next)
        if (next.stage == TapStage.COMPLETE && current.stage != TapStage.COMPLETE) finish()
        else if (next.stage == TapStage.RUNNING && current.stage == TapStage.ARMED) {
            ticker = viewModelScope.launch { while (isActive && foreground && mutable.value.session.stage == TapStage.RUNNING) { delay(50); settle() } }
        }
    }
    private fun settle() {
        val current = mutable.value.session
        val next = TapPolicy.advance(current, cases.now())
        if (current != next) update(next)
        if (current.stage == TapStage.RUNNING && next.stage == TapStage.COMPLETE) finish()
    }
    private fun finish() {
        completedAt = cases.wall(); saved["tapAt"] = completedAt
        saveResult()
    }
    fun restart() {
        if (mutable.value.busy || mutable.value.error == TapStorageError.SAVE) return
        ticker?.cancel(); ticker = null
        id = UUID.randomUUID().toString(); completedAt = 0; stored = false; saved["tapStored"] = false
        update(TapSession(seconds = mutable.value.session.seconds, multiTouch = mutable.value.session.multiTouch))
    }
    fun csv(enabled: Boolean) { mutable.value = mutable.value.copy(csv = enabled) }
    private fun update(session: TapSession) {
        mutable.value = mutable.value.copy(session = session)
        saved["tapId"] = id; saved["tapSeconds"] = session.seconds; saved["tapMulti"] = session.multiTouch
        saved["tapBins"] = session.bins.toIntArray(); saved["tapElapsed"] = session.elapsedMs; saved["tapSources"] = session.sources
        saved["tapComplete"] = session.stage == TapStage.COMPLETE; saved["tapStarted"] = session.stage != TapStage.IDLE
    }
    fun retry() { when (mutable.value.error) {
        TapStorageError.SAVE -> saveResult(); TapStorageError.LOAD -> loadHistory(); TapStorageError.CLEAR -> clearHistory(); null -> Unit
    } }
    private fun loadHistory() = storage(TapStorageError.LOAD) { cases.history() }
    private fun saveResult() {
        val s = mutable.value.session
        if (s.stage != TapStage.COMPLETE) return
        if (completedAt <= 0) { completedAt = cases.wall(); saved["tapAt"] = completedAt }
        storage(TapStorageError.SAVE) { cases.save(TapRecord(id, completedAt, s.seconds, s.multiTouch, s.bins, s.sources)) }
    }
    fun clearHistory() {
        if (mutable.value.session.stage in ACTIVE || mutable.value.error == TapStorageError.SAVE) return
        storage(TapStorageError.CLEAR) { cases.clear() }
    }
    private fun storage(error: TapStorageError, action: suspend () -> List<TapRecord>) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val records = action()
                mutable.value = mutable.value.copy(history = records, busy = false)
                if (error == TapStorageError.SAVE) { stored = true; saved["tapStored"] = true }
                if (error == TapStorageError.LOAD && !stored && mutable.value.session.stage == TapStage.COMPLETE) saveResult()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, error = error) }
        }
    }
    companion object {
        val ACTIVE = setOf(TapStage.ARMED, TapStage.RUNNING)
        fun factory(cases: TapUseCases): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = TapViewModel(cases, extras.createSavedStateHandle()) as T
        }
    }
}
