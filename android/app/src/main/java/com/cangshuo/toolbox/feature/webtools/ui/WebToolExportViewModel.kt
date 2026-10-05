package com.cangshuo.toolbox.feature.webtools.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.webtools.domain.DecodeWebToolExportUseCase
import com.cangshuo.toolbox.feature.webtools.domain.SaveWebToolExportUseCase
import com.cangshuo.toolbox.feature.webtools.domain.WebToolExport
import com.cangshuo.toolbox.feature.webtools.domain.WebToolExportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WebExportMessage { NONE, SAVED, FAILED }
data class WebExportUiState(val busy: Boolean = false, val pending: WebToolExport? = null, val pickerRequested: Boolean = false, val message: WebExportMessage = WebExportMessage.NONE)

class WebToolExportViewModel(private val save: SaveWebToolExportUseCase, private val worker: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val mutableState = MutableStateFlow(WebExportUiState())
    val state = mutableState.asStateFlow()

    fun request(dataUrl: String) {
        if (mutableState.value.busy) return
        mutableState.value = WebExportUiState(busy = true)
        viewModelScope.launch {
            try {
                val export = withContext(worker) { DecodeWebToolExportUseCase(dataUrl) }
                mutableState.value = WebExportUiState(busy = true, pending = export)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: WebToolExportException) {
                mutableState.value = WebExportUiState(message = WebExportMessage.FAILED)
            }
        }
    }

    fun complete(destination: String?) {
        val export = mutableState.value.pending ?: return
        mutableState.value = WebExportUiState(busy = destination != null)
        if (destination == null) return
        viewModelScope.launch {
            try {
                save(destination, export)
                mutableState.value = WebExportUiState(message = WebExportMessage.SAVED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: WebToolExportException) {
                mutableState.value = WebExportUiState(message = WebExportMessage.FAILED)
            }
        }
    }

    fun acknowledgeMessage() { mutableState.value = mutableState.value.copy(message = WebExportMessage.NONE) }
    fun markPickerRequested() { mutableState.value = mutableState.value.copy(pickerRequested = true) }

    companion object {
        fun factory(save: SaveWebToolExportUseCase): ViewModelProvider.Factory = viewModelFactory { initializer { WebToolExportViewModel(save) } }
    }
}
