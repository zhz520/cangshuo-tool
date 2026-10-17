package com.cangshuo.toolbox.feature.timescreen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.timescreen.domain.ReadTimeScreenUseCase
import com.cangshuo.toolbox.feature.timescreen.domain.TimeScreenRepository
import com.cangshuo.toolbox.feature.timescreen.ui.TimeScreenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimeScreenViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    private class FakeClock(
        var now: ZonedDateTime = ZonedDateTime.of(2026, 10, 17, 21, 5, 7, 250_000_000, ZoneId.of("Asia/Shanghai")),
    ) : TimeScreenRepository {
        override fun now() = now
        override fun locale() = Locale.US
    }

    private fun model(repo: FakeClock = FakeClock(), saved: SavedStateHandle = SavedStateHandle()) =
        TimeScreenViewModel(ReadTimeScreenUseCase(repo), saved).also { store.put("time", it) }

    private fun timedTest(block: suspend TestScope.() -> Unit) = runTest {
        try { block() } finally { store.clear() }
    }

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }

    @Test fun initialSnapshotReflectsSavedFormat() = timedTest {
        val state = model().state.value
        assertEquals("21:05:07", state.snapshot.clock.main)
        assertTrue(state.settings.antiBurnIn)
        assertEquals(8f, kotlin.math.hypot(state.drift.xDp, state.drift.yDp), 0.1f)
    }

    @Test fun formatTogglesUpdateImmediatelyAndPersist() = timedTest {
        val saved = SavedStateHandle()
        val model = model(saved = saved)
        model.setUse24Hour(false)
        model.setShowSeconds(false)
        model.setShowDate(false)
        model.setShowZone(false)
        assertEquals("9:05", model.state.value.snapshot.clock.main)
        assertEquals("PM", model.state.value.snapshot.clock.period)
        assertFalse(saved.get<Boolean>("ts24Hour")!!)
        assertFalse(saved.get<Boolean>("tsSeconds")!!)
        assertFalse(saved.get<Boolean>("tsDate")!!)
        assertFalse(saved.get<Boolean>("tsZone")!!)
    }

    @Test fun burnInAndKeepScreenPreferencesPersistAndDriftCanBeDisabled() = timedTest {
        val saved = SavedStateHandle()
        val model = model(saved = saved)
        model.setAntiBurnIn(false)
        model.setDriftRadius(12)
        model.setKeepScreenOn(false)
        assertEquals(0f, model.state.value.drift.xDp)
        assertEquals(0f, model.state.value.drift.yDp)
        assertEquals(12, saved.get<Int>("tsDrift"))
        assertEquals(false, saved.get<Boolean>("tsKeepScreen"))
    }

    @Test fun tickerRefreshesAtSecondBoundaryAndStopsInBackground() = timedTest {
        val clock = FakeClock()
        val model = model(clock)
        model.start()
        runCurrent()
        clock.now = clock.now.plusSeconds(1)
        advanceTimeBy(751L)
        runCurrent()
        assertEquals("21:05:08", model.state.value.snapshot.clock.main)
        model.stop()
        clock.now = clock.now.plusSeconds(1)
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals("21:05:08", model.state.value.snapshot.clock.main)
    }

    @Test fun savedSettingsAreBoundedAndResetRestoresDefaults() = timedTest {
        val saved = SavedStateHandle(
            mapOf(
                "ts24Hour" to false,
                "tsSeconds" to false,
                "tsDate" to false,
                "tsZone" to false,
                "tsBurnIn" to false,
                "tsDrift" to 99,
                "tsKeepScreen" to false,
            ),
        )
        val model = model(saved = saved)
        assertEquals(16, model.state.value.settings.driftRadiusDp)
        assertFalse(model.state.value.settings.use24Hour)
        model.reset()
        assertTrue(model.state.value.settings.use24Hour)
        assertTrue(model.state.value.settings.showSeconds)
        assertTrue(model.state.value.settings.showDate)
        assertTrue(model.state.value.settings.showZone)
        assertTrue(model.state.value.settings.antiBurnIn)
        assertTrue(model.state.value.settings.keepScreenOn)
    }
}
