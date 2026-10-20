package com.cangshuo.toolbox.feature.vibrator.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.vibrator.domain.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VibrationStorageError { LOAD, SAVE, CLEAR }
data class VibratorUiState(val capability: DeviceVibrationCapability? = null, val pattern: VibrationPattern = VibrationPattern(),
    val phase: VibrationPhase = VibrationPhase.IDLE, val busy: Boolean = false, val history: List<VibrationRecord> = emptyList(),
    val error: VibrationStorageError? = null, val hardwareError: Boolean = false, val csv: Boolean = false,
    val waveform: VibrationWaveform? = null)

class VibratorViewModel(private val cases: VibrationUseCases, private val saved: SavedStateHandle) : ViewModel() {
    private val mutable = MutableStateFlow(VibratorUiState(
        pattern = VibrationPattern(
            preset = saved.get<String>("vibPreset")?.let { runCatching { VibrationPreset.valueOf(it) }.getOrNull() } ?: VibrationPreset.SINGLE,
            durationMs = (saved.get<Int>("vibDuration") ?: 200).coerceIn(VibrationPattern.MIN_DURATION, VibrationPattern.MAX_DURATION),
            strengthPercent = (saved.get<Int>("vibStrength") ?: 70).coerceIn(VibrationPattern.MIN_STRENGTH, VibrationPattern.MAX_STRENGTH)),
        phase = if (saved.get<Boolean>("vibActive") == true) VibrationPhase.INTERRUPTED else VibrationPhase.IDLE))
    val state = mutable.asStateFlow()
    private var activeStartedAt = 0L
    private var activeWaveform: VibrationWaveform? = null
    private var activeToken = ""
    private var activeRecordId = ""
    private var ticker: Job? = null
    private var foreground = false
    private var completedAt = 0L
    private var completedRecord: VibrationRecord? = null

    init {
        val cap = runCatching { cases.capability() }.getOrElse { DeviceVibrationCapability(false, false) }
        mutable.value = mutable.value.copy(capability = cap, waveform = runCatching { VibrationPolicy.waveform(mutable.value.pattern) }.getOrNull())
        if (saved.get<Boolean>("vibActive") == true) { cases.cancel(); saved["vibActive"] = false }
        loadHistory()
    }
    fun foreground() { foreground = true }
    fun background() { foreground = false; cancelRound() }
    fun selectPreset(preset: VibrationPreset) { if (!mutable.value.busy && mutable.value.phase != VibrationPhase.PLAYING) update(mutable.value.pattern.copy(preset = preset)) }
    fun duration(ms: Int) { if (mutable.value.busy || mutable.value.phase == VibrationPhase.PLAYING) return; update(mutable.value.pattern.copy(durationMs = ms.coerceIn(VibrationPattern.MIN_DURATION, VibrationPattern.MAX_DURATION))) }
    fun strength(percent: Int) { if (mutable.value.busy || mutable.value.phase == VibrationPhase.PLAYING) return; update(mutable.value.pattern.copy(strengthPercent = percent.coerceIn(VibrationPattern.MIN_STRENGTH, VibrationPattern.MAX_STRENGTH))) }
    fun play() {
        val cap = mutable.value.capability ?: return
        if (!foreground || mutable.value.busy || mutable.value.error == VibrationStorageError.SAVE ||
            mutable.value.phase == VibrationPhase.PLAYING || !cap.hasVibrator) return
        val waveform = runCatching { VibrationPolicy.waveform(mutable.value.pattern).let {
            if (cap.amplitudeControl) it else VibrationPolicy.noAmplitudeWaveform(mutable.value.pattern) } }.getOrNull()
            ?: run { mutable.value = mutable.value.copy(hardwareError = true); return }
        mutable.value = mutable.value.copy(busy = true, hardwareError = false)
        try { cases.play(waveform) }
        catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, hardwareError = true); return }
        completedRecord = null
        activeStartedAt = cases.now(); activeWaveform = waveform; activeToken = UUID.randomUUID().toString()
        activeRecordId = activeToken; completedAt = 0
        update(mutable.value.pattern, VibrationPhase.PLAYING, waveform, busy = false)
        ticker = viewModelScope.launch { delay(waveform.totalMs + 100); complete(activeToken) }
    }
    fun stop() { cancelRound() }
    private fun complete(token: String) {
        if (token != activeToken || mutable.value.phase != VibrationPhase.PLAYING) return
        activeToken = ""
        completedAt = cases.wall()
        val cap = mutable.value.capability ?: DeviceVibrationCapability(false, false)
        val pattern = mutable.value.pattern
        val actual = (cases.now() - activeStartedAt).coerceIn(1, activeWaveform?.totalMs ?: 1)
        completedRecord = VibrationRecord(activeRecordId, completedAt, pattern.preset, pattern.durationMs,
            pattern.strengthPercent, actual, cap.amplitudeControl)
        update(mutable.value.pattern, VibrationPhase.FINISHED, activeWaveform)
        saveResult()
    }
    private fun cancelRound() {
        ticker?.cancel(); ticker = null
        if (activeToken.isNotEmpty() || mutable.value.phase == VibrationPhase.PLAYING) runCatching { cases.cancel() }
        activeToken = ""
        if (mutable.value.phase == VibrationPhase.PLAYING) update(mutable.value.pattern, VibrationPhase.CANCELLED, activeWaveform)
    }
    private fun update(pattern: VibrationPattern, phase: VibrationPhase = mutable.value.phase,
                       waveform: VibrationWaveform? = mutable.value.waveform, busy: Boolean = mutable.value.busy) {
        val safeWaveform = runCatching { VibrationPolicy.waveform(pattern) }.getOrElse { mutable.value.waveform }
        mutable.value = mutable.value.copy(pattern = pattern, phase = phase, waveform = safeWaveform, busy = busy, hardwareError = false)
        saved["vibPreset"] = pattern.preset.name; saved["vibDuration"] = pattern.durationMs; saved["vibStrength"] = pattern.strengthPercent
        saved["vibActive"] = phase == VibrationPhase.PLAYING
    }
    fun csv(enabled: Boolean) { mutable.value = mutable.value.copy(csv = enabled) }
    fun retry() { when (mutable.value.error) { VibrationStorageError.LOAD -> loadHistory(); VibrationStorageError.SAVE -> saveResult(); VibrationStorageError.CLEAR -> clearHistory(); null -> Unit } }
    private fun loadHistory() = storage(VibrationStorageError.LOAD) { cases.history() }
    private fun saveResult() {
        if (mutable.value.phase != VibrationPhase.FINISHED) return
        val record = completedRecord ?: return
        storage(VibrationStorageError.SAVE) { cases.save(record) }
    }
    fun clearHistory() { if (mutable.value.phase != VibrationPhase.PLAYING && mutable.value.error != VibrationStorageError.SAVE)
        storage(VibrationStorageError.CLEAR) { cases.clear() } }
    private fun storage(error: VibrationStorageError, action: suspend () -> List<VibrationRecord>) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { mutable.value = mutable.value.copy(history = action(), busy = false) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, error = error) }
        }
    }
    override fun onCleared() { ticker?.cancel(); runCatching { cases.cancel() }; super.onCleared() }
    companion object {
        fun factory(cases: VibrationUseCases): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = VibratorViewModel(cases, extras.createSavedStateHandle()) as T
        }
    }
}
