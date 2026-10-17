package com.cangshuo.toolbox.feature.purecolor.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.purecolor.domain.PureColorError
import com.cangshuo.toolbox.feature.purecolor.domain.PureColorException
import com.cangshuo.toolbox.feature.purecolor.domain.PureColorLimits
import com.cangshuo.toolbox.feature.purecolor.domain.PureColorParams
import com.cangshuo.toolbox.feature.purecolor.domain.SavePureColorUseCase
import com.cangshuo.toolbox.feature.purecolor.domain.SharePureColorUseCase
import com.cangshuo.toolbox.feature.purecolor.domain.parseHexColor
import com.cangshuo.toolbox.feature.purecolor.domain.validatePureColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PureColorMessage { SAVED, SAVE_FAILED, SHARE_FAILED }

data class PureColorUiState(
    val colorInput: String = "#1677FF",
    val widthInput: String = "1080",
    val heightInput: String = "1080",
    val saving: Boolean = false,
    val sharing: Boolean = false,
    val message: PureColorMessage? = null,
    val shareRequest: Uri? = null,
) {
    val argb: Int? get() = parseHexColor(colorInput)
    val width: Int? get() = widthInput.toIntOrNull()
    val height: Int? get() = heightInput.toIntOrNull()
    val valid: Boolean get() {
        val a = argb
        val w = width
        val h = height
        return a != null && w != null && h != null &&
            w in 1..PureColorLimits.MAX_EDGE && h in 1..PureColorLimits.MAX_EDGE &&
            w.toLong() * h <= PureColorLimits.MAX_PIXELS
    }
    val busy: Boolean get() = saving || sharing
}

class PureColorViewModel(
    private val save: SavePureColorUseCase,
    private val share: SharePureColorUseCase,
    private val stateHandle: SavedStateHandle,
) : ViewModel() {
    private val state = MutableStateFlow(
        PureColorUiState(
            colorInput = stateHandle[KEY_COLOR] ?: "#1677FF",
            widthInput = stateHandle[KEY_WIDTH] ?: "1080",
            heightInput = stateHandle[KEY_HEIGHT] ?: "1080",
        )
    )
    val uiState = state.asStateFlow()
    private var job: Job? = null

    fun updateColor(value: String) {
        if (state.value.busy) return
        val limited = value.take(9)
        stateHandle[KEY_COLOR] = limited
        state.update { it.copy(colorInput = limited, message = null) }
    }

    fun updateWidth(value: String) = updateDimension(value, Key.WIDTH)
    fun updateHeight(value: String) = updateDimension(value, Key.HEIGHT)

    fun setPreset(width: Int, height: Int) {
        if (state.value.busy) return
        stateHandle[KEY_WIDTH] = width.toString()
        stateHandle[KEY_HEIGHT] = height.toString()
        state.update { it.copy(widthInput = width.toString(), heightInput = height.toString(), message = null) }
    }

    fun onSave(destination: Uri?) {
        val params = paramsOrNull() ?: return
        if (state.value.busy) return
        job?.cancel()
        state.update { it.copy(saving = true, message = null) }
        job = viewModelScope.launch {
            val result = try { save(params, destination) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
                Result.failure(PureColorException(PureColorError.SAVE_FAILED))
            }
            state.update { it.copy(saving = false, message = if (result.isSuccess) PureColorMessage.SAVED else PureColorMessage.SAVE_FAILED) }
        }
    }

    fun onShare() {
        val params = paramsOrNull() ?: return
        if (state.value.busy) return
        job?.cancel()
        state.update { it.copy(sharing = true, message = null) }
        job = viewModelScope.launch {
            val result = try { share(params) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
                Result.failure(PureColorException(PureColorError.SHARE_FAILED))
            }
            state.update {
                it.copy(sharing = false, shareRequest = result.getOrNull(),
                    message = if (result.isSuccess) it.message else PureColorMessage.SHARE_FAILED)
            }
        }
    }

    fun onShareHandled(success: Boolean) = state.update {
        if (it.shareRequest == null) it else it.copy(shareRequest = null,
            message = if (success) it.message else PureColorMessage.SHARE_FAILED)
    }

    fun onMessageConsumed() = state.update { it.copy(message = null) }

    private fun paramsOrNull(): PureColorParams? {
        val current = state.value
        val argb = current.argb ?: return null
        val width = current.width ?: return null
        val height = current.height ?: return null
        return try {
            PureColorParams(argb, width, height).also { validatePureColor(it, Runtime.getRuntime().maxMemory()) }
        } catch (_: PureColorException) { null }
    }

    private fun updateDimension(value: String, key: Key) {
        if (state.value.busy) return
        val limited = value.filter { it.isDigit() }.take(4)
        when (key) {
            Key.WIDTH -> { stateHandle[KEY_WIDTH] = limited; state.update { it.copy(widthInput = limited, message = null) } }
            Key.HEIGHT -> { stateHandle[KEY_HEIGHT] = limited; state.update { it.copy(heightInput = limited, message = null) } }
        }
    }

    private enum class Key { WIDTH, HEIGHT }

    companion object {
        private const val KEY_COLOR = "color"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"

        fun factory(save: SavePureColorUseCase, share: SharePureColorUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    PureColorViewModel(save, share, extras.createSavedStateHandle()) as T
            }
    }
}
