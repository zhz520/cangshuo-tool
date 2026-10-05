package com.cangshuo.toolbox.feature.json.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.json.domain.CompressJsonUseCase
import com.cangshuo.toolbox.feature.json.domain.EscapeJsonUseCase
import com.cangshuo.toolbox.feature.json.domain.FormatJsonUseCase
import com.cangshuo.toolbox.feature.json.domain.JsonFailure
import com.cangshuo.toolbox.feature.json.domain.JsonIndent
import com.cangshuo.toolbox.feature.json.domain.JsonProcessResult
import com.cangshuo.toolbox.feature.json.domain.JsonProcessingPolicy
import com.cangshuo.toolbox.feature.json.domain.JsonStringInput
import com.cangshuo.toolbox.feature.json.domain.UnescapeJsonUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class JsonViewModel(
    private val formatJsonUseCase: FormatJsonUseCase,
    private val compressJsonUseCase: CompressJsonUseCase,
    private val escapeJsonUseCase: EscapeJsonUseCase,
    private val unescapeJsonUseCase: UnescapeJsonUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(JsonUiState(
        mode = restoreEnum("mode", JsonMode.FORMAT),
        indent = restoreEnum("indent", JsonIndent.TWO_SPACES),
        stringInput = restoreEnum("string_input", JsonStringInput.QUOTED_LITERAL),
        inputText = savedStateHandle.get<String>("input")?.takeIf { it.length <= JsonProcessingPolicy.MAX_INPUT_LENGTH }.orEmpty(),
    ))
    val uiState: StateFlow<JsonUiState> = mutableState.asStateFlow()
    private var processingJob: Job? = null
    private var requestVersion = 0L

    init { requestProcessing() }

    fun setMode(mode: JsonMode) {
        savedStateHandle["mode"] = mode.name
        mutableState.update { it.copy(mode = mode) }
        requestProcessing()
    }
    fun setIndent(indent: JsonIndent) {
        savedStateHandle["indent"] = indent.name
        mutableState.update { it.copy(indent = indent) }
        requestProcessing()
    }
    fun setStringInput(value: JsonStringInput) {
        savedStateHandle["string_input"] = value.name
        mutableState.update { it.copy(stringInput = value) }
        requestProcessing()
    }
    fun updateInput(text: String) {
        if (text.length > JsonProcessingPolicy.MAX_INPUT_LENGTH) {
            mutableState.update { it.copy(inputRejected = true) }
            return
        }
        savedStateHandle["input"] = text
        mutableState.update { it.copy(inputText = text) }
        requestProcessing(debounce = true)
    }

    private fun requestProcessing(debounce: Boolean = false) {
        processingJob?.cancel()
        val version = ++requestVersion
        val snapshot = uiState.value
        val idle = snapshot.inputText.isEmpty() && snapshot.mode in listOf(JsonMode.FORMAT, JsonMode.COMPRESS)
        mutableState.update { it.copy(outputText = "", hasResult = false, error = null, cancelled = false,
            inputRejected = false, outputLineCount = 0, outputByteCount = 0, duplicateKeyCount = 0,
            inputByteCount = if (snapshot.inputText.isEmpty()) 0 else null, isProcessing = !idle) }
        if (idle) return
        processingJob = viewModelScope.launch {
            try {
                if (debounce) delay(150)
                val result = when (snapshot.mode) {
                    JsonMode.FORMAT -> formatJsonUseCase(snapshot.inputText, snapshot.indent)
                    JsonMode.COMPRESS -> compressJsonUseCase(snapshot.inputText)
                    JsonMode.ESCAPE -> escapeJsonUseCase(snapshot.inputText)
                    JsonMode.UNESCAPE -> unescapeJsonUseCase(snapshot.inputText, snapshot.stringInput)
                }
                if (version != requestVersion) return@launch
                mutableState.update { state -> when (result) {
                    is JsonProcessResult.Success -> state.copy(isProcessing = false, hasResult = true,
                        outputText = result.output, inputByteCount = result.inputByteCount,
                        outputLineCount = result.lineCount, outputByteCount = result.byteCount,
                        duplicateKeyCount = result.duplicateKeyCount)
                    is JsonProcessResult.Error -> state.copy(isProcessing = false, error = result, inputByteCount = result.inputByteCount)
                    is JsonProcessResult.Empty -> state.copy(isProcessing = false, inputByteCount = result.inputByteCount)
                } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (version == requestVersion) mutableState.update {
                    it.copy(isProcessing = false, error = JsonProcessResult.Error(JsonFailure.PROCESSING_FAILED))
                }
            }
        }
    }

    fun cancelProcessing() {
        ++requestVersion
        processingJob?.cancel()
        mutableState.update { it.copy(isProcessing = false, cancelled = true) }
    }
    fun retryProcessing() { requestProcessing() }
    fun swapContent() {
        val snapshot = uiState.value
        if (!snapshot.hasResult || snapshot.isProcessing) return
        val mode = when (snapshot.mode) { JsonMode.ESCAPE -> JsonMode.UNESCAPE; JsonMode.UNESCAPE -> JsonMode.ESCAPE; else -> snapshot.mode }
        savedStateHandle["input"] = snapshot.outputText
        savedStateHandle["mode"] = mode.name
        savedStateHandle["string_input"] = JsonStringInput.QUOTED_LITERAL.name
        mutableState.update { it.copy(inputText = snapshot.outputText, mode = mode, stringInput = JsonStringInput.QUOTED_LITERAL) }
        requestProcessing()
    }
    fun clear() { updateInput("") }
    fun loadSample() {
        val snapshot = uiState.value
        updateInput(when (snapshot.mode) {
            JsonMode.FORMAT, JsonMode.COMPRESS -> SAMPLE
            JsonMode.ESCAPE -> "  沧烁工具箱\n第二行：\"JSON\" 与反斜杠 \\  "
            JsonMode.UNESCAPE -> if (snapshot.stringInput == JsonStringInput.QUOTED_LITERAL) "\"$ESCAPED_SAMPLE\"" else ESCAPED_SAMPLE
        })
    }

    private inline fun <reified T : Enum<T>> restoreEnum(key: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == savedStateHandle.get<String>(key) } ?: fallback

    companion object {
        private const val SAMPLE = "{\"name\":\"沧烁工具箱\",\"largeInteger\":9007199254740993,\"decimal\":1.2300e+4,\"enabled\":true,\"items\":[\"中文\",null,{\"empty\":\"\"}]}"
        private const val ESCAPED_SAMPLE = "  \\u6ca7\\u70c1\\n\\\"JSON\\\" 与反斜杠 \\\\  "
        fun factory(formatJsonUseCase: FormatJsonUseCase, compressJsonUseCase: CompressJsonUseCase,
            escapeJsonUseCase: EscapeJsonUseCase, unescapeJsonUseCase: UnescapeJsonUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    require(modelClass.isAssignableFrom(JsonViewModel::class.java))
                    return JsonViewModel(formatJsonUseCase, compressJsonUseCase, escapeJsonUseCase,
                        unescapeJsonUseCase, extras.createSavedStateHandle()) as T
                }
            }
    }
}
