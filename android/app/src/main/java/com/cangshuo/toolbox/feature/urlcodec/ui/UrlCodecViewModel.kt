package com.cangshuo.toolbox.feature.urlcodec.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.urlcodec.domain.DecodeUrlUseCase
import com.cangshuo.toolbox.feature.urlcodec.domain.EncodeUrlUseCase
import com.cangshuo.toolbox.feature.urlcodec.domain.UrlCodecFailure
import com.cangshuo.toolbox.feature.urlcodec.domain.UrlCodecMode
import com.cangshuo.toolbox.feature.urlcodec.domain.UrlCodecPolicy
import com.cangshuo.toolbox.feature.urlcodec.domain.UrlCodecResult
import com.cangshuo.toolbox.feature.urlcodec.domain.UrlEncodeType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UrlCodecViewModel(
    private val encodeUseCase: EncodeUrlUseCase,
    private val decodeUseCase: DecodeUrlUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(UrlCodecUiState(
        mode = restoreEnum(KEY_MODE, UrlCodecMode.ENCODE),
        encodeType = restoreEnum(KEY_TYPE, UrlEncodeType.COMPONENT),
        inputText = savedStateHandle.get<String>(KEY_INPUT)?.takeIf { it.length <= UrlCodecPolicy.MAX_INPUT_LENGTH }.orEmpty(),
    ))
    val uiState: StateFlow<UrlCodecUiState> = mutableState.asStateFlow()
    private var processingJob: Job? = null
    private var requestVersion = 0L

    init {
        val restoredInputRejected = savedStateHandle.get<String>(KEY_INPUT).orEmpty().length > UrlCodecPolicy.MAX_INPUT_LENGTH
        savedStateHandle[KEY_INPUT] = uiState.value.inputText
        requestProcessing()
        if (restoredInputRejected) mutableState.update { it.copy(inputRejected = true) }
    }

    fun setMode(mode: UrlCodecMode) {
        if (mode == uiState.value.mode) return
        savedStateHandle[KEY_MODE] = mode.name
        mutableState.update { it.copy(mode = mode) }
        requestProcessing()
    }

    fun setEncodeType(type: UrlEncodeType) {
        if (type == uiState.value.encodeType) return
        savedStateHandle[KEY_TYPE] = type.name
        mutableState.update { it.copy(encodeType = type) }
        requestProcessing()
    }

    fun updateInput(text: String) {
        if (text.length > UrlCodecPolicy.MAX_INPUT_LENGTH) {
            mutableState.update { it.copy(inputRejected = true) }
            return
        }
        savedStateHandle[KEY_INPUT] = text
        mutableState.update { it.copy(inputText = text) }
        requestProcessing(debounce = true)
    }

    private fun requestProcessing(debounce: Boolean = false) {
        processingJob?.cancel()
        val version = ++requestVersion
        val snapshot = uiState.value
        val idle = snapshot.inputText.isEmpty()
        mutableState.update { it.copy(outputText = "", error = null, hasResult = false,
            isProcessing = !idle, cancelled = false, inputRejected = false) }
        if (idle) return
        processingJob = viewModelScope.launch {
            try {
                if (debounce) delay(150)
                val result = when (snapshot.mode) {
                    UrlCodecMode.ENCODE -> encodeUseCase(snapshot.inputText, snapshot.encodeType)
                    UrlCodecMode.DECODE -> decodeUseCase(snapshot.inputText, snapshot.encodeType)
                }
                if (version != requestVersion) return@launch
                mutableState.update { state -> when (result) {
                    is UrlCodecResult.Success -> state.copy(isProcessing = false, hasResult = true, outputText = result.text)
                    is UrlCodecResult.Error -> state.copy(isProcessing = false, error = result)
                    UrlCodecResult.Empty -> state.copy(isProcessing = false)
                } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (version == requestVersion) mutableState.update {
                    it.copy(isProcessing = false, error = UrlCodecResult.Error(UrlCodecFailure.PROCESSING_FAILED))
                }
            }
        }
    }

    fun cancelProcessing() {
        if (!uiState.value.isProcessing) return
        ++requestVersion
        processingJob?.cancel()
        mutableState.update { it.copy(isProcessing = false, cancelled = true) }
    }
    fun retryProcessing() { requestProcessing() }

    fun swapContent() {
        val snapshot = uiState.value
        if (!snapshot.hasResult || snapshot.isProcessing) return
        if (snapshot.outputText.length > UrlCodecPolicy.MAX_INPUT_LENGTH) {
            mutableState.update { it.copy(inputRejected = true) }
            return
        }
        val mode = if (snapshot.mode == UrlCodecMode.ENCODE) UrlCodecMode.DECODE else UrlCodecMode.ENCODE
        savedStateHandle[KEY_INPUT] = snapshot.outputText
        savedStateHandle[KEY_MODE] = mode.name
        mutableState.update { it.copy(inputText = snapshot.outputText, mode = mode) }
        requestProcessing()
    }

    fun clear() { updateInput("") }
    fun loadSample() {
        val snapshot = uiState.value
        updateInput(when (snapshot.encodeType) {
            UrlEncodeType.COMPONENT -> if (snapshot.mode == UrlCodecMode.ENCODE) "沧烁 工具箱+a!()*'~" else "a%20b%2B%21%28%29%2A%27~"
            UrlEncodeType.FULL_URL -> if (snapshot.mode == UrlCodecMode.ENCODE) "https://example.com/a b?q=沧烁+tools#part"
                else "https://example.com/a%20b?q=%E6%B2%A7%E7%83%81+tools%26extra#part"
            UrlEncodeType.FORM_VALUE -> if (snapshot.mode == UrlCodecMode.ENCODE) "沧烁 工具箱+~*" else "a+b%2B%7E*"
        })
    }

    private inline fun <reified T : Enum<T>> restoreEnum(key: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == savedStateHandle.get<String>(key) } ?: fallback

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_TYPE = "type"
        private const val KEY_INPUT = "input"
        fun factory(encodeUseCase: EncodeUrlUseCase, decodeUseCase: DecodeUrlUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    require(modelClass.isAssignableFrom(UrlCodecViewModel::class.java))
                    return UrlCodecViewModel(encodeUseCase, decodeUseCase, extras.createSavedStateHandle()) as T
                }
            }
    }
}
