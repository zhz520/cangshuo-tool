package com.cangshuo.toolbox.feature.noise.ui

import com.cangshuo.toolbox.feature.noise.domain.NoisePlaybackState
import com.cangshuo.toolbox.feature.noise.domain.NoiseSettings

data class NoiseUiState(
    val settings: NoiseSettings = NoiseSettings(),
    val playback: NoisePlaybackState = NoisePlaybackState(),
) {
    val hasActiveLayers: Boolean get() = settings.activeTypes.isNotEmpty()
    val isPlaying: Boolean get() = playback.playing
    val isPaused: Boolean get() = playback.paused
}
