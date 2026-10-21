package com.cangshuo.toolbox.feature.fps.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.fps.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FpsStorageError { LOAD, SAVE, CLEAR }
data class FpsUiState(val request: FpsRequest = FpsRequest(), val stage: FpsStage = FpsStage.IDLE,
    val result: FpsRecord? = null, val history: List<FpsRecord> = emptyList(),
    val busy: Boolean = false, val error: FpsStorageError? = null, val csv: Boolean = false)

class FpsViewModel(private val cases: FpsUseCases, private val saved: SavedStateHandle) : ViewModel() {
    private val restored = restoreRecord(saved)
    private val request = FpsRequest(saved["fpsSeconds"] ?: 10, saved["fpsTarget"] ?: 0, saved["fpsObjects"] ?: 24)
        .takeIf(FpsPolicy::valid) ?: FpsRequest()
    private val mutable = MutableStateFlow(FpsUiState(request = restored?.request ?: request, result = restored,
        stage = if (restored != null) { if (restored.complete) FpsStage.COMPLETE else FpsStage.STOPPED }
            else if (saved.get<Boolean>("fpsActive") == true || saved.get<Boolean>("fpsInterrupted") == true) FpsStage.INTERRUPTED else FpsStage.IDLE))
    val state = mutable.asStateFlow()
    private var sampling: Job? = null
    private var foreground = false
    private var stored = saved.get<Boolean>("fpsStored") ?: false
    init { load() }

    fun foreground() { foreground = true }
    fun background() {
        foreground = false
        if (mutable.value.stage in ACTIVE) {
            sampling?.cancel(); sampling = null
            mutable.value = mutable.value.copy(stage = FpsStage.INTERRUPTED)
            saved["fpsActive"] = false
            saved["fpsInterrupted"] = true
            // Interrupted results may be inspected but are never labeled as completed or auto-saved.
        }
    }
    fun configure(request: FpsRequest) {
        if (mutable.value.stage != FpsStage.IDLE || mutable.value.busy || !FpsPolicy.valid(request)) return
        mutable.value = mutable.value.copy(request = request)
        saved["fpsSeconds"] = request.seconds; saved["fpsTarget"] = request.targetHz; saved["fpsObjects"] = request.objects
    }
    fun start(displayId: Int) {
        if (!foreground || mutable.value.busy || mutable.value.error != null || mutable.value.stage != FpsStage.IDLE) return
        saved["fpsActive"] = true; saved["fpsInterrupted"] = false; clearRecord(saved); saved["fpsStored"] = false; stored = false
        mutable.value = mutable.value.copy(stage = FpsStage.WAITING, result = null)
        sampling = viewModelScope.launch {
            try {
                cases.observe(mutable.value.request, displayId).collect { update ->
                    if (foreground && mutable.value.stage in ACTIVE) {
                        mutable.value = mutable.value.copy(stage = update.stage, result = update.record)
                        if (update.stage !in ACTIVE) {
                            saved["fpsActive"] = false
                            if (update.stage == FpsStage.COMPLETE && update.record != null) {
                                persistRecord(saved, update.record)
                                save()
                            }
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { saved["fpsActive"] = false; mutable.value = mutable.value.copy(stage = FpsStage.FAILED) }
        }
    }
    fun stop() {
        if (mutable.value.stage !in ACTIVE) return
        sampling?.cancel(); sampling = null; saved["fpsActive"] = false
        val r = mutable.value.result?.copy(complete = false)
        mutable.value = mutable.value.copy(stage = FpsStage.STOPPED, result = r)
        if (r != null) { persistRecord(saved, r); save() }
    }
    fun reset() {
        if (mutable.value.busy || mutable.value.stage in ACTIVE || mutable.value.error == FpsStorageError.SAVE) return
        sampling?.cancel(); sampling = null
        clearRecord(saved); saved["fpsActive"] = false; saved["fpsStored"] = false; stored = false
        saved["fpsInterrupted"] = false
        mutable.value = mutable.value.copy(stage = FpsStage.IDLE, result = null)
    }
    fun select(record: FpsRecord) {
        if (mutable.value.busy || mutable.value.stage in ACTIVE || mutable.value.error != null) return
        persistRecord(saved, record); saved["fpsStored"] = true; stored = true
        saved["fpsSeconds"] = record.request.seconds; saved["fpsTarget"] = record.request.targetHz; saved["fpsObjects"] = record.request.objects
        mutable.value = mutable.value.copy(request = record.request, result = record,
            stage = if (record.complete) FpsStage.COMPLETE else FpsStage.STOPPED)
    }
    fun csv(value: Boolean) { mutable.value = mutable.value.copy(csv = value) }
    fun retry() { when (mutable.value.error) { FpsStorageError.LOAD -> load(); FpsStorageError.SAVE -> save(); FpsStorageError.CLEAR -> clear(); null -> Unit } }
    fun clear() {
        if (mutable.value.stage in ACTIVE || mutable.value.error == FpsStorageError.SAVE) return
        storage(FpsStorageError.CLEAR) { cases.clear() }
    }
    private fun load() = storage(FpsStorageError.LOAD) { cases.history() }
    private fun save() { mutable.value.result?.let { record -> storage(FpsStorageError.SAVE) { cases.save(record) } } }
    private fun storage(error: FpsStorageError, action: suspend () -> List<FpsRecord>) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val records = action()
                mutable.value = mutable.value.copy(history = records, busy = false)
                if (error == FpsStorageError.SAVE) { stored = true; saved["fpsStored"] = true }
                if (error == FpsStorageError.CLEAR && mutable.value.result != null) { stored = true; saved["fpsStored"] = true }
                if (error == FpsStorageError.LOAD && !stored && restored != null) save()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, error = error) }
        }
    }
    companion object {
        val ACTIVE = setOf(FpsStage.WAITING, FpsStage.RUNNING)
        fun factory(cases: FpsUseCases): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = FpsViewModel(cases, extras.createSavedStateHandle()) as T
        }
    }
}

private fun persistRecord(saved: SavedStateHandle, r: FpsRecord) {
    saved["fpsResultId"] = r.id
    saved["fpsResultLongs"] = longArrayOf(r.at, r.elapsedNanos, r.p95Nanos, r.p99Nanos, r.worstNanos, r.estimatedMissed)
    saved["fpsResultInts"] = intArrayOf(r.request.seconds, r.request.targetHz, r.request.objects, r.frames, r.longFrames, r.ignored)
    saved["fpsResultRates"] = doubleArrayOf(r.budgetHz, r.minRefresh, r.maxRefresh)
    saved["fpsResultComplete"] = r.complete
    saved["fpsResultNanos"] = r.windows.map(FpsWindow::nanos).toLongArray()
    saved["fpsResultCounts"] = r.windows.map(FpsWindow::count).toIntArray()
}
private fun clearRecord(saved: SavedStateHandle) {
    listOf("Id", "Longs", "Ints", "Rates", "Complete", "Nanos", "Counts").forEach { saved.remove<Any>("fpsResult$it") }
}
private fun restoreRecord(saved: SavedStateHandle): FpsRecord? = runCatching {
    val id = saved.get<String>("fpsResultId") ?: return null
    val longs = requireNotNull(saved.get<LongArray>("fpsResultLongs")); require(longs.size == 6)
    val ints = requireNotNull(saved.get<IntArray>("fpsResultInts")); require(ints.size == 6)
    val rates = requireNotNull(saved.get<DoubleArray>("fpsResultRates")); require(rates.size == 3)
    val nanos = requireNotNull(saved.get<LongArray>("fpsResultNanos"))
    val counts = requireNotNull(saved.get<IntArray>("fpsResultCounts"))
    require(nanos.size in 1..80 && nanos.size == counts.size)
    FpsRecord(id, longs[0], FpsRequest(ints[0], ints[1], ints[2]), rates[0], longs[1], ints[3], longs[2], longs[3], longs[4],
        ints[4], longs[5], ints[5], rates[1], rates[2], nanos.indices.map { FpsWindow(nanos[it], counts[it]) },
        saved.get<Boolean>("fpsResultComplete") == true).also { require(FpsPolicy.valid(it)) }
}.getOrNull()
