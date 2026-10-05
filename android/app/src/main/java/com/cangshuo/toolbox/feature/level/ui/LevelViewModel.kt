package com.cangshuo.toolbox.feature.level.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.level.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class LevelUiState(val reading: LevelReading? = null, val reference: LevelAngles? = null,
    val waiting: Boolean = true, val failed: Boolean = false, val unsupported: Boolean = false, val paused: Boolean = false)
class LevelViewModel(private val observe: ObserveLevelUseCase) : ViewModel() {
    private val mutable = MutableStateFlow(LevelUiState())
    val state = mutable.asStateFlow()
    private var active = false
    private var job: Job? = null
    private var timeout: Job? = null
    private var referenceRotation: Int? = null
    fun start() { active = true; subscribe() }
    fun stop() { active = false; cancel() }
    private fun cancel() { job?.cancel();job = null;timeout?.cancel();timeout = null }
    fun refresh() { cancel(); mutable.update { it.copy(paused = false) }; subscribe() }
    fun togglePause() { cancel();mutable.update { it.copy(paused = !it.paused, waiting = false) };subscribe() }
    fun zero() {
        mutable.value.reading?.takeIf { it.angles.faceUp }?.let { reading ->
            referenceRotation = reading.rotation
            mutable.update { it.copy(reference = reading.angles) }
        }
    }
    fun reset() { referenceRotation = null; mutable.update { it.copy(reference = null) } }
    private fun subscribe() {
        if (!active || mutable.value.paused || job?.isActive == true) return
        mutable.update { it.copy(reading = null, waiting = true, failed = false, unsupported = false) }
        timeout = viewModelScope.launch { delay(3000);mutable.update { it.copy(waiting = false) } }
        job = viewModelScope.launch {
            try { observe().collect { reading ->
                ensureActive();timeout?.cancel()
                if (reading != null && referenceRotation != null && reading.rotation != referenceRotation) reset()
                mutable.update { it.copy(reading = reading, waiting = false) }
            } }
            catch (e: CancellationException) { throw e }
            catch (_: LevelUnavailable) { mutable.update { it.copy(waiting = false, unsupported = true) } }
            catch (_: Exception) { mutable.update { it.copy(waiting = false, failed = true) } }
        }
    }
    companion object { fun factory(observe: ObserveLevelUseCase) = viewModelFactory { initializer { LevelViewModel(observe) } } }
}
