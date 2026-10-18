package com.cangshuo.toolbox.feature.timer

import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.timer.domain.CountdownPolicy
import com.cangshuo.toolbox.feature.timer.domain.CountdownRepository
import com.cangshuo.toolbox.feature.timer.domain.CountdownStatus
import com.cangshuo.toolbox.feature.timer.domain.CountdownTimerState
import com.cangshuo.toolbox.feature.timer.domain.CountdownUseCases
import com.cangshuo.toolbox.feature.timer.domain.TimerError
import com.cangshuo.toolbox.feature.timer.ui.TimerMessage
import com.cangshuo.toolbox.feature.timer.ui.TimerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class TimerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeRepository : CountdownRepository {
        var elapsed = 10_000L
        var wall = 1_000_000L
        var serviceRequests = 0
        val mutable = MutableStateFlow(CountdownTimerState())
        override val state: StateFlow<CountdownTimerState> = mutable
        override fun prepare(durationMillis: Long) {
            mutable.value = CountdownPolicy.fresh(durationMillis, mutable.value)
        }
        override fun start(durationMillis: Long, label: String) {
            mutable.value = CountdownPolicy.start(mutable.value, durationMillis, label, elapsed, wall)
        }
        override fun pause() { mutable.value = CountdownPolicy.pause(mutable.value, elapsed) }
        override fun resume() { mutable.value = CountdownPolicy.resume(mutable.value, elapsed, wall) }
        override fun reset() { mutable.value = CountdownPolicy.fresh(mutable.value.totalMillis, mutable.value) }
        override fun extend(additionalMillis: Long) {
            mutable.value = CountdownPolicy.extend(mutable.value, additionalMillis, elapsed, wall)
        }
        override fun finish(): Boolean {
            if (mutable.value.status != CountdownStatus.RUNNING || mutable.value.remainingAt(elapsed) > 0L) return false
            mutable.value = CountdownPolicy.finish(mutable.value)
            return true
        }
        override fun markCompletionNotified() { mutable.value = mutable.value.copy(completionNotified = true) }
        override fun setKeepScreenOn(enabled: Boolean) { mutable.value = mutable.value.copy(keepScreenOn = enabled) }
        override fun ensureService() { serviceRequests++ }
        override fun nowElapsedMillis() = elapsed
        override fun nowWallMillis() = wall
    }

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun durationActionsPauseResumeExtendAndValidationDelegate() = runTest {
        val repo = FakeRepository()
        val model = TimerViewModel(CountdownUseCases(repo), SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        runCurrent()

        model.choosePreset(1)
        runCurrent()
        assertEquals("0", model.state.value.hours)
        assertEquals("1", model.state.value.minutes)
        model.setLabel("Tea")
        model.start()
        runCurrent()
        assertEquals(CountdownStatus.RUNNING, model.state.value.status)
        assertEquals(TimerMessage.STARTED, model.state.value.message)

        repo.elapsed += 30_000L
        model.refresh()
        assertEquals(30_000L, model.state.value.remainingMillis)
        model.pause()
        runCurrent()
        repo.elapsed += 10_000L
        model.refresh()
        assertEquals(30_000L, model.state.value.remainingMillis)
        assertTrue(model.state.value.paused)

        model.resume()
        runCurrent()
        assertEquals(CountdownStatus.RUNNING, model.state.value.status)
        assertEquals(1, repo.serviceRequests)
        model.extend()
        runCurrent()
        assertEquals(120_000L, model.state.value.totalMillis)
        assertEquals(90_000L, model.state.value.remainingMillis)
        assertEquals(2, repo.serviceRequests)

        model.pause()
        runCurrent()
        model.reset()
        runCurrent()
        assertEquals(CountdownStatus.IDLE, model.state.value.status)
        assertEquals(TimerMessage.RESET, model.state.value.message)

        model.setHours("0")
        model.setMinutes("0")
        model.setSeconds("0")
        model.start()
        assertEquals(TimerError.DURATION, model.state.value.error)
        model.setKeepScreenOn(false)
        assertEquals(false, model.state.value.keepScreenOn)
        model.reset()
        model.refreshNotification()
        assertEquals(2, repo.serviceRequests)
        model.leaveForeground()
    }

    @Test fun idleStoreEmissionKeepsDraftButResetRestoresDuration() = runTest {
        val repo = FakeRepository()
        val model = TimerViewModel(CountdownUseCases(repo), SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        runCurrent()

        model.setHours("24")
        repo.mutable.value = repo.mutable.value.copy(label = "emission")
        runCurrent()
        assertEquals("24", model.state.value.hours)

        model.setHours("0")
        model.choosePreset(1)
        model.start()
        runCurrent()
        repo.elapsed += 60_000L
        model.reset()
        runCurrent()
        assertEquals("0", model.state.value.hours)
        assertEquals("1", model.state.value.minutes)
        assertEquals("0", model.state.value.seconds)
    }

    @Test fun pauseAtDeadlineFinishesWithoutFalsePauseMessage() = runTest {
        val repo = FakeRepository()
        val model = TimerViewModel(CountdownUseCases(repo), SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        runCurrent()

        model.choosePreset(1)
        model.start()
        runCurrent()
        repo.elapsed += 60_000L
        model.pause()
        runCurrent()
        assertEquals(CountdownStatus.FINISHED, model.state.value.status)
        assertEquals(TimerMessage.FINISHED, model.state.value.message)
    }
}
