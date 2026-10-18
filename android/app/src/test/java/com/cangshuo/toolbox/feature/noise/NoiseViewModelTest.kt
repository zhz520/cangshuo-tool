package com.cangshuo.toolbox.feature.noise

import com.cangshuo.toolbox.feature.noise.domain.NoisePlaybackState
import com.cangshuo.toolbox.feature.noise.domain.NoisePreset
import com.cangshuo.toolbox.feature.noise.domain.NoiseRepository
import com.cangshuo.toolbox.feature.noise.domain.NoiseSettings
import com.cangshuo.toolbox.feature.noise.domain.NoiseType
import com.cangshuo.toolbox.feature.noise.ui.NoiseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoiseViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeRepository(initial: NoiseSettings = NoiseSettings()) : NoiseRepository {
        val settingsFlow = MutableStateFlow(initial)
        val playbackFlow = MutableStateFlow(NoisePlaybackState())
        var started = 0
        var paused = 0
        var resumed = 0
        var stopped = 0
        var refreshed = 0
        var lastTimer = initial.timerMinutes
        var lastPreset: NoisePreset? = null
        override val settings: StateFlow<NoiseSettings> = settingsFlow
        override val playback: StateFlow<NoisePlaybackState> = playbackFlow

        override fun toggleLayer(type: NoiseType) {
            settingsFlow.value = settingsFlow.value.copy(
                levels = settingsFlow.value.levels + (type to 0.5f),
            )
        }
        override fun setLayerLevel(type: NoiseType, level: Float) {
            settingsFlow.value = settingsFlow.value.copy(levels = settingsFlow.value.levels + (type to level))
        }
        override fun setMasterVolume(level: Float) {
            settingsFlow.value = settingsFlow.value.copy(masterVolume = level)
        }
        override fun setTimerMinutes(minutes: Int) {
            lastTimer = minutes
            settingsFlow.value = settingsFlow.value.copy(timerMinutes = minutes)
        }
        override fun applyPreset(preset: NoisePreset) {
            lastPreset = preset
        }
        override fun start() { started++ }
        override fun pause() { paused++ }
        override fun resume() { resumed++ }
        override fun stop() { stopped++ }
        override fun refresh() { refreshed++ }
    }

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun stateCombinesSettingsAndPlaybackAndDelegatesActions() = runTest {
        val repo = FakeRepository(NoiseSettings(levels = mapOf(NoiseType.RAIN to 0.7f), timerMinutes = 30))
        val model = NoiseViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        runCurrent()
        assertEquals(listOf(NoiseType.RAIN), model.state.value.settings.activeTypes)
        assertTrue(model.state.value.playback.idle)

        repo.playbackFlow.value = NoisePlaybackState(playing = true, remainingSeconds = 42)
        runCurrent()
        assertTrue(model.state.value.isPlaying)
        assertEquals(42L, model.state.value.playback.remainingSeconds)

        model.toggleLayer(NoiseType.FAN)
        model.setLevel(NoiseType.FAN, 0.4f)
        model.setMaster(0.3f)
        model.setTimer(60)
        model.applyPreset(NoisePreset.SLEEP)
        model.start()
        model.pause()
        model.resume()
        model.stop()
        model.refreshPlayback()
        runCurrent()
        assertTrue(NoiseType.FAN in model.state.value.settings.activeTypes)
        assertEquals(0.3f, model.state.value.settings.masterVolume)
        assertEquals(60, repo.lastTimer)
        assertEquals(NoisePreset.SLEEP, repo.lastPreset)
        assertEquals(1, repo.started)
        assertEquals(1, repo.paused)
        assertEquals(1, repo.resumed)
        assertEquals(1, repo.stopped)
        assertEquals(1, repo.refreshed)
        advanceUntilIdle()
    }
}
