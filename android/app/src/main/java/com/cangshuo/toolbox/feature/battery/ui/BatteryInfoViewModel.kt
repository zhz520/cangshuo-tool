package com.cangshuo.toolbox.feature.battery.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.battery.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BatteryInfoUiState(val snapshot: BatterySnapshot? = null, val loading: Boolean = false, val failed: Boolean = false)
class BatteryInfoViewModel(private val observe: ObserveBatteryInfoUseCase) : ViewModel() {
    private val mutable = MutableStateFlow(BatteryInfoUiState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    fun start() {
        if (job?.isActive == true) return
        mutable.update { it.copy(loading = true, failed = false) }
        job = viewModelScope.launch {
            try { observe().collect { snapshot -> mutable.value = BatteryInfoUiState(snapshot) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(loading = false, failed = true) } }
        }
    }
    fun stop() { job?.cancel(); job = null; mutable.update { it.copy(loading = false) } }
    fun refresh() { stop(); start() }
    companion object { fun factory(observe: ObserveBatteryInfoUseCase) = viewModelFactory { initializer { BatteryInfoViewModel(observe) } } }
}
