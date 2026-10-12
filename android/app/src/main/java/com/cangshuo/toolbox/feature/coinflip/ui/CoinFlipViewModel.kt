package com.cangshuo.toolbox.feature.coinflip.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.coinflip.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class CoinError { LABELS, COUNT, LIMIT, FAILED, INTERRUPTED }
data class CoinFlipUiState(
    val headsInput: String = "",
    val tailsInput: String = "",
    val countInput: String = "10",
    val animation: Boolean = true,
    val busy: Boolean = false,
    val flipId: Long = 0,
    val pending: CoinBatch? = null,
    val lastBatch: CoinBatch? = null,
    val session: CoinSession = CoinSession(),
    val error: CoinError? = null,
    val csvReport: Boolean = false,
    val showAll: Boolean = false,
)

class CoinFlipViewModel(private val flipCoins: FlipCoinsUseCase, private val saved: SavedStateHandle) : ViewModel() {
    private val mutable = MutableStateFlow(CoinFlipUiState(
        headsInput = saved.get<String>("coinHeads")?.takeIf { it.length <= 40 }.orEmpty(),
        tailsInput = saved.get<String>("coinTails")?.takeIf { it.length <= 40 }.orEmpty(),
        countInput = saved.get<String>("coinCount")?.takeIf { it.length <= 3 } ?: "10",
        animation = saved["coinAnimation"] ?: true,
        csvReport = saved["coinCsv"] ?: false,
    ))
    val state = mutable.asStateFlow()
    private var foreground = false
    private var job: Job? = null
    private var generation = 0L

    fun start() { foreground = true }
    fun stop() {
        foreground = false
        generation++
        job?.cancel(); job = null
        mutable.update { it.copy(busy = false, pending = null, error = if (it.busy) CoinError.INTERRUPTED else it.error) }
    }
    // Default labels are supplied by the current UI locale; custom labels survive configuration changes.
    fun updateHeads(text: String) = updateLabel(text, true)
    fun updateTails(text: String) = updateLabel(text, false)
    private fun updateLabel(text: String, heads: Boolean) {
        if (mutable.value.busy) return
        if (text.length > 40 || text.codePointCount(0, text.length) > CoinPolicy.LABEL_CODE_POINTS) {
            mutable.update { it.copy(error = CoinError.LABELS) }; return
        }
        saved[if (heads) "coinHeads" else "coinTails"] = text
        mutable.update { if (heads) it.copy(headsInput = text, error = null) else it.copy(tailsInput = text, error = null) }
    }
    fun updateCount(text: String) {
        if (mutable.value.busy) return
        if (text.length > 3) { mutable.update { it.copy(error = CoinError.COUNT) }; return }
        saved["coinCount"] = text
        mutable.update { it.copy(countInput = text, error = null) }
    }
    fun setAnimation(enabled: Boolean) {
        if (mutable.value.busy) return
        saved["coinAnimation"] = enabled
        mutable.update { it.copy(animation = enabled) }
    }
    fun selectReport(csv: Boolean) { saved["coinCsv"] = csv; mutable.update { it.copy(csvReport = csv) } }
    fun showAll(enabled: Boolean) { mutable.update { it.copy(showAll = enabled) } }
    fun clear() {
        generation++
        job?.cancel(); job = null
        mutable.update { it.copy(busy = false, pending = null, lastBatch = null, session = CoinSession(), error = null, showAll = false) }
    }

    fun flip(batch: Boolean, defaultHeads: String, defaultTails: String) {
        val current = mutable.value
        if (!foreground || current.busy) return
        val labels = CoinPolicy.labels(current.headsInput.ifBlank { defaultHeads }, current.tailsInput.ifBlank { defaultTails })
        if (labels == null) { mutable.update { it.copy(error = CoinError.LABELS) }; return }
        val count = if (batch) CoinPolicy.count(current.countInput) else 1
        if (count == null) { mutable.update { it.copy(error = CoinError.COUNT) }; return }
        if (count > CoinPolicy.MAX_SESSION - current.session.total) { mutable.update { it.copy(error = CoinError.LIMIT) }; return }
        val id = ++generation
        // Set before launching: two taps in the same frame cannot create two jobs.
        mutable.update { it.copy(busy = true, flipId = id, pending = null, error = null) }
        job = viewModelScope.launch {
            try {
                val sides = withTimeout(CoinPolicy.REQUEST_TIMEOUT_MS) { flipCoins(count) }
                ensureActive()
                if (generation == id && foreground) {
                    mutable.update { it.copy(pending = CoinBatch(sides, labels)) }
                }
            } catch (_: TimeoutCancellationException) {
                if (generation == id) mutable.update { it.copy(busy = false, pending = null, error = CoinError.FAILED) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (generation == id) mutable.update { it.copy(busy = false, pending = null, error = CoinError.FAILED) }
            }
        }
    }

    /** Called after the shared coin settles (immediately when motion is disabled). */
    fun settle(id: Long) {
        if (!foreground || generation != id) return
        val current = mutable.value
        val batch = current.pending ?: return
        if (!current.busy || current.flipId != id) return
        mutable.update { it.copy(busy = false, pending = null, lastBatch = batch, session = it.session.add(batch)) }
    }

    companion object {
        fun factory(flip: FlipCoinsUseCase): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                CoinFlipViewModel(flip, extras.createSavedStateHandle()) as T
        }
    }
}
