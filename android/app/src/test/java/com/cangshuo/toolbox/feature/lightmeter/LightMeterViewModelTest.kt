package com.cangshuo.toolbox.feature.lightmeter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.lightmeter.domain.*
import com.cangshuo.toolbox.feature.lightmeter.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LightMeterViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private class FakeRepository : LightMeterRepository {
        val events = MutableSharedFlow<LightEvent>(extraBufferCapacity = 16)
        var active = 0
        var starts = 0
        var fail = false
        override fun observe() = events.onStart { active++; starts++; if (fail) error("Sensor failure") }
            .onCompletion { active-- }
    }
    private fun model(repository: LightMeterRepository, saved: SavedStateHandle = SavedStateHandle()): LightMeterViewModel =
        LightMeterViewModel(ObserveLightUseCase(repository), saved).also { store.put("light", it) }
    private fun reading(lux: Double = 100.0, seconds: Long = 1) = LightEvent.Reading(LightSample(lux, seconds * 1_000_000_000L, 3))
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }

    @Test fun listenerStartsOnlyInTheForegroundAndStartIsIdempotent() = runTest {
        val repo = FakeRepository(); val model = model(repo)
        runCurrent(); assertEquals(0, repo.starts)
        model.start(); model.start(); runCurrent()
        assertEquals(1, repo.active); assertEquals(1, repo.starts)
        model.stop(); runCurrent(); assertEquals(0, repo.active)
        model.start(); runCurrent(); assertEquals(1, repo.active); assertEquals(2, repo.starts)
    }
    @Test fun pauseHoldsTheReadingAndUnregistersTheListener() = runTest {
        val repo = FakeRepository(); val model = model(repo)
        model.start(); runCurrent(); repo.events.emit(reading()); runCurrent()
        model.togglePause(); runCurrent()
        assertTrue(model.state.value.paused); assertEquals(0, repo.active)
        repo.events.emit(reading(200.0, 2)); runCurrent()
        assertEquals(100.0, model.state.value.sample!!.lux, 0.0)
        model.stop(); model.start(); runCurrent(); assertEquals(0, repo.active)
        model.togglePause(); runCurrent(); repo.events.emit(reading(300.0, 3)); runCurrent()
        assertEquals(1, repo.active); assertEquals(2L, model.state.value.session.count)
        assertEquals(300.0, model.state.value.sample!!.lux, 0.0)
    }
    @Test fun firstReadingTimeoutStopsSamplingAndCanRecoverByRetry() = runTest {
        val repo = FakeRepository(); val model = model(repo)
        model.start(); runCurrent(); advanceTimeBy(5_000); runCurrent()
        assertEquals(LightStatus.NO_READING, model.state.value.status); assertEquals(0, repo.active)
        model.retry(); runCurrent(); repo.events.emit(reading()); runCurrent()
        assertEquals(LightStatus.READY, model.state.value.status)
        advanceTimeBy(60_000); runCurrent()
        // TYPE_LIGHT reports on change: quiet light after the first sample is not a timeout.
        assertEquals(LightStatus.READY, model.state.value.status); assertEquals(1, repo.active)
    }
    @Test fun invalidReadingsDoNotCreateAFakeZeroOrChangeStatistics() = runTest {
        val repo = FakeRepository(); val model = model(repo)
        model.start(); runCurrent(); repo.events.emit(reading()); runCurrent()
        repo.events.emit(LightEvent.InvalidReading); repo.events.emit(reading(Double.NaN, 2)); runCurrent()
        assertEquals(1L, model.state.value.session.count)
        assertEquals(100.0, model.state.value.sample!!.lux, 0.0); assertTrue(model.state.value.invalidReading)
        repo.events.emit(reading(200.0, 3)); runCurrent(); assertFalse(model.state.value.invalidReading)
    }
    @Test fun outOfOrderEventsAndResubscriptionDoNotDoubleCount() = runTest {
        val repo = FakeRepository(); val model = model(repo)
        model.start(); runCurrent(); repo.events.emit(reading(200.0, 2)); runCurrent()
        repo.events.emit(reading(100.0, 1)); runCurrent()
        assertEquals(200.0, model.state.value.sample!!.lux, 0.0)
        model.stop(); runCurrent(); model.start(); runCurrent()
        repo.events.emit(reading(200.0, 2)); runCurrent()
        assertEquals(1L, model.state.value.session.count); assertEquals(LightStatus.READY, model.state.value.status)
    }
    @Test fun clearStartsNewStatisticsAndPreservesCalibrationAndHeldValue() = runTest {
        val repo = FakeRepository(); val model = model(repo)
        model.start(); runCurrent(); repo.events.emit(reading()); runCurrent()
        model.updateReference("150"); model.calibrate(); model.clearSession()
        assertEquals(0L, model.state.value.session.count); assertTrue(model.state.value.session.history.isEmpty())
        assertEquals(1.5, model.state.value.factor, 0.0); assertEquals(100.0, model.state.value.sample!!.lux, 0.0)
        repo.events.emit(reading(200.0, 2)); runCurrent()
        assertEquals(1L, model.state.value.session.count); assertEquals(200.0, model.state.value.session.mean, 0.0)
    }
    @Test fun calibrationUsesRawValueAndRejectsInvalidInputWithoutChangingTheFactor() = runTest {
        val repo = FakeRepository(); val model = model(repo)
        model.start(); runCurrent(); repo.events.emit(reading()); runCurrent()
        model.togglePause(); runCurrent(); model.updateReference("150"); model.calibrate()
        assertEquals(1.5, model.state.value.factor, 0.0)
        model.updateReference("200"); model.calibrate(); assertEquals(2.0, model.state.value.factor, 0.0)
        model.updateReference("2001"); model.calibrate()
        assertEquals(LightCalibrationError.FACTOR_OUT_OF_RANGE, model.state.value.calibrationError)
        assertEquals(2.0, model.state.value.factor, 0.0)
        model.resetCalibration(); assertEquals(1.0, model.state.value.factor, 0.0)
    }
    @Test fun calibrationCannotUseAStaleReadingDuringRecovery() = runTest {
        val repo = FakeRepository(); val model = model(repo)
        model.start(); runCurrent(); repo.events.emit(reading()); runCurrent()
        model.updateReference("150"); model.retry(); runCurrent(); model.calibrate()
        assertEquals(LightCalibrationError.NO_READING, model.state.value.calibrationError)
        assertEquals(1.0, model.state.value.factor, 0.0)
    }
    @Test fun settingsRestoreButMeasurementHistoryDoesNotPersist() = runTest {
        val saved = SavedStateHandle(); val model = model(FakeRepository(), saved)
        model.selectUnit(LightUnit.FOOT_CANDLE); model.selectWindow(60); model.selectReport(true); model.updateReference("125")
        store.clear()
        val restored = model(FakeRepository(), saved)
        assertEquals(LightUnit.FOOT_CANDLE, restored.state.value.unit)
        assertEquals(60, restored.state.value.windowSeconds); assertTrue(restored.state.value.csvReport)
        assertEquals("125", restored.state.value.reference); assertNull(restored.state.value.sample)
        restored.selectWindow(999); assertEquals(60, restored.state.value.windowSeconds)
        restored.updateReference("x".repeat(17)); assertEquals("125", restored.state.value.reference)
    }
    @Test fun invalidRestoredSettingsFallBackToSafeDefaults() {
        val model = model(FakeRepository(), SavedStateHandle(mapOf("lightFactor" to Double.NaN,
            "lightUnit" to "invalid", "lightWindow" to -1, "lightReference" to "9".repeat(50))))
        assertEquals(1.0, model.state.value.factor, 0.0); assertEquals(LightUnit.LUX, model.state.value.unit)
        assertEquals(30, model.state.value.windowSeconds); assertEquals("", model.state.value.reference)
    }
    @Test fun sensorFailureUnregistersAndRetryDoesNotLeakAnotherListener() = runTest {
        val repo = FakeRepository(); repo.fail = true; val model = model(repo)
        model.start(); runCurrent(); assertEquals(LightStatus.FAILED, model.state.value.status); assertEquals(0, repo.active)
        repo.fail = false; model.retry(); runCurrent(); assertEquals(1, repo.active)
        repo.events.emit(reading()); runCurrent(); assertEquals(LightStatus.READY, model.state.value.status)
    }
    @Test fun unsupportedHardwareAndUnexpectedCompletionHaveDistinctRecoverableStates() = runTest {
        val unsupported = model(object : LightMeterRepository {
            override fun observe(): Flow<LightEvent> = flow { throw LightSensorUnavailable() }
        })
        unsupported.start(); runCurrent(); assertEquals(LightStatus.UNSUPPORTED, unsupported.state.value.status)
        val complete = model(object : LightMeterRepository { override fun observe() = emptyFlow<LightEvent>() })
        complete.start(); runCurrent(); assertEquals(LightStatus.FAILED, complete.state.value.status)
    }
}
