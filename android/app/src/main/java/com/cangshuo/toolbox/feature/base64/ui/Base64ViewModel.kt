package com.cangshuo.toolbox.feature.base64.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.base64.domain.Base64Mode
import com.cangshuo.toolbox.feature.base64.domain.Base64Result
import com.cangshuo.toolbox.feature.base64.domain.DecodeBase64UseCase
import com.cangshuo.toolbox.feature.base64.domain.EncodeBase64UseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class Base64ViewModel(
    private val encodeUseCase: EncodeBase64UseCase,
    private val decodeUseCase: DecodeBase64UseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val modeName = savedStateHandle.getStateFlow(KEY_MODE, Base64Mode.ENCODE.name)
    private val input = savedStateHandle.getStateFlow(KEY_INPUT, "")
    private val isUrlSafe = savedStateHandle.getStateFlow(KEY_URL_SAFE, false)

    val uiState: StateFlow<Base64UiState> = combine(
        modeName,
        input,
        isUrlSafe,
    ) { mName, inText, urlSafe ->
        val mode = runCatching { Base64Mode.valueOf(mName) }.getOrDefault(Base64Mode.ENCODE)
        val result = when (mode) {
            Base64Mode.ENCODE -> encodeUseCase(inText, urlSafe)
            Base64Mode.DECODE -> decodeUseCase(inText, urlSafe)
        }
        val output: String
        val error: Boolean
        val decodeInfo: Base64DecodeInfo?
        when (result) {
            is Base64Result.Encoded -> {
                output = result.text
                error = false
                decodeInfo = null
            }
            is Base64Result.Decoded -> {
                output = result.text.orEmpty()
                error = false
                decodeInfo = Base64DecodeInfo(
                    hex = result.hex,
                    byteCount = result.byteCount,
                    invalidUtf8Count = result.invalidUtf8Count,
                    firstInvalidByteOffset = result.firstInvalidByteOffset,
                )
            }
            is Base64Result.Empty -> {
                output = ""
                error = false
                decodeInfo = null
            }
            is Base64Result.InvalidInput -> {
                output = ""
                error = true
                decodeInfo = null
            }
        }
        Base64UiState(
            mode = mode,
            inputText = inText,
            outputText = output,
            isUrlSafe = urlSafe,
            isError = error,
            decodeInfo = decodeInfo,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        Base64UiState(),
    )

    fun setMode(mode: Base64Mode) {
        savedStateHandle[KEY_MODE] = mode.name
    }

    fun updateInput(text: String) {
        if (text.length <= MAX_INPUT_LENGTH) {
            savedStateHandle[KEY_INPUT] = text
        }
    }

    fun setUrlSafe(enabled: Boolean) {
        savedStateHandle[KEY_URL_SAFE] = enabled
    }

    fun swapContent() {
        val currentOutput = uiState.value.outputText
        val currentMode = uiState.value.mode
        if (currentOutput.isNotEmpty()) {
            savedStateHandle[KEY_INPUT] = currentOutput
            savedStateHandle[KEY_MODE] = if (currentMode == Base64Mode.ENCODE) {
                Base64Mode.DECODE.name
            } else {
                Base64Mode.ENCODE.name
            }
        }
    }

    fun clear() {
        savedStateHandle[KEY_INPUT] = ""
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_INPUT = "input"
        private const val KEY_URL_SAFE = "url_safe"
        private const val MAX_INPUT_LENGTH = 100_000

        fun factory(
            encodeUseCase: EncodeBase64UseCase,
            decodeUseCase: DecodeBase64UseCase,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val handle = extras.createSavedStateHandle()
                return Base64ViewModel(encodeUseCase, decodeUseCase, handle) as T
            }
        }
    }
}
