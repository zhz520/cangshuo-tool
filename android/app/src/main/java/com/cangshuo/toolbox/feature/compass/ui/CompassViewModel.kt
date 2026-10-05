package com.cangshuo.toolbox.feature.compass.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.compass.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class CompassUiState(val reading: CompassReading? = null, val waiting: Boolean = true,
    val failed: Boolean = false, val unsupported: Boolean = false, val paused: Boolean = false)
class CompassViewModel(private val observe: ObserveCompassUseCase) : ViewModel() {
    private val mutable = MutableStateFlow(CompassUiState())
    val state = mutable.asStateFlow()
    private var active = false
    private var job: Job? = null
    private var timeout: Job? = null
    fun start() { active = true; subscribe() }
    fun stop() { active = false; cancel() }
    private fun cancel() { job?.cancel(); job = null; timeout?.cancel(); timeout = null }
    fun refresh() { cancel(); mutable.update { it.copy(paused = false) }; subscribe() }
    fun togglePause() { cancel(); mutable.update { it.copy(paused = !it.paused, waiting = false) }; subscribe() }
    private fun subscribe() {
        if (!active || mutable.value.paused || job?.isActive == true) return
        mutable.value = CompassUiState()
        timeout = viewModelScope.launch { delay(3000); mutable.update { it.copy(waiting = false) } }
        job = viewModelScope.launch {
            try { observe().collect { reading -> ensureActive(); timeout?.cancel(); mutable.value = CompassUiState(reading, waiting = false) } }
            catch (e: CancellationException) { throw e }
            catch (_: CompassUnavailable) { mutable.update { it.copy(waiting = false, unsupported = true) } }
            catch (_: Exception) { mutable.update { it.copy(waiting = false, failed = true) } }
        }
    }
    companion object { fun factory(observe: ObserveCompassUseCase) = viewModelFactory { initializer { CompassViewModel(observe) } } }
}
