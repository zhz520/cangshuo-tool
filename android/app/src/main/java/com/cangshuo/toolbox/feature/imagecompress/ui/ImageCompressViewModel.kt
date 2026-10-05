package com.cangshuo.toolbox.feature.imagecompress.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.feature.imagecompress.domain.CompressImageUseCase
import com.cangshuo.toolbox.feature.imagecompress.domain.CompressParams
import com.cangshuo.toolbox.feature.imagecompress.domain.CompressResult
import com.cangshuo.toolbox.feature.imagecompress.domain.DiscardImageUseCase
import com.cangshuo.toolbox.feature.imagecompress.domain.ImageExportRequest
import com.cangshuo.toolbox.feature.imagecompress.domain.ImageFailure
import com.cangshuo.toolbox.feature.imagecompress.domain.ImageProcessingException
import com.cangshuo.toolbox.feature.imagecompress.domain.ImageShareRequest
import com.cangshuo.toolbox.feature.imagecompress.domain.OutputImageFormat
import com.cangshuo.toolbox.feature.imagecompress.domain.PrepareImageShareUseCase
import com.cangshuo.toolbox.feature.imagecompress.domain.ReadImageMetadataUseCase
import com.cangshuo.toolbox.feature.imagecompress.domain.ResizeMode
import com.cangshuo.toolbox.feature.imagecompress.domain.SaveImageUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ImageCompressViewModel(
    private val readMetadataUseCase: ReadImageMetadataUseCase,
    private val compressImageUseCase: CompressImageUseCase,
    private val saveImageUseCase: SaveImageUseCase,
    private val prepareShareUseCase: PrepareImageShareUseCase,
    private val discardImageUseCase: DiscardImageUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImageCompressUiState())
    val uiState: StateFlow<ImageCompressUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var compressJob: Job? = null
    private var imageVersion = 0L
    private var compressionVersion = 0L
    private var pendingDocumentExport: CompressResult? = null
    private val isExporting: Boolean get() = _uiState.value.isSaving || _uiState.value.isSharing

    fun onImageSelected(uri: Uri) {
        if (isExporting) return
        cancelImageWork()
        val version = imageVersion
        _uiState.value.result?.let(discardImageUseCase::invoke)
        _uiState.value = ImageCompressUiState(selectedUri = uri, compressParams = _uiState.value.compressParams, isLoadingImage = true)
        loadJob = viewModelScope.launch {
            val loaded = readMetadataUseCase(uri)
            if (version != imageVersion || _uiState.value.selectedUri != uri) return@launch
            loaded.onSuccess { (meta, thumb) ->
                _uiState.update { it.copy(originalMetadata = meta, originalPreviewBitmap = thumb, isLoadingImage = false) }
                executeCompress(uri, _uiState.value.compressParams)
            }.onFailure { error ->
                _uiState.update { it.copy(isLoadingImage = false, userMessageRes = messageFor(error, R.string.image_compress_error_load)) }
            }
        }
    }

    fun onQualityChanged(quality: Int) = updateParams(_uiState.value.compressParams.copy(quality = quality.coerceIn(1, 100)))
    fun onResizeModeChanged(mode: ResizeMode) = updateParams(_uiState.value.compressParams.copy(resizeMode = mode))
    fun onScalePercentChanged(percent: Int) = updateParams(_uiState.value.compressParams.copy(scalePercent = percent.coerceIn(10, 100)))
    fun onMaxDimensionChanged(dim: Int) = updateParams(_uiState.value.compressParams.copy(maxDimension = dim.coerceIn(100, 8192)))
    fun onFormatChanged(format: OutputImageFormat) = updateParams(_uiState.value.compressParams.copy(outputFormat = format))

    private fun updateParams(params: CompressParams) {
        if (isExporting || params == _uiState.value.compressParams) return
        _uiState.update { it.copy(compressParams = params) }
        val state = _uiState.value
        // A pending metadata job uses the newest parameters when it completes.
        if (state.isLoadingImage || state.originalMetadata == null) return
        executeCompress(state.selectedUri ?: return, params, debounce = true)
    }

    private fun executeCompress(uri: Uri, params: CompressParams, debounce: Boolean = false) {
        compressJob?.cancel()
        val request = ++compressionVersion
        val selection = imageVersion
        _uiState.value.result?.let(discardImageUseCase::invoke)
        _uiState.update { it.copy(result = null, isCompressing = true, userMessageRes = null, isSuccessMessage = false) }
        compressJob = viewModelScope.launch {
            // Merge rapid slider changes; this is input debouncing, not an artificial loading delay.
            if (debounce) delay(150)
            val compressed = compressImageUseCase(uri, params)
            if (request != compressionVersion || selection != imageVersion || _uiState.value.selectedUri != uri) {
                compressed.getOrNull()?.let(discardImageUseCase::invoke)
                return@launch
            }
            compressed.onSuccess { result ->
                _uiState.update { it.copy(result = result, originalMetadata = result.originalMetadata, isCompressing = false) }
            }.onFailure { error ->
                _uiState.update { it.copy(isCompressing = false, userMessageRes = messageFor(error, R.string.image_compress_error_compress)) }
            }
        }
    }

    fun onSaveRequested(useDocument: Boolean): ImageExportRequest? {
        val state = _uiState.value
        if (!canExport(state)) return null
        val result = state.result ?: return null
        _uiState.update { it.copy(isSaving = true, userMessageRes = null) }
        return if (useDocument) {
            pendingDocumentExport = result
            ImageExportRequest(result.compressedMetadata.fileName, result.compressedMetadata.mimeType)
        } else {
            save(result, null)
            null
        }
    }

    fun onDocumentCreated(uri: Uri?) {
        val result = pendingDocumentExport
        pendingDocumentExport = null
        if (uri == null) {
            _uiState.update { it.copy(isSaving = false) }
        } else if (result == null) {
            onExportUnavailable()
        } else {
            save(result, uri)
        }
    }

    fun onExportUnavailable() {
        pendingDocumentExport = null
        _uiState.update { it.copy(isSaving = false, userMessageRes = R.string.image_compress_error_save, isSuccessMessage = false) }
    }

    private fun save(result: CompressResult, destination: Uri?) {
        viewModelScope.launch {
            val saved = saveImageUseCase(result, destination)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    userMessageRes = if (saved.isSuccess) {
                        if (destination == null) R.string.image_compress_saved_success else R.string.image_compress_document_saved
                    } else messageFor(saved.exceptionOrNull(), R.string.image_compress_error_save),
                    isSuccessMessage = saved.isSuccess,
                )
            }
        }
    }

    fun onShareRequested() {
        val state = _uiState.value
        if (!canExport(state)) return
        val result = state.result ?: return
        _uiState.update { it.copy(isSharing = true, userMessageRes = null) }
        viewModelScope.launch {
            val prepared = prepareShareUseCase(result)
            prepared.onSuccess { uri ->
                _uiState.update { it.copy(shareRequest = ImageShareRequest(uri, result.compressedMetadata.mimeType)) }
            }.onFailure { error ->
                _uiState.update { it.copy(isSharing = false, userMessageRes = messageFor(error, R.string.image_compress_error_share), isSuccessMessage = false) }
            }
        }
    }

    fun onShareHandled(success: Boolean) {
        _uiState.update {
            it.copy(isSharing = false, shareRequest = null, userMessageRes = if (success) null else R.string.image_compress_error_share, isSuccessMessage = false)
        }
    }

    fun onPreviewTabChanged(tab: PreviewTab) { _uiState.update { it.copy(previewTab = tab) } }

    fun onClear() {
        if (isExporting) return
        cancelImageWork()
        _uiState.value.result?.let(discardImageUseCase::invoke)
        _uiState.value = ImageCompressUiState()
    }

    fun onDismissMessage() { _uiState.update { it.copy(userMessageRes = null) } }

    fun onRetry() {
        if (isExporting) return
        val state = _uiState.value
        val uri = state.selectedUri ?: return
        if (state.originalMetadata == null) onImageSelected(uri) else executeCompress(uri, state.compressParams)
    }

    private fun cancelImageWork() {
        imageVersion++
        compressionVersion++
        loadJob?.cancel()
        compressJob?.cancel()
    }

    private fun canExport(state: ImageCompressUiState): Boolean =
        state.result != null && !state.isLoadingImage && !state.isCompressing && !isExporting

    private fun messageFor(error: Throwable?, fallback: Int): Int = when ((error as? ImageProcessingException)?.reason) {
        ImageFailure.INVALID_IMAGE, ImageFailure.UNSUPPORTED_FORMAT -> R.string.image_compress_error_load
        ImageFailure.TOO_LARGE, ImageFailure.MEMORY_LIMIT -> R.string.image_compress_error_large
        ImageFailure.OUTPUT_TOO_LARGE -> R.string.image_compress_error_output_large
        ImageFailure.CACHE_FULL -> R.string.image_compress_error_cache
        else -> fallback
    }

    override fun onCleared() {
        cancelImageWork()
        _uiState.value.result?.let(discardImageUseCase::invoke)
        super.onCleared()
    }

    companion object {
        fun factory(
            readMetadataUseCase: ReadImageMetadataUseCase,
            compressImageUseCase: CompressImageUseCase,
            saveImageUseCase: SaveImageUseCase,
            prepareShareUseCase: PrepareImageShareUseCase,
            discardImageUseCase: DiscardImageUseCase,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ImageCompressViewModel(readMetadataUseCase, compressImageUseCase, saveImageUseCase, prepareShareUseCase, discardImageUseCase) as T
        }
    }
}
