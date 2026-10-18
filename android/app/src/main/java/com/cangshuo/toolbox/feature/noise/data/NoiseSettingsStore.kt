package com.cangshuo.toolbox.feature.noise.data

import android.content.Context
import androidx.core.content.edit
import com.cangshuo.toolbox.feature.noise.domain.NoisePolicy
import com.cangshuo.toolbox.feature.noise.domain.NoiseSettings
import com.cangshuo.toolbox.feature.noise.domain.NoiseType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NoiseSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val settings: StateFlow<NoiseSettings> = mutable.asStateFlow()

    fun read(): NoiseSettings {
        val levels = NoiseType.entries.associateWith { type ->
            preferences.getFloat(levelKey(type), if (type == NoiseType.WHITE) 0.55f else 0f)
                .let(NoisePolicy::safeLevel)
        }
        return NoiseSettings(
            levels = levels,
            masterVolume = NoisePolicy.safeLevel(preferences.getFloat(KEY_MASTER, 0.65f)),
            timerMinutes = preferences.getInt(KEY_TIMER, 0).takeIf { it in NoisePolicy.timerOptions } ?: 0,
        )
    }

    fun update(settings: NoiseSettings) {
        val normalized = settings.copy(
            levels = settings.levels.mapValues { NoisePolicy.safeLevel(it.value) },
            masterVolume = NoisePolicy.safeLevel(settings.masterVolume),
            timerMinutes = settings.timerMinutes.takeIf { it in NoisePolicy.timerOptions } ?: 0,
        )
        preferences.edit {
            NoiseType.entries.forEach { type -> putFloat(levelKey(type), normalized.levels[type] ?: 0f) }
            putFloat(KEY_MASTER, normalized.masterVolume)
            putInt(KEY_TIMER, normalized.timerMinutes)
        }
        mutable.value = normalized
    }

    private fun levelKey(type: NoiseType) = "level_" + type.name.lowercase()

    private companion object {
        const val PREFERENCES = "noise_soundscape_v1"
        const val KEY_MASTER = "master_volume"
        const val KEY_TIMER = "timer_minutes"
    }
}
