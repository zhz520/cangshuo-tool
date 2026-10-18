package com.cangshuo.toolbox.feature.noise

import com.cangshuo.toolbox.feature.noise.domain.NoiseError
import com.cangshuo.toolbox.feature.noise.domain.NoiseLayerState
import com.cangshuo.toolbox.feature.noise.domain.NoisePlaybackPolicy
import com.cangshuo.toolbox.feature.noise.domain.NoisePolicy
import com.cangshuo.toolbox.feature.noise.domain.NoisePreset
import com.cangshuo.toolbox.feature.noise.domain.NoiseSettings
import com.cangshuo.toolbox.feature.noise.domain.NoiseSynthesis
import com.cangshuo.toolbox.feature.noise.domain.NoiseType
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoisePolicyTest {
    @Test fun defaultsAndLayerTogglesAreSafe() {
        val defaults = NoiseSettings()
        assertEquals(listOf(NoiseType.WHITE), defaults.activeTypes)
        val added = NoisePolicy.toggled(defaults, NoiseType.RAIN)
        assertTrue(NoisePolicy.activeTypes(added.levels).containsAll(listOf(NoiseType.WHITE, NoiseType.RAIN)))
        val removed = NoisePolicy.toggled(added, NoiseType.WHITE)
        assertEquals(0f, removed.levels[NoiseType.WHITE])
        assertEquals(listOf(NoiseType.RAIN), removed.activeTypes)
    }

    @Test fun presetsAndValidationNormalizeLevels() {
        val focus = NoisePolicy.preset(NoisePreset.FOCUS)
        assertEquals(listOf(NoiseType.BROWN, NoiseType.FAN), focus.activeTypes)
        assertEquals(60, focus.timerMinutes)
        val rain = NoisePolicy.preset(NoisePreset.RAIN)
        assertTrue(rain.activeTypes.containsAll(listOf(NoiseType.RAIN, NoiseType.OCEAN)))
        val invalid = NoiseSettings(
            levels = mapOf(NoiseType.WHITE to -1f, NoiseType.RAIN to Float.NaN),
            masterVolume = 2f,
            timerMinutes = 17,
        )
        val validated = NoisePolicy.validate(invalid)
        assertEquals(NoiseError.INVALID_TIMER, validated.error)
        val normalized = NoisePolicy.validate(NoiseSettings(levels = mapOf(NoiseType.WHITE to 2f)))
        assertEquals(1f, normalized.normalized.levels[NoiseType.WHITE])
        assertEquals(NoiseError.NO_LAYER, NoisePolicy.validate(NoiseSettings(levels = emptyMap())).error)
    }

    @Test fun allTimersAndPresetBoundsAreValid() {
        NoisePolicy.timerOptions.forEach { minutes ->
            assertNull(NoisePolicy.validate(NoiseSettings(timerMinutes = minutes)).error)
        }
        assertEquals(0, NoisePolicy.preset(NoisePreset.RAIN).timerMinutes % 15)
        assertTrue(NoisePolicy.mixWeight(mapOf(NoiseType.WHITE to 0.2f, NoiseType.RAIN to 0.3f)) >= 1.0)
    }

    @Test fun eachSynthesizerStaysBoundedAndNonSilent() {
        NoiseType.entries.forEach { type ->
            val state = NoiseLayerState(NoiseSynthesis.seedFor(type))
            val samples = FloatArray(NoiseSynthesis.RENDER_CHUNK_FRAMES * 4) {
                state.next(type, NoiseSynthesis.SAMPLE_RATE)
            }
            assertTrue(samples.all { it.isFinite() })
            assertTrue(samples.all { abs(it) <= 0.95f })
            assertTrue(NoiseSynthesis.rms(samples) > 0.001)
        }
    }

    @Test fun mixingIsDeterministicAndMasterVolumeBounded() {
        val settings = NoiseSettings(
            levels = mapOf(NoiseType.WHITE to 0.5f, NoiseType.PINK to 0.4f, NoiseType.RAIN to 0.3f),
            masterVolume = 0.8f,
        )
        val firstStates = mutableMapOf<NoiseType, NoiseLayerState>()
        val secondStates = mutableMapOf<NoiseType, NoiseLayerState>()
        val first = NoiseSynthesis.renderMixed(settings, firstStates)
        val second = NoiseSynthesis.renderMixed(settings, secondStates)
        assertTrue(first.contentEquals(second))
        assertTrue(first.all { it.isFinite() && abs(it) <= 1f })
        val quiet = NoiseSynthesis.renderMixed(settings.copy(masterVolume = 0f), mutableMapOf())
        assertTrue(quiet.all { it == 0f })
        val empty = NoiseSynthesis.renderMixed(NoiseSettings(levels = emptyMap()), mutableMapOf())
        assertTrue(empty.all { it == 0f })
    }

    @Test fun monotonicTimerAndSleepFadeHaveExactBoundaries() {
        assertNull(NoisePlaybackPolicy.timerMillis(0))
        assertNull(NoisePlaybackPolicy.timerMillis(17))
        assertEquals(900_000L, NoisePlaybackPolicy.timerMillis(15))
        assertEquals(10_800_000L, NoisePlaybackPolicy.timerMillis(180))

        val deadline = NoisePlaybackPolicy.deadline(16_000L, 5_000L)
        assertEquals(21_000L, deadline)
        assertEquals(16_000L, NoisePlaybackPolicy.remaining(deadline, 5_000L))
        assertEquals(0L, NoisePlaybackPolicy.remaining(deadline, 30_000L))
        assertEquals(16L, NoisePlaybackPolicy.remainingSeconds(15_001L))
        assertEquals(0L, NoisePlaybackPolicy.remainingSeconds(0L))

        assertEquals(1f, NoisePlaybackPolicy.fadeMultiplier(null))
        assertEquals(1f, NoisePlaybackPolicy.fadeMultiplier(15_000L))
        assertEquals(0.5f, NoisePlaybackPolicy.fadeMultiplier(7_500L))
        assertEquals(0f, NoisePlaybackPolicy.fadeMultiplier(0L))
    }
}
