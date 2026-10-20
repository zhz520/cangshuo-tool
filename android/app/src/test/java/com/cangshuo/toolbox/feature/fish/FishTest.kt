package com.cangshuo.toolbox.feature.fish

import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.fish.domain.*
import com.cangshuo.toolbox.feature.fish.ui.FishViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FishTest {
    private val dispatcher = StandardTestDispatcher()
    private class Repo(var value: FishState) : FishRepository {
        var flushes = 0; var taps = 0
        override fun load() = value
        override fun roll(today: String) = FishPolicy.rollDate(value, today).also { value = it }
        override fun tap(today: String): FishState { taps++; value = FishPolicy.registerTap(value, today); return value }
        override fun updateSettings(settings: FishSettings) = value.copy(settings = settings).also { value = it }
        override fun reset() = FishState().also { value = it }
        override fun flush() { flushes++ }
    }
    private class Feedback : FishFeedback {
        var prepared = false; var plays = 0; var last: Pair<Boolean, Boolean>? = null
        override suspend fun prepare(): Boolean { prepared = true; return true }
        override fun tap(sound: Boolean, haptic: Boolean): Boolean { plays++; last = sound to haptic; return true }
        override fun stop() = Unit
        override fun release() = Unit
    }
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun tapUnlocksThresholdsAndCapsAtIntegerLimit() {
        var s = FishState(date = "2026-10-20")
        repeat(100) { s = FishPolicy.registerTap(s, "2026-10-20") }
        assertEquals(100, s.total); assertEquals(100, s.today)
        assertEquals(setOf(1L, 100L), s.unlocked); assertTrue(FishPolicy.valid(s))
    }
    @Test fun dateRollResetsOnlyTodayAndKeepsTotalAndAchievements() {
        var s = FishState(date = "2026-10-20")
        repeat(3) { s = FishPolicy.registerTap(s, "2026-10-20") }
        val rolled = FishPolicy.rollDate(s, "2026-10-21")
        assertEquals(3, rolled.total); assertEquals(0, rolled.today); assertEquals(s.unlocked, rolled.unlocked)
        val next = FishPolicy.registerTap(rolled, "2026-10-21")
        assertEquals(4, next.total); assertEquals(1, next.today)
    }
    @Test fun invalidPersistentStateIsRejected() {
        assertFalse(FishPolicy.valid(FishState(total = -1)))
        assertFalse(FishPolicy.valid(FishState(date = "not-a-date")))
        assertFalse(FishPolicy.valid(FishState(unlocked = setOf(2))))
        assertFalse(FishPolicy.valid(FishState(settings = FishSettings(goal = 7))))
        assertFalse(FishPolicy.valid(FishState(settings = FishSettings(autoIntervalMs = 10))))
    }
    @Test fun viewModelLoadsAudioAndTapUpdatesCountsFeedbackAndAchievement() = runTest(dispatcher) {
        val repo = Repo(FishState(date = "2026-10-20")); val feedback = Feedback()
        val model = FishViewModel(FishUseCases(repo, feedback), SavedStateHandle(), { "2026-10-20" })
        advanceUntilIdle(); model.foreground(); model.tap()
        assertEquals(1, model.state.value.stored!!.total); assertEquals(1, feedback.plays)
        assertEquals(1L, model.state.value.newAchievements.single().threshold); assertEquals(1L, model.state.value.pulse)
        assertTrue(feedback.prepared); assertTrue(model.state.value.audioReady)
    }
    @Test fun settingsControlFeedbackAndPersist() = runTest(dispatcher) {
        val repo = Repo(FishState(date = "2026-10-20")); val feedback = Feedback()
        val model = FishViewModel(FishUseCases(repo, feedback), SavedStateHandle(), { "2026-10-20" })
        advanceUntilIdle(); model.foreground(); model.setSound(false); model.setHaptic(false); model.tap()
        assertEquals(false to false, feedback.last); assertEquals(108, model.state.value.stored!!.settings.goal)
    }
    @Test fun dateChangeRefreshesTodayWithoutTouchingTotal() = runTest(dispatcher) {
        val repo = Repo(FishState(date = "2026-10-20")); var day = "2026-10-20"
        val model = FishViewModel(FishUseCases(repo, Feedback()), SavedStateHandle(), { day })
        advanceUntilIdle(); model.foreground(); model.tap(); model.tap()
        day = "2026-10-21"; model.foreground();
        assertEquals(2, model.state.value.stored!!.total); assertEquals(0, model.state.value.stored!!.today)
        model.tap(); assertEquals(1, model.state.value.stored!!.today)
    }
    @Test fun dailyLimitClearsWhenForegroundRollsToTheNextDay() = runTest(dispatcher) {
        val repo = Repo(FishState(total = 10, today = FishPolicy.MAX_TODAY, date = "2026-10-20"))
        var day = "2026-10-20"
        val model = FishViewModel(FishUseCases(repo, Feedback()), SavedStateHandle(), { day })
        advanceUntilIdle(); model.foreground(); model.tap(); assertTrue(model.state.value.limitReached)
        day = "2026-10-21"; model.foreground()
        assertFalse(model.state.value.limitReached); assertEquals(0, model.state.value.stored!!.today)
        model.tap(); assertEquals(1, model.state.value.stored!!.today)
    }
    @Test fun autoRunsOnlyWhileForegroundAndStopsOnBackground() = runTest(dispatcher) {
        val repo = Repo(FishState(date = "2026-10-20")); val model = FishViewModel(FishUseCases(repo, Feedback()), SavedStateHandle(), { "2026-10-20" })
        advanceUntilIdle(); model.foreground(); model.setInterval(500); model.toggleAuto(); advanceTimeBy(1_100); runCurrent()
        assertEquals(2, repo.taps); model.background(); advanceTimeBy(2_000); runCurrent()
        assertEquals(2, repo.taps); assertFalse(model.state.value.autoEnabled); assertTrue(repo.flushes >= 1)
    }
    @Test fun invalidGoalAndIntervalAreIgnored() = runTest(dispatcher) {
        val repo = Repo(FishState(date = "2026-10-20")); val model = FishViewModel(FishUseCases(repo, Feedback()), SavedStateHandle(), { "2026-10-20" })
        advanceUntilIdle(); model.setGoal(7); model.setInterval(123)
        assertEquals(108, model.state.value.stored!!.settings.goal); assertEquals(1_000, model.state.value.stored!!.settings.autoIntervalMs)
    }
    @Test fun resetClearsCountsAchievementsPulseAndStopsAuto() = runTest(dispatcher) {
        val repo = Repo(FishState(total = 100, today = 100, date = "2026-10-20", unlocked = setOf(1, 100)))
        val model = FishViewModel(FishUseCases(repo, Feedback()), SavedStateHandle(), { "2026-10-20" })
        advanceUntilIdle(); model.foreground(); model.toggleAuto(); model.resetAll()
        val s = model.state.value.stored!!
        assertEquals(0, s.total); assertEquals(0, s.today); assertTrue(s.unlocked.isEmpty()); assertFalse(model.state.value.autoEnabled)
    }
    @Test fun summaryIsDeterministicAndContainsNoUserText() {
        val s = FishState(total = 3, today = 2, date = "2026-10-20", unlocked = setOf(1), settings = FishSettings(goal = 21))
        assertEquals("total=3\ntoday=2\ngoal=21\nunlocked=1\n", FishPolicy.summary(s))
    }
}
