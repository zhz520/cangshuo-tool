package com.cangshuo.toolbox.feature.noise.data

import android.content.Context
import com.cangshuo.toolbox.feature.noise.domain.NoisePolicy
import com.cangshuo.toolbox.feature.noise.domain.NoisePreset
import com.cangshuo.toolbox.feature.noise.domain.NoiseRepository
import com.cangshuo.toolbox.feature.noise.domain.NoiseSettings
import com.cangshuo.toolbox.feature.noise.domain.NoiseType
import com.cangshuo.toolbox.feature.noise.service.NoisePlaybackService
import kotlinx.coroutines.flow.StateFlow

class LocalNoiseRepository(context: Context) : NoiseRepository {
    private val appContext = context.applicationContext
    private val store = NoiseSettingsStore(appContext)
    override val settings: StateFlow<NoiseSettings> = store.settings
    override val playback: StateFlow<com.cangshuo.toolbox.feature.noise.domain.NoisePlaybackState> =
        NoisePlaybackRuntime.state

    override fun toggleLayer(type: NoiseType) {
        store.update(NoisePolicy.toggled(store.settings.value, type))
        refreshRunningPlayback()
    }

    override fun setLayerLevel(type: NoiseType, level: Float) {
        store.update(NoisePolicy.adjusted(store.settings.value, type, level))
        refreshRunningPlayback()
    }

    override fun setMasterVolume(level: Float) {
        store.update(store.settings.value.copy(masterVolume = NoisePolicy.safeLevel(level)))
        refreshRunningPlayback()
    }

    override fun setTimerMinutes(minutes: Int) {
        val safe = minutes.takeIf { it in NoisePolicy.timerOptions } ?: 0
        store.update(store.settings.value.copy(timerMinutes = safe))
        if (!NoisePlaybackRuntime.state.value.idle) {
            NoisePlaybackService.updateTimer(appContext, safe)
        }
    }

    override fun applyPreset(preset: NoisePreset) {
        store.update(NoisePolicy.preset(preset))
        refreshRunningPlayback()
        if (!NoisePlaybackRuntime.state.value.idle) {
            NoisePlaybackService.updateTimer(appContext, store.settings.value.timerMinutes)
        }
    }

    override fun start() {
        var current = store.settings.value
        if (current.activeTypes.isEmpty()) {
            current = current.copy(levels = current.levels + (NoiseType.WHITE to NoisePolicy.DEFAULT_LAYER_LEVEL))
            store.update(current)
        }
        NoisePlaybackService.start(appContext)
    }

    override fun pause() = NoisePlaybackService.pause(appContext)
    override fun resume() = NoisePlaybackService.resume(appContext)
    override fun stop() = NoisePlaybackService.stop(appContext)
    override fun refresh() = refreshRunningPlayback()

    private fun refreshRunningPlayback() {
        if (!NoisePlaybackRuntime.state.value.idle) NoisePlaybackService.reload(appContext)
    }
}
