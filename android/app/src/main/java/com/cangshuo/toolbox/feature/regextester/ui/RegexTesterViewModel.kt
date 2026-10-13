package com.cangshuo.toolbox.feature.regextester.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.regextester.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegexTesterUiState(
    val request: RegexRequest = RegexRequest(),
    val outcome: RegexOutcome? = null,
    val processing: Boolean = false,
)

class RegexTesterViewModel(private val testRegex: TestRegexUseCase, private val saved: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(RegexTesterUiState(request = restored()))
    val uiState = mutableState.asStateFlow()
    private var job: Job? = null
    private var revision = 0L

    fun setPattern(value: String) = textChange(value, RegexLimits.MAX_PATTERN, RegexFailure.PATTERN_TOO_LONG) { copy(pattern = value) }
    fun setInput(value: String) = textChange(value, RegexLimits.MAX_INPUT, RegexFailure.INPUT_TOO_LONG) { copy(input = value) }
    fun setReplacement(value: String) = textChange(value, RegexLimits.MAX_REPLACEMENT, RegexFailure.REPLACEMENT_TOO_LONG) { copy(replacement = value) }
    fun setMode(value: RegexMode) = change { copy(mode = value) }
    fun setGlobal(value: Boolean) = change { copy(global = value) }
    fun setIgnoreCase(value: Boolean) = change { copy(ignoreCase = value) }
    fun setMultiline(value: Boolean) = change { copy(multiline = value) }
    fun setDotAll(value: Boolean) = change { copy(dotAll = value) }
    fun setExpandGroups(value: Boolean) = change { copy(expandGroups = value) }
    fun clear() = change { copy(input = "", replacement = "") }
    fun applyPreset(preset: RegexPreset, sample: String) = change {
        copy(pattern = preset.expression, input = sample, mode = RegexMode.MATCH, global = true,
            ignoreCase = preset.ignoreCase, multiline = preset.multiline, dotAll = false)
    }

    fun run() {
        invalidate()
        val owner = revision
        val request = mutableState.value.request
        mutableState.value = RegexTesterUiState(request = request, processing = true)
        job = viewModelScope.launch {
            try {
                val result = testRegex(request)
                if (revision == owner) mutableState.value = RegexTesterUiState(request = request, outcome = result)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (revision == owner) mutableState.value = RegexTesterUiState(request,
                    RegexOutcome.Failure(RegexFailure.PROCESSING_FAILED))
            } finally {
                if (revision == owner && mutableState.value.processing)
                    mutableState.value = mutableState.value.copy(processing = false)
            }
        }
    }
    fun cancel() {
        invalidate()
        mutableState.value = mutableState.value.copy(processing = false, outcome = null)
    }

    private fun textChange(value: String, limit: Int, reason: RegexFailure, edit: RegexRequest.() -> RegexRequest) {
        if (value.length <= limit) change(edit)
        else {
            invalidate()
            mutableState.value = mutableState.value.copy(outcome = RegexOutcome.Failure(reason), processing = false)
        }
    }
    private fun change(edit: RegexRequest.() -> RegexRequest) {
        invalidate()
        val request = mutableState.value.request.edit()
        persist(request)
        mutableState.value = RegexTesterUiState(request)
    }
    private fun invalidate() { revision++; job?.cancel(); job = null }
    private fun restored() = RegexRequest(
        pattern = saved.get<String>("pattern")?.takeIf { it.length <= RegexLimits.MAX_PATTERN } ?: "",
        input = saved.get<String>("input")?.takeIf { it.length <= RegexLimits.MAX_INPUT } ?: "",
        replacement = saved.get<String>("replacement")?.takeIf { it.length <= RegexLimits.MAX_REPLACEMENT } ?: "",
        mode = RegexMode.entries.firstOrNull { it.name == saved.get<String>("mode") } ?: RegexMode.MATCH,
        global = saved["global"] ?: true, ignoreCase = saved["ignoreCase"] ?: false,
        multiline = saved["multiline"] ?: false, dotAll = saved["dotAll"] ?: false,
        expandGroups = saved["expandGroups"] ?: false,
    )
    private fun persist(request: RegexRequest) {
        saved["pattern"] = request.pattern; saved["input"] = request.input; saved["replacement"] = request.replacement
        saved["mode"] = request.mode.name; saved["global"] = request.global; saved["ignoreCase"] = request.ignoreCase
        saved["multiline"] = request.multiline; saved["dotAll"] = request.dotAll; saved["expandGroups"] = request.expandGroups
    }
    companion object {
        fun factory(testRegex: TestRegexUseCase): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                RegexTesterViewModel(testRegex, extras.createSavedStateHandle()) as T
        }
    }
}
