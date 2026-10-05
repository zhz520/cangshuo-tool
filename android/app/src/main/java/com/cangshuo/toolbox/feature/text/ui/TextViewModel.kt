package com.cangshuo.toolbox.feature.text.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.feature.text.domain.ComputeTextStatisticsUseCase
import com.cangshuo.toolbox.feature.text.domain.FindReplaceUseCase
import com.cangshuo.toolbox.feature.text.domain.TextCategoryGroup
import com.cangshuo.toolbox.feature.text.domain.TextFailure
import com.cangshuo.toolbox.feature.text.domain.TextOperation
import com.cangshuo.toolbox.feature.text.domain.TextProcessingException
import com.cangshuo.toolbox.feature.text.domain.TextProcessingPolicy
import com.cangshuo.toolbox.feature.text.domain.TextStatistics
import com.cangshuo.toolbox.feature.text.domain.TransformTextUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TextViewModel(
    private val computeStatsUseCase: ComputeTextStatisticsUseCase,
    private val transformUseCase: TransformTextUseCase,
    private val findReplaceUseCase: FindReplaceUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val restoredGroup = restoreEnum("group", TextCategoryGroup.CASE)
    private val mutableState = MutableStateFlow(TextUiState(
        selectedGroup = restoredGroup,
        selectedOperation = restoreEnum("operation", defaultOperation(restoredGroup))
            .takeIf { it.group == restoredGroup } ?: defaultOperation(restoredGroup),
        inputText = restoreText("input", TextProcessingPolicy.MAX_INPUT_LENGTH),
        findText = restoreText("find", TextProcessingPolicy.MAX_FIND_LENGTH),
        replaceText = restoreText("replace", TextProcessingPolicy.MAX_REPLACEMENT_LENGTH),
        matchCase = savedStateHandle["match_case"] ?: true,
        useRegex = savedStateHandle["use_regex"] ?: false,
        expandGroups = savedStateHandle["expand_groups"] ?: true,
        multiline = savedStateHandle["multiline"] ?: false,
        dotAll = savedStateHandle["dot_all"] ?: false,
    ))
    val uiState: StateFlow<TextUiState> = mutableState.asStateFlow()
    private var processingJob: Job? = null
    private var requestVersion = 0L

    init { requestProcessing() }

    fun setGroup(group: TextCategoryGroup) {
        if (uiState.value.selectedGroup == group) return
        val operation = defaultOperation(group)
        savedStateHandle["group"] = group.name
        savedStateHandle["operation"] = operation.name
        mutableState.update { it.copy(selectedGroup = group, selectedOperation = operation) }
        requestProcessing()
    }

    fun setOperation(operation: TextOperation) {
        if (operation.group != uiState.value.selectedGroup) return
        savedStateHandle["operation"] = operation.name
        mutableState.update { it.copy(selectedOperation = operation) }
        requestProcessing()
    }

    fun updateInput(text: String) {
        if (!acceptLength(text, TextProcessingPolicy.MAX_INPUT_LENGTH, R.string.text_error_input_limit)) return
        savedStateHandle["input"] = text
        mutableState.update { it.copy(inputText = text) }
        requestProcessing(debounce = true)
    }

    fun updateFind(text: String) {
        if (!acceptLength(text, TextProcessingPolicy.MAX_FIND_LENGTH, R.string.text_error_find_limit)) return
        savedStateHandle["find"] = text
        mutableState.update { it.copy(findText = text) }
        requestProcessing(debounce = true)
    }

    fun updateReplace(text: String) {
        if (!acceptLength(text, TextProcessingPolicy.MAX_REPLACEMENT_LENGTH, R.string.text_error_replace_limit)) return
        savedStateHandle["replace"] = text
        mutableState.update { it.copy(replaceText = text) }
        requestProcessing(debounce = true)
    }

    fun setMatchCase(value: Boolean) {
        savedStateHandle["match_case"] = value
        mutableState.update { it.copy(matchCase = value) }
        requestProcessing()
    }
    fun setUseRegex(value: Boolean) {
        savedStateHandle["use_regex"] = value
        mutableState.update { it.copy(useRegex = value) }
        requestProcessing()
    }
    fun setExpandGroups(value: Boolean) {
        savedStateHandle["expand_groups"] = value
        mutableState.update { it.copy(expandGroups = value) }
        requestProcessing()
    }
    fun setMultiline(value: Boolean) {
        savedStateHandle["multiline"] = value
        mutableState.update { it.copy(multiline = value) }
        requestProcessing()
    }
    fun setDotAll(value: Boolean) {
        savedStateHandle["dot_all"] = value
        mutableState.update { it.copy(dotAll = value) }
        requestProcessing()
    }

    private fun requestProcessing(debounce: Boolean = false) {
        processingJob?.cancel()
        val version = ++requestVersion
        val snapshot = uiState.value
        val needsWork = snapshot.inputText.isNotEmpty() ||
            (snapshot.selectedGroup == TextCategoryGroup.FIND_REPLACE && snapshot.findText.isNotEmpty())
        mutableState.update { it.copy(outputText = "", hasResult = false, matchCount = null,
            stats = if (snapshot.inputText.isEmpty()) TextStatistics() else null,
            processingErrorRes = null, inputErrorRes = null, statusMessageRes = null, isProcessing = needsWork) }
        if (!needsWork) return
        processingJob = viewModelScope.launch {
            try {
                if (debounce) delay(150)
                val stats = computeStatsUseCase(snapshot.inputText)
                if (version != requestVersion) return@launch
                mutableState.update { it.copy(stats = stats.getOrNull()) }
                if (stats.isFailure) {
                    mutableState.update { it.copy(isProcessing = false, processingErrorRes = stats.exceptionOrNull().messageRes()) }
                    return@launch
                }
                if (snapshot.selectedGroup == TextCategoryGroup.FIND_REPLACE) {
                    val result = findReplaceUseCase(snapshot.inputText, snapshot.findText, snapshot.replaceText,
                        snapshot.matchCase, snapshot.useRegex, snapshot.expandGroups, snapshot.multiline, snapshot.dotAll)
                    if (version != requestVersion) return@launch
                    mutableState.update { it.copy(isProcessing = false, hasResult = result.isSuccess,
                        outputText = result.getOrNull()?.text.orEmpty(), matchCount = result.getOrNull()?.matchCount,
                        processingErrorRes = if (result.isFailure) result.exceptionOrNull().messageRes() else null) }
                } else {
                    val result = transformUseCase(snapshot.inputText, snapshot.selectedOperation)
                    if (version != requestVersion) return@launch
                    mutableState.update { it.copy(isProcessing = false, hasResult = result.isSuccess,
                        outputText = result.getOrDefault(""),
                        processingErrorRes = if (result.isFailure) result.exceptionOrNull().messageRes() else null) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (version == requestVersion) mutableState.update {
                    it.copy(isProcessing = false, processingErrorRes = R.string.text_error_processing)
                }
            }
        }
    }

    fun cancelProcessing() {
        ++requestVersion
        processingJob?.cancel()
        mutableState.update { it.copy(isProcessing = false, statusMessageRes = R.string.text_cancelled) }
    }
    fun retryProcessing() { requestProcessing() }
    fun swapContent() {
        val snapshot = uiState.value
        if (!snapshot.hasResult || snapshot.isProcessing) return
        updateInput(snapshot.outputText)
    }
    fun loadSample() { updateInput(SAMPLE_TEXT) }
    fun clear() { updateInput("") }

    private fun acceptLength(value: String, limit: Int, errorRes: Int): Boolean {
        if (value.length <= limit) return true
        mutableState.update { it.copy(inputErrorRes = errorRes) }
        return false
    }
    private fun restoreText(key: String, limit: Int): String = savedStateHandle.get<String>(key)?.takeIf { it.length <= limit }.orEmpty()
    private inline fun <reified T : Enum<T>> restoreEnum(key: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == savedStateHandle.get<String>(key) } ?: fallback

    companion object {
        private const val SAMPLE_TEXT = "Hello World! Welcome to Cangshuo Toolbox.\n这是沧烁工具箱的文本处理套件。\nfile20\nfile2\nfile10\nfile2\n   lots   of   extra   whitespace   "
        private fun defaultOperation(group: TextCategoryGroup): TextOperation = when (group) {
            TextCategoryGroup.CASE, TextCategoryGroup.FIND_REPLACE -> TextOperation.UPPERCASE
            TextCategoryGroup.WHITESPACE -> TextOperation.TRIM_LINES
            TextCategoryGroup.LINES -> TextOperation.SORT_AZ
        }
        fun factory(computeStatsUseCase: ComputeTextStatisticsUseCase, transformUseCase: TransformTextUseCase,
            findReplaceUseCase: FindReplaceUseCase): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                require(modelClass.isAssignableFrom(TextViewModel::class.java))
                return TextViewModel(computeStatsUseCase, transformUseCase, findReplaceUseCase, extras.createSavedStateHandle()) as T
            }
        }
    }
}

private fun Throwable?.messageRes(): Int = when ((this as? TextProcessingException)?.reason) {
    TextFailure.INPUT_TOO_LONG -> R.string.text_error_input_limit
    TextFailure.FIND_TOO_LONG -> R.string.text_error_find_limit
    TextFailure.REPLACEMENT_TOO_LONG -> R.string.text_error_replace_limit
    TextFailure.INVALID_UNICODE -> R.string.text_error_unicode
    TextFailure.INVALID_REGEX -> R.string.text_error_regex
    TextFailure.REGEX_TOO_COMPLEX -> R.string.text_error_regex_complex
    TextFailure.INVALID_REPLACEMENT -> R.string.text_error_replacement
    TextFailure.OUTPUT_TOO_LONG -> R.string.text_error_output_limit
    TextFailure.TOO_MANY_LINES -> R.string.text_error_lines
    TextFailure.TIME_BUDGET -> R.string.text_error_timeout
    TextFailure.MEMORY_LIMIT -> R.string.text_error_memory
    TextFailure.PROCESSING_FAILED, null -> R.string.text_error_processing
}
