package com.cangshuo.toolbox.feature.colorpicker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cangshuo.toolbox.feature.colorpicker.domain.ColorPickerError
import com.cangshuo.toolbox.feature.colorpicker.domain.ColorPickerException
import com.cangshuo.toolbox.feature.colorpicker.domain.LoadImageUseCase
import com.cangshuo.toolbox.feature.colorpicker.domain.LoadedImage
import com.cangshuo.toolbox.feature.colorpicker.domain.PickedColor
import com.cangshuo.toolbox.feature.colorpicker.domain.SampleColorUseCase
import com.cangshuo.toolbox.feature.colorpicker.domain.SamplePoint
import com.cangshuo.toolbox.feature.colorpicker.domain.pushPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ColorPickerUiState(
    val loading: Boolean = false,
    val image: LoadedImage? = null,
    val point: SamplePoint? = null,
    val selected: PickedColor? = null,
    val palette: List<PickedColor> = emptyList(),
    val error: ColorPickerError? = null,
    val cancelled: Boolean = false,
)

class ColorPickerViewModel(
    private val loadImage: LoadImageUseCase,
    private val sampleColor: SampleColorUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(ColorPickerUiState())
    val uiState = state.asStateFlow()
    private var job: Job? = null
    private var revision = 0L
    private var lastSource: String? = null

    fun onImagePicked(uri: String?) {
        if (uri == null) return
        lastSource = uri
        revision++
        val owner = revision
        job?.cancel()
        state.update { it.copy(loading = true, error = null, cancelled = false) }
        job = viewModelScope.launch {
            try {
                val result = loadImage(uri)
                if (revision != owner) return@launch
                result.fold(
                onSuccess = { loaded ->
                    state.update {
                        it.copy(loading = false, image = loaded, point = null, selected = null, error = null, palette = emptyList())
                    }
                },
                onFailure = { failure ->
                    val reason = (failure as? ColorPickerException)?.reason ?: ColorPickerError.LOAD_FAILED
                    state.update { it.copy(loading = false, error = reason) }
                },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (revision == owner) state.update { it.copy(error = ColorPickerError.LOAD_FAILED) }
            } finally {
                if (revision == owner) state.update { it.copy(loading = false) }
            }
        }
    }

    fun onSample(point: SamplePoint?) {
        if (point == null) return
        if (state.value.loading) return
        val image = state.value.image ?: return
        if (point.x !in 0 until image.width || point.y !in 0 until image.height) return
        val color = sampleColor(image, point)
        state.update { it.copy(point = point, selected = color, palette = pushPalette(it.palette, color)) }
    }

    fun onPaletteSelected(color: PickedColor) {
        if (!state.value.loading && color in state.value.palette) state.update { it.copy(selected = color, point = null) }
    }

    fun onRetry() { lastSource?.let(::onImagePicked) }
    fun onPickerUnavailable() = state.update { it.copy(error = ColorPickerError.PICKER_UNAVAILABLE) }
    fun onCancelLoad() {
        revision++
        job?.cancel()
        job = null
        state.update { it.copy(loading = false, cancelled = true, error = null) }
    }

    fun onClearImage() {
        revision++
        job?.cancel()
        job = null
        lastSource = null
        state.value = ColorPickerUiState()
    }

    fun onClearPalette() = state.update { it.copy(palette = emptyList()) }

    companion object {
        fun factory(loadImage: LoadImageUseCase, sampleColor: SampleColorUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ColorPickerViewModel(loadImage, sampleColor) as T
            }
    }
}
