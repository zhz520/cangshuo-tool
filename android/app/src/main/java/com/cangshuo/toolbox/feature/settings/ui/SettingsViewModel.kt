package com.cangshuo.toolbox.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.settings.domain.SettingsCacheUseCases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsCacheState(val sizeBytes: Long? = null, val busy: Boolean = false, val failed: Boolean = false, val cleared: Boolean = false)

class SettingsViewModel(private val useCases: SettingsCacheUseCases) : ViewModel() {
    private val mutable = MutableStateFlow(SettingsCacheState())
    val state = mutable.asStateFlow()
    init { viewModelScope.launch { try { mutable.update { it.copy(sizeBytes=useCases.sizeBytes()) } }
        catch(cancelled: CancellationException) {throw cancelled}
        catch(_: Exception) {mutable.update {it.copy(failed=true)}} } }
    fun clear() {
        if(state.value.busy) return
        mutable.update {it.copy(busy=true,failed=false,cleared=false)}
        viewModelScope.launch {
            try {useCases.clear(); val size=useCases.sizeBytes(); mutable.update {it.copy(sizeBytes=size,cleared=true)}}
            catch(cancelled: CancellationException) {throw cancelled}
            catch(_: Exception) {mutable.update {it.copy(failed=true)}}
            finally {mutable.update {it.copy(busy=false)}}
        }
    }
    companion object {fun factory(useCases: SettingsCacheUseCases)=viewModelFactory {initializer {SettingsViewModel(useCases)}}}
}
