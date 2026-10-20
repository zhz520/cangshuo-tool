package com.cangshuo.toolbox.feature.fish.data

import android.content.Context
import android.annotation.SuppressLint
import com.cangshuo.toolbox.feature.fish.domain.*

class LocalFishRepository(context: Context) : FishRepository {
    private val preferences = context.getSharedPreferences("wooden_fish", Context.MODE_PRIVATE)
    private var cached: FishState = read()

    override fun load(): FishState = cached
    override fun roll(today: String): FishState {
        val rolled = FishPolicy.rollDate(cached, today)
        if (rolled != cached) { cached = rolled; persist(cached, false) }
        return cached
    }
    override fun tap(today: String): FishState {
        cached = FishPolicy.registerTap(cached, today)
        persist(cached, false)
        return cached
    }
    override fun updateSettings(settings: FishSettings): FishState {
        require(settings.goal in FishPolicy.goals && settings.autoIntervalMs in FishPolicy.intervals)
        cached = cached.copy(settings = settings)
        persist(cached, true)
        return cached
    }
    override fun reset(): FishState { cached = FishState(); persist(cached, true); return cached }
    override fun flush() { persist(cached, true) }

    private fun read(): FishState {
        val unlocked = preferences.getString("unlocked", "")?.split('|')?.filter { it.isNotEmpty() }
            ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        val state = FishState(
            total = preferences.getLong("total", 0).coerceAtLeast(0),
            today = preferences.getInt("today", 0).coerceAtLeast(0),
            date = preferences.getString("date", "") ?: "",
            unlocked = unlocked,
            settings = FishSettings(
                sound = preferences.getBoolean("sound", true), haptic = preferences.getBoolean("haptic", true),
                motion = preferences.getBoolean("motion", true),
                goal = preferences.getInt("goal", 108).takeIf { it in FishPolicy.goals } ?: 108,
                autoIntervalMs = preferences.getLong("auto_interval", 1_000).takeIf { it in FishPolicy.intervals } ?: 1_000,
            )
        )
        return if (FishPolicy.valid(state)) state else FishState(date = state.date)
    }
    @SuppressLint("ApplySharedPref") // Background/reset paths deliberately wait for a durable flush.
    private fun persist(state: FishState, synchronously: Boolean) {
        val editor = preferences.edit().putLong("total", state.total).putInt("today", state.today)
            .putString("date", state.date).putString("unlocked", state.unlocked.sorted().joinToString("|"))
            .putBoolean("sound", state.settings.sound).putBoolean("haptic", state.settings.haptic)
            .putBoolean("motion", state.settings.motion).putInt("goal", state.settings.goal)
            .putLong("auto_interval", state.settings.autoIntervalMs)
        if (synchronously) editor.commit() else editor.apply()
    }
}
