package com.cangshuo.toolbox.feature.storage.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.storage.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class StorageInfoUiState(val snapshot: StorageSnapshot? = null, val loading: Boolean = false, val failed: Boolean = false)
class StorageInfoViewModel(private val read: ReadStorageInfoUseCase) : ViewModel() {
    private val mutable = MutableStateFlow(StorageInfoUiState())
    val state = mutable.asStateFlow()
    fun refresh() {
        if (mutable.value.loading) return
        mutable.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            try { val result = read(); ensureActive(); mutable.update { it.copy(snapshot = result) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(failed = true) } }
            finally { mutable.update { it.copy(loading = false) } }
        }
    }
    companion object { fun factory(read: ReadStorageInfoUseCase) = viewModelFactory { initializer { StorageInfoViewModel(read) } } }
}
