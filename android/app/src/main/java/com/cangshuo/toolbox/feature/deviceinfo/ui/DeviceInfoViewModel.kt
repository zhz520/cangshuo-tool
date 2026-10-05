package com.cangshuo.toolbox.feature.deviceinfo.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.deviceinfo.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DeviceInfoUiState(val snapshot: DeviceSnapshot?=null,val loading: Boolean=false,val failed: Boolean=false)
class DeviceInfoViewModel(private val read: ReadDeviceInfoUseCase) : ViewModel() {
    private val mutable=MutableStateFlow(DeviceInfoUiState())
    val state=mutable.asStateFlow()
    init { refresh() }
    fun refresh() {
        if(mutable.value.loading) return
        mutable.update { it.copy(loading=true,failed=false) }
        viewModelScope.launch {
            try { val result=read();ensureActive();mutable.update { it.copy(snapshot=result) } }
            catch(e: CancellationException) { throw e }
            catch(_: Exception) { mutable.update { it.copy(failed=true) } }
            finally { mutable.update { it.copy(loading=false) } }
        }
    }
    companion object { fun factory(read: ReadDeviceInfoUseCase)=viewModelFactory { initializer { DeviceInfoViewModel(read) } } }
}
