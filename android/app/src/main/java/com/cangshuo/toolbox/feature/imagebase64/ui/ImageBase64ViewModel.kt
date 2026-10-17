package com.cangshuo.toolbox.feature.imagebase64.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cangshuo.toolbox.feature.imagebase64.domain.DecodeImageBase64UseCase
import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Error
import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Exception
import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Limits
import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Mode
import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Options
import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Result
import com.cangshuo.toolbox.feature.imagebase64.domain.LoadImageBase64UseCase
import com.cangshuo.toolbox.feature.imagebase64.domain.PrepareImageBase64ShareUseCase
import com.cangshuo.toolbox.feature.imagebase64.domain.SaveImageBase64TextUseCase
import com.cangshuo.toolbox.feature.imagebase64.domain.renderImageBase64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ImageBase64Message { SAVE_SUCCEEDED, SAVE_FAILED, SHARE_FAILED }

data class ImageBase64UiState(
    val mode: ImageBase64Mode = ImageBase64Mode.ENCODE,
    val options: ImageBase64Options = ImageBase64Options(),
    val input: String = "",
    val loading: Boolean = false,
    val saving: Boolean = false,
    val sharing: Boolean = false,
    val renderingOutput: Boolean = false,
    val cancelled: Boolean = false,
    val result: ImageBase64Result? = null,
    val output: String = "",
    val error: ImageBase64Error? = null,
    val message: ImageBase64Message? = null,
    val shareRequest: Uri? = null,
)

class ImageBase64ViewModel(
    private val encode: LoadImageBase64UseCase,
    private val decode: DecodeImageBase64UseCase,
    private val saveText: SaveImageBase64TextUseCase,
    private val prepareShare: PrepareImageBase64ShareUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(ImageBase64UiState())
    val uiState = state.asStateFlow()
    private var loadJob: Job? = null
    private var renderJob: Job? = null
    private var saveJob: Job? = null
    private var shareJob: Job? = null
    private var revision = 0L
    private var renderRevision = 0L
    private var resultRevision = 0L
    private var lastImageUri: String? = null
    private var lastDecodedText: String? = null

    fun onModeChanged(mode: ImageBase64Mode) {
        if (state.value.mode == mode) return
        revision++
        renderRevision++
        loadJob?.cancel()
        renderJob?.cancel()
        cancelExports()
        loadJob = null
        renderJob = null
        resultRevision++
        state.update {
            it.copy(mode = mode, loading = false, saving = false, sharing = false, cancelled = false,
                renderingOutput = false, result = null, output = "", error = null, message = null, shareRequest = null)
        }
    }

    fun updateInput(text: String) {
        if (text.length <= ImageBase64Limits.MAX_INPUT_CHARS) state.update { it.copy(input = text, error = null) }
        else state.update { it.copy(error = ImageBase64Error.TOO_LARGE) }
    }

    fun clearInput() = state.update { it.copy(input = "", error = null) }

    fun onImagePicked(uri: String?) {
        if (uri == null) return
        lastImageUri = uri
        lastDecodedText = null
        loadImage(uri)
    }

    fun onDecode() {
        val input = state.value.input
        if (input.isBlank() || state.value.error == ImageBase64Error.TOO_LARGE || state.value.loading) return
        lastDecodedText = input
        val owner = beginLoad()
        loadJob = viewModelScope.launch {
            try {
                val result = decode(input)
                publishResult(result, owner)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (revision == owner) state.update { it.copy(loading = false, error = ImageBase64Error.LOAD_FAILED) }
            }
        }
    }

    fun onRetry() {
        if (state.value.mode == ImageBase64Mode.ENCODE) lastImageUri?.let(::loadImage) else {
            lastDecodedText?.let { value -> state.update { it.copy(input = value) }; onDecode() }
        }
    }

    fun onCancelLoad() {
        revision++
        renderRevision++
        loadJob?.cancel()
        loadJob = null
        state.update { it.copy(loading = false, cancelled = true, error = null) }
    }

    fun onPickerUnavailable() = state.update { it.copy(error = ImageBase64Error.PICKER_UNAVAILABLE) }

    fun setDataUrl(enabled: Boolean) {
        val options = state.value.options.copy(
            dataUrl = enabled,
            urlSafe = if (enabled) false else state.value.options.urlSafe,
            lineWrap = if (enabled) false else state.value.options.lineWrap,
        )
        renderOptions(options)
    }

    fun setUrlSafe(enabled: Boolean) = renderOptions(state.value.options.copy(urlSafe = enabled, dataUrl = false))

    fun setLineWrap(enabled: Boolean) = renderOptions(state.value.options.copy(lineWrap = enabled, dataUrl = false))

    fun onSave(uri: String?) {
        val text = state.value.output
        if (uri == null || text.isEmpty() || state.value.saving || state.value.sharing || state.value.renderingOutput) return
        val owner = resultRevision
        state.update { it.copy(saving = true, message = null) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            val result: Result<Unit> = try { saveText(uri, text) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { Result.failure(ImageBase64Exception(ImageBase64Error.SAVE_FAILED)) }
            if (resultRevision == owner) state.update {
                it.copy(saving = false, message = if (result.isSuccess) ImageBase64Message.SAVE_SUCCEEDED else ImageBase64Message.SAVE_FAILED)
            }
        }
    }

    fun onShare() {
        val text = state.value.output
        if (text.isEmpty() || state.value.sharing || state.value.saving || state.value.renderingOutput) return
        val owner = resultRevision
        state.update { it.copy(sharing = true, message = null) }
        shareJob?.cancel()
        shareJob = viewModelScope.launch {
            val result: Result<Uri> = try { prepareShare(text) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { Result.failure(ImageBase64Exception(ImageBase64Error.SHARE_FAILED)) }
            if (resultRevision == owner) state.update {
                it.copy(sharing = false, shareRequest = result.getOrNull(),
                    message = if (result.isSuccess) it.message else ImageBase64Message.SHARE_FAILED)
            }
        }
    }

    fun onShareHandled(success: Boolean) = state.update {
        if (it.shareRequest == null) it else
            it.copy(shareRequest = null, message = if (success) it.message else ImageBase64Message.SHARE_FAILED)
    }

    fun onMessageConsumed() = state.update { it.copy(message = null) }

    fun onClearResult() {
        revision++
        renderRevision++
        loadJob?.cancel()
        renderJob?.cancel()
        cancelExports()
        resultRevision++
        lastImageUri = null
        lastDecodedText = null
        state.update { it.copy(result = null, output = "", loading = false, saving = false, sharing = false,
            renderingOutput = false, cancelled = false, error = null, shareRequest = null) }
    }

    private fun loadImage(uri: String) {
        val owner = beginLoad()
        loadJob = viewModelScope.launch {
            try {
                val result = encode(uri)
                publishResult(result, owner)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (revision == owner) state.update { it.copy(loading = false, error = ImageBase64Error.LOAD_FAILED) }
            }
        }
    }

    private fun beginLoad(): Long {
        revision++
        renderRevision++
        resultRevision++
        loadJob?.cancel()
        renderJob?.cancel()
        cancelExports()
        state.update { it.copy(loading = true, cancelled = false, error = null, message = null,
            saving = false, sharing = false, shareRequest = null) }
        return revision
    }

    private suspend fun publishResult(result: Result<ImageBase64Result>, owner: Long) {
        if (revision != owner) return
        result.fold(
            onSuccess = { image ->
                try {
                    val output = withContext(Dispatchers.Default) {
                        renderImageBase64(image.rawBase64, image.mimeType, state.value.options)
                    }
                    if (revision == owner) {
                        resultRevision++
                        state.update {
                            it.copy(loading = false, result = image, output = output, error = null, cancelled = false,
                                renderingOutput = false, message = null, shareRequest = null)
                        }
                    }
                } catch (_: OutOfMemoryError) {
                    if (revision == owner) state.update {
                        it.copy(loading = false, renderingOutput = false, output = "", error = ImageBase64Error.MEMORY_LIMIT)
                    }
                }
            },
            onFailure = { failure ->
                val reason = (failure as? ImageBase64Exception)?.reason ?: ImageBase64Error.LOAD_FAILED
                if (revision == owner) state.update { it.copy(loading = false, error = reason) }
            },
        )
    }

    private fun renderOptions(options: ImageBase64Options) {
        val previous = state.value
        val result = previous.result
        state.update { it.copy(options = options, renderingOutput = result != null) }
        if (result == null) return
        val owner = ++renderRevision
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            try {
                val rendered = withContext(Dispatchers.Default) { renderImageBase64(result.rawBase64, result.mimeType, options) }
                if (renderRevision == owner) state.update { it.copy(output = rendered, renderingOutput = false) }
            } catch (_: OutOfMemoryError) {
                if (renderRevision == owner) state.update {
                    it.copy(renderingOutput = false, output = "", error = ImageBase64Error.MEMORY_LIMIT)
                }
            }
        }
    }

    private fun cancelExports() {
        saveJob?.cancel()
        shareJob?.cancel()
        saveJob = null
        shareJob = null
    }

    companion object {
        fun factory(
            encode: LoadImageBase64UseCase,
            decode: DecodeImageBase64UseCase,
            saveText: SaveImageBase64TextUseCase,
            prepareShare: PrepareImageBase64ShareUseCase,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ImageBase64ViewModel(encode, decode, saveText, prepareShare) as T
        }
    }
}
