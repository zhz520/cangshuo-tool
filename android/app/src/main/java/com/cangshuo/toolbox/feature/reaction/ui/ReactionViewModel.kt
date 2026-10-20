package com.cangshuo.toolbox.feature.reaction.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.reaction.domain.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReactionStage { IDLE, WAITING, CUE, READY, RESULT, EARLY, TIMEOUT, INTERRUPTED, COMPLETE }
enum class ReactionStorageError { LOAD, SAVE, CLEAR }

data class ReactionUiState(
    val stage: ReactionStage = ReactionStage.IDLE,
    val rounds: Int = 5,
    val samples: List<Long> = emptyList(),
    val early: Int = 0,
    val missed: Int = 0,
    val history: List<ReactionRecord> = emptyList(),
    val busy: Boolean = false,
    val storageError: ReactionStorageError? = null,
    val csv: Boolean = false,
)

class ReactionViewModel(private val cases: ReactionUseCases, private val saved: SavedStateHandle) : ViewModel() {
    private var sessionId = saved.get<String>("rxId") ?: UUID.randomUUID().toString()
    private var finishedAt = saved.get<Long>("rxFinished") ?: 0L
    private var committed = saved.get<Boolean>("rxStored") ?: false
    private val rounds = saved.get<Int>("rxRounds")?.takeIf { it in ReactionPolicy.roundOptions } ?: 5
    private val samples = saved.get<LongArray>("rxSamples")?.toList()?.takeIf {
        it.size <= rounds && it.all { ms -> ms in ReactionPolicy.MIN_REACTION..ReactionPolicy.MAX_REACTION }
    } ?: emptyList()
    private val mutable = MutableStateFlow(ReactionUiState(
        stage = if (samples.size == rounds) ReactionStage.COMPLETE else if (saved.get<Boolean>("rxActive") == true) ReactionStage.INTERRUPTED else if (samples.isNotEmpty()) ReactionStage.RESULT else ReactionStage.IDLE,
        rounds = rounds, samples = samples, early = (saved.get<Int>("rxEarly") ?: 0).coerceAtLeast(0),
        missed = (saved.get<Int>("rxMissed") ?: 0).coerceAtLeast(0),
    ))
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var visibleAt: Long? = null
    private var foreground = false

    init { loadHistory() }

    fun foreground() { foreground = true }
    fun background() {
        foreground = false
        interruptRound()
    }
    fun interruptRound() {
        if (mutable.value.stage in ACTIVE) {
            cancelRound()
            update(mutable.value.copy(stage = ReactionStage.INTERRUPTED))
        }
    }

    fun selectRounds(value: Int) {
        if (value !in ReactionPolicy.roundOptions || mutable.value.stage != ReactionStage.IDLE) return
        update(mutable.value.copy(rounds = value))
    }

    fun newSession() {
        if (mutable.value.busy || mutable.value.storageError == ReactionStorageError.SAVE) return
        cancelRound()
        sessionId = UUID.randomUUID().toString()
        finishedAt = 0
        committed = false
        saved["rxStored"] = false
        update(mutable.value.copy(stage = ReactionStage.IDLE, samples = emptyList(), early = 0, missed = 0))
    }

    fun beginRound() {
        val current = mutable.value
        if (!foreground || current.busy || current.storageError != null || current.stage in ACTIVE || current.stage == ReactionStage.COMPLETE) return
        cancelRound()
        val token = generation
        update(current.copy(stage = ReactionStage.WAITING))
        job = viewModelScope.launch {
            delay(cases.waitMs())
            if (foreground && token == generation && mutable.value.stage == ReactionStage.WAITING) {
                // CUE is drawn before accepting any reaction time. Timer starts in the draw callback.
                update(mutable.value.copy(stage = ReactionStage.CUE))
            }
        }
    }

    fun cueDrawn() {
        if (!foreground || mutable.value.stage != ReactionStage.CUE || visibleAt != null) return
        visibleAt = cases.now()
        val token = generation
        update(mutable.value.copy(stage = ReactionStage.READY))
        job = viewModelScope.launch {
            delay(ReactionPolicy.MAX_REACTION + 1)
            if (foreground && token == generation && mutable.value.stage == ReactionStage.READY) timeout()
        }
    }

    /** Touch uses the input event's uptime timestamp; accessibility/keyboard uses current uptime. */
    fun tap(eventUptimeMs: Long = cases.now()) {
        if (!foreground) return
        val current = mutable.value
        if (current.stage !in ACTIVE) return
        val start = visibleAt
        if (current.stage != ReactionStage.READY || start == null || eventUptimeMs - start < ReactionPolicy.MIN_REACTION) {
            cancelRound()
            update(current.copy(stage = ReactionStage.EARLY, early = (current.early + 1).coerceAtMost(1_000_000)))
            return
        }
        if (eventUptimeMs > cases.now() || eventUptimeMs - start > ReactionPolicy.MAX_REACTION) { timeout(); return }
        val result = eventUptimeMs - start
        cancelRound()
        val values = current.samples + result
        val complete = values.size == current.rounds
        update(current.copy(stage = if (complete) ReactionStage.COMPLETE else ReactionStage.RESULT, samples = values))
        if (complete) { finishedAt = cases.wall(); saved["rxFinished"] = finishedAt; saveResult() }
    }

    private fun timeout() {
        cancelRound()
        update(mutable.value.copy(stage = ReactionStage.TIMEOUT, missed = (mutable.value.missed + 1).coerceAtMost(1_000_000)))
    }

    private fun cancelRound() { generation++; job?.cancel(); job = null; visibleAt = null }

    private fun update(next: ReactionUiState) {
        mutable.value = next
        saved["rxId"] = sessionId
        saved["rxRounds"] = next.rounds
        saved["rxSamples"] = next.samples.toLongArray()
        saved["rxEarly"] = next.early
        saved["rxMissed"] = next.missed
        saved["rxActive"] = next.stage in ACTIVE
    }

    fun selectCsv(enabled: Boolean) { mutable.value = mutable.value.copy(csv = enabled) }
    fun retry() {
        when (mutable.value.storageError) {
            ReactionStorageError.SAVE -> saveResult()
            ReactionStorageError.LOAD -> loadHistory()
            ReactionStorageError.CLEAR -> clearHistory()
            null -> Unit
        }
    }
    private fun loadHistory() = storage(ReactionStorageError.LOAD) { cases.history() }
    private fun saveResult() {
        val current = mutable.value
        if (current.stage != ReactionStage.COMPLETE) return
        if (finishedAt <= 0) { finishedAt = cases.wall(); saved["rxFinished"] = finishedAt }
        val record = ReactionRecord(sessionId, finishedAt, current.samples, current.early, current.missed)
        storage(ReactionStorageError.SAVE) { cases.save(record) }
    }
    fun clearHistory() {
        if (mutable.value.stage in ACTIVE || mutable.value.storageError == ReactionStorageError.SAVE) return
        storage(ReactionStorageError.CLEAR) { cases.clear() }
    }
    private fun storage(error: ReactionStorageError, action: suspend () -> List<ReactionRecord>) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, storageError = null)
        viewModelScope.launch {
            try {
                val records = action()
                mutable.value = mutable.value.copy(history = records, busy = false)
                if (error == ReactionStorageError.SAVE) { committed = true; saved["rxStored"] = true }
                // Recover a completed but not yet committed session after process death.
                if (error == ReactionStorageError.LOAD && !committed && mutable.value.stage == ReactionStage.COMPLETE) saveResult()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, storageError = error) }
        }
    }

    companion object {
        val ACTIVE = setOf(ReactionStage.WAITING, ReactionStage.CUE, ReactionStage.READY)
        fun factory(cases: ReactionUseCases): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                ReactionViewModel(cases, extras.createSavedStateHandle()) as T
        }
    }
}
