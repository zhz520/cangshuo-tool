package com.cangshuo.toolbox.feature.fish.domain

import java.time.LocalDate

data class FishAchievement(val threshold: Long)
data class FishSettings(val sound: Boolean = true, val haptic: Boolean = true, val motion: Boolean = true,
    val goal: Int = 108, val autoIntervalMs: Long = 1_000)
data class FishState(val total: Long = 0, val today: Int = 0, val date: String = "",
    val unlocked: Set<Long> = emptySet(), val settings: FishSettings = FishSettings())

object FishPolicy {
    const val MAX_TOTAL = 9_999_999L
    const val MAX_TODAY = 999_999
    const val AUTO_MAX_MS = 300_000L
    val goals = listOf(21, 108, 1_080)
    val intervals = listOf(500L, 1_000L, 2_000L, 5_000L)
    val achievements = listOf(
        FishAchievement(1), FishAchievement(100), FishAchievement(500), FishAchievement(1_000),
        FishAchievement(3_000), FishAchievement(10_000), FishAchievement(30_000),
    )

    fun valid(state: FishState): Boolean = state.total in 0..MAX_TOTAL && state.today in 0..MAX_TODAY &&
        state.settings.goal in goals && state.settings.autoIntervalMs in intervals &&
        state.unlocked.all { value -> achievements.any { it.threshold == value } } &&
        (state.date.isEmpty() || runCatching { LocalDate.parse(state.date) }.isSuccess)

    fun rollDate(state: FishState, today: String): FishState {
        require(runCatching { LocalDate.parse(today) }.isSuccess)
        return if (state.date == today) state else state.copy(today = 0, date = today)
    }

    fun registerTap(state: FishState, today: String): FishState {
        require(valid(state))
        val current = rollDate(state, today)
        require(current.total < MAX_TOTAL && current.today < MAX_TODAY) { "Daily merit limit reached" }
        val total = current.total + 1
        val unlocked = current.unlocked + achievements.filter { total >= it.threshold }.map { it.threshold }
        return current.copy(total = total, today = current.today + 1, unlocked = unlocked)
    }

    fun newlyUnlocked(before: FishState, after: FishState): List<FishAchievement> =
        achievements.filter { it.threshold in after.unlocked && it.threshold !in before.unlocked }

    fun summary(state: FishState): String = "total=${state.total}\ntoday=${state.today}\ngoal=${state.settings.goal}\n" +
        "unlocked=${state.unlocked.sorted().joinToString("|")}\n"
}

interface FishRepository {
    fun load(): FishState
    fun roll(today: String): FishState
    fun tap(today: String): FishState
    fun updateSettings(settings: FishSettings): FishState
    fun reset(): FishState
    fun flush()
}

interface FishFeedback {
    suspend fun prepare(): Boolean
    fun tap(sound: Boolean, haptic: Boolean): Boolean
    fun stop()
    fun release()
}

class FishUseCases(private val repository: FishRepository, private val feedback: FishFeedback) {
    fun load() = repository.load()
    fun roll(today: String) = repository.roll(today)
    suspend fun prepare() = feedback.prepare()
    fun tap(today: String): FishState = repository.tap(today)
    fun settings(settings: FishSettings) = repository.updateSettings(settings)
    fun reset() = repository.reset()
    fun flush() = repository.flush()
    fun feedback(sound: Boolean, haptic: Boolean) = feedback.tap(sound, haptic)
    fun release() = feedback.release()
}
