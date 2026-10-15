package com.cangshuo.toolbox.feature.chineseconvert.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionError
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionLimits
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionMode
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionOutcome
import com.cangshuo.toolbox.feature.chineseconvert.domain.ConvertChineseUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChineseConversionUiState(
    val input: String = "",
    val mode: ChineseConversionMode = ChineseConversionMode.S2T,
    val processing: Boolean = false,
    val cancelled: Boolean = false,
    val outcome: ChineseConversionOutcome? = null,
)

class ChineseConversionViewModel(
    private val convert: ConvertChineseUseCase,
    private val savedState: SavedStateHandle,
    private val debounceMillis: Long = 150L,
) : ViewModel() {
    private val state = MutableStateFlow(
        ChineseConversionUiState(
            input = savedState.get<String>(KEY_INPUT)?.takeIf { it.length <= ChineseConversionLimits.MAX_INPUT } ?: "",
            mode = ChineseConversionMode.entries.firstOrNull { it.name == savedState.get<String>(KEY_MODE) }
                ?: ChineseConversionMode.S2T,
        ),
    )
    val uiState = state.asStateFlow()
    private var job: Job? = null
    private var revision = 0L

    init { if (state.value.input.isNotEmpty()) schedule() }

    fun updateInput(value: String) {
        if (value.length > ChineseConversionLimits.MAX_INPUT) {
            invalidate()
            state.value = state.value.copy(
                processing = false,
                cancelled = false,
                outcome = ChineseConversionOutcome.Failure(ChineseConversionError.INPUT_TOO_LONG),
            )
            return
        }
        savedState[KEY_INPUT] = value
        state.value = state.value.copy(input = value)
        schedule()
    }

    fun selectMode(value: ChineseConversionMode) {
        savedState[KEY_MODE] = value.name
        state.value = state.value.copy(mode = value)
        schedule()
    }

    /** Feed the full result back as input and select the paired reverse mode in one update. */
    fun reverseResult() {
        val success = state.value.outcome as? ChineseConversionOutcome.Success ?: return
        if (success.text.length > ChineseConversionLimits.MAX_INPUT || state.value.processing) return
        val reversed = state.value.mode.reverse
        savedState[KEY_INPUT] = success.text
        savedState[KEY_MODE] = reversed.name
        state.value = state.value.copy(input = success.text, mode = reversed)
        schedule()
    }

    fun clear() = updateInput("")

    fun cancel() {
        invalidate()
        state.value = state.value.copy(processing = false, cancelled = true, outcome = null)
    }

    fun retry() { if (state.value.input.isNotEmpty()) schedule() }

    private fun invalidate() {
        revision++
        job?.cancel()
        job = null
    }

    private fun schedule() {
        invalidate()
        val snapshot = state.value
        state.value = snapshot.copy(processing = snapshot.input.isNotEmpty(), cancelled = false, outcome = null)
        if (snapshot.input.isEmpty()) return
        val owner = revision
        job = viewModelScope.launch {
            try {
                delay(debounceMillis)
                val outcome = convert(snapshot.input, snapshot.mode)
                if (revision == owner) state.value = state.value.copy(processing = false, outcome = outcome)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (revision == owner) state.value = state.value.copy(
                    processing = false,
                    outcome = ChineseConversionOutcome.Failure(ChineseConversionError.FAILED),
                )
            } finally {
                if (revision == owner && state.value.processing) state.value = state.value.copy(processing = false)
            }
        }
    }

    companion object {
        private const val KEY_INPUT = "input"
        private const val KEY_MODE = "mode"

        fun factory(convert: ConvertChineseUseCase): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                ChineseConversionViewModel(convert, extras.createSavedStateHandle()) as T
        }
    }
}
