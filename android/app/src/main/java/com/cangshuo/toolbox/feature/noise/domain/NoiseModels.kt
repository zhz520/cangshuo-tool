package com.cangshuo.toolbox.feature.noise.domain

import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.flow.StateFlow

enum class NoiseType {
    WHITE, PINK, BROWN, RAIN, OCEAN, FAN;
}

data class NoiseSettings(
    val levels: Map<NoiseType, Float> = mapOf(NoiseType.WHITE to 0.55f),
    val masterVolume: Float = 0.65f,
    val timerMinutes: Int = 0,
) {
    val activeTypes: List<NoiseType>
        get() = NoisePolicy.activeTypes(levels)
}

data class NoisePlaybackState(
    val playing: Boolean = false,
    val paused: Boolean = false,
    val remainingSeconds: Long? = null,
    val error: NoiseError? = null,
) {
    val idle: Boolean get() = !playing && !paused
}

enum class NoisePreset { FOCUS, RAIN, SLEEP, NONE }

data class NoiseValidation(val normalized: NoiseSettings, val error: NoiseError? = null)

enum class NoiseError { NO_LAYER, INVALID_LEVEL, INVALID_TIMER, AUDIO_UNAVAILABLE }

interface NoiseRepository {
    val settings: StateFlow<NoiseSettings>
    val playback: StateFlow<NoisePlaybackState>

    fun toggleLayer(type: NoiseType)
    fun setLayerLevel(type: NoiseType, level: Float)
    fun setMasterVolume(level: Float)
    fun setTimerMinutes(minutes: Int)
    fun applyPreset(preset: NoisePreset)
    fun start()
    fun pause()
    fun resume()
    fun stop()
    fun refresh()
}

object NoisePolicy {
    val timerOptions = listOf(0, 15, 30, 60, 90, 180)
    const val MIN_LEVEL = 0f
    const val MAX_LEVEL = 1f
    const val DEFAULT_LAYER_LEVEL = 0.5f

    fun activeTypes(levels: Map<NoiseType, Float>): List<NoiseType> =
        NoiseType.entries.filter { (levels[it] ?: 0f) > 0.001f }

    fun validate(settings: NoiseSettings): NoiseValidation {
        if (settings.timerMinutes !in timerOptions && settings.timerMinutes != 0) {
            return NoiseValidation(settings, NoiseError.INVALID_TIMER)
        }
        if (settings.masterVolume !in MIN_LEVEL..MAX_LEVEL) {
            return NoiseValidation(settings, NoiseError.INVALID_LEVEL)
        }
        val levels = NoiseType.entries.associateWith { type ->
            val value = settings.levels[type] ?: 0f
            if (value.isNaN()) 0f else value.coerceIn(MIN_LEVEL, MAX_LEVEL)
        }
        val normalized = settings.copy(levels = levels)
        if (activeTypes(levels).isEmpty()) return NoiseValidation(normalized, NoiseError.NO_LAYER)
        return NoiseValidation(normalized)
    }

    fun preset(preset: NoisePreset): NoiseSettings = when (preset) {
        NoisePreset.FOCUS -> NoiseSettings(
            levels = mapOf(NoiseType.BROWN to 0.55f, NoiseType.FAN to 0.28f),
            masterVolume = 0.62f,
            timerMinutes = 60,
        )
        NoisePreset.RAIN -> NoiseSettings(
            levels = mapOf(NoiseType.RAIN to 0.65f, NoiseType.OCEAN to 0.35f),
            masterVolume = 0.65f,
            timerMinutes = 30,
        )
        NoisePreset.SLEEP -> NoiseSettings(
            levels = mapOf(NoiseType.PINK to 0.5f, NoiseType.BROWN to 0.35f),
            masterVolume = 0.55f,
            timerMinutes = 90,
        )
        NoisePreset.NONE -> NoiseSettings()
    }

    fun toggled(settings: NoiseSettings, type: NoiseType): NoiseSettings {
        val current = settings.levels[type] ?: 0f
        val next = if (current > 0.001f) 0f else DEFAULT_LAYER_LEVEL
        return settings.copy(levels = settings.levels + (type to next))
    }

    fun adjusted(settings: NoiseSettings, type: NoiseType, level: Float): NoiseSettings =
        settings.copy(levels = settings.levels + (type to level.coerceIn(MIN_LEVEL, MAX_LEVEL)))

    fun remainingMinutes(settings: NoiseSettings): Int = settings.timerMinutes

    fun mixWeight(levels: Map<NoiseType, Float>): Double {
        val sum = activeTypes(levels).sumOf { (levels[it] ?: 0f).toDouble() }
        return max(1.0, kotlin.math.sqrt(sum.coerceAtLeast(0.0)))
    }

    fun safeLevel(value: Float): Float = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)

    fun isFinite(value: Float): Boolean = value.isFinite() && abs(value) <= 10f
}

object NoisePlaybackPolicy {
    const val FADE_OUT_MILLIS = 15_000L

    fun timerMillis(minutes: Int): Long? =
        minutes.takeIf { it in NoisePolicy.timerOptions && it > 0 }?.toLong()?.times(60_000L)

    fun deadline(remainingMillis: Long?, nowMillis: Long): Long? =
        remainingMillis?.let { nowMillis + it }

    fun remaining(deadlineMillis: Long?, nowMillis: Long): Long? =
        deadlineMillis?.let { (it - nowMillis).coerceAtLeast(0L) }

    fun remainingSeconds(remainingMillis: Long?): Long? =
        remainingMillis?.let { (it.coerceAtLeast(0L) + 999L) / 1_000L }

    fun fadeMultiplier(remainingMillis: Long?): Float =
        remainingMillis?.let {
            (it.toDouble() / FADE_OUT_MILLIS).coerceIn(0.0, 1.0).toFloat()
        } ?: 1f
}
