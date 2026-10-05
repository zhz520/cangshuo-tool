package com.cangshuo.toolbox.feature.sensors.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.sensors.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SensorsUiState(val catalog: SensorCatalog? = null, val selected: Int? = null, val reading: SensorReading? = null,
    val loading: Boolean = false, val failed: Boolean = false, val paused: Boolean = false)
class SensorsViewModel(private val cases: SensorsUseCases) : ViewModel() {
    private val mutable = MutableStateFlow(SensorsUiState())
    val state = mutable.asStateFlow()
    private var active = false
    private var sampling: Job? = null
    init { refresh() }
    fun refresh() {
        if (mutable.value.loading) return
        sampling?.cancel(); sampling = null
        mutable.update { it.copy(loading = true, failed = false, reading = null) }
        viewModelScope.launch {
            try {
                val catalog = cases.catalog(); ensureActive()
                val selected = catalog.sensors.firstOrNull { it.liveSupported }?.id
                mutable.update { it.copy(catalog = catalog, selected = selected, loading = false) }
                subscribe()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(loading = false, failed = true) } }
        }
    }
    fun start() { active = true; subscribe() }
    fun stop() { active = false; sampling?.cancel(); sampling = null }
    fun select(id: Int) {
        if (mutable.value.loading) return
        sampling?.cancel(); sampling = null
        mutable.update { it.copy(selected = id, reading = null, failed = false) }
        subscribe()
    }
    fun togglePause() {
        mutable.update { it.copy(paused = !it.paused) }
        sampling?.cancel(); sampling = null; subscribe()
    }
    private fun subscribe() {
        if (!active || mutable.value.paused || sampling?.isActive == true || mutable.value.loading) return
        val descriptor = mutable.value.catalog?.sensors?.firstOrNull { it.id == mutable.value.selected } ?: return
        if (!descriptor.liveSupported) return
        mutable.update { it.copy(reading = null, failed = false) }
        sampling = viewModelScope.launch {
            try { cases.observe(descriptor.id).collect { reading -> ensureActive(); mutable.update { it.copy(reading = reading) } } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(failed = true) } }
        }
    }
    companion object { fun factory(cases: SensorsUseCases) = viewModelFactory { initializer { SensorsViewModel(cases) } } }
}
