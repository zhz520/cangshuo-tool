package com.cangshuo.toolbox.feature.noise.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.noise.domain.NoisePreset
import com.cangshuo.toolbox.feature.noise.domain.NoiseRepository
import com.cangshuo.toolbox.feature.noise.domain.NoiseType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class NoiseViewModel(private val repository: NoiseRepository) : ViewModel() {
    val state: StateFlow<NoiseUiState> = combine(repository.settings, repository.playback) { settings, playback ->
        NoiseUiState(settings, playback)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoiseUiState())

    fun toggleLayer(type: NoiseType) = repository.toggleLayer(type)
    fun setLevel(type: NoiseType, level: Float) = repository.setLayerLevel(type, level)
    fun setMaster(level: Float) = repository.setMasterVolume(level)
    fun setTimer(minutes: Int) = repository.setTimerMinutes(minutes)
    fun applyPreset(preset: NoisePreset) = repository.applyPreset(preset)
    fun start() = repository.start()
    fun pause() = repository.pause()
    fun resume() = repository.resume()
    fun stop() = repository.stop()
    fun refreshPlayback() = repository.refresh()

    companion object {
        fun factory(repository: NoiseRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                NoiseViewModel(repository) as T
        }
    }
}
