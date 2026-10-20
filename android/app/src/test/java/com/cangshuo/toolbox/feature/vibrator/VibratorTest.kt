package com.cangshuo.toolbox.feature.vibrator

import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.vibrator.domain.*
import com.cangshuo.toolbox.feature.vibrator.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VibratorTest {
    private val dispatcher = StandardTestDispatcher()
    private class Hardware(var cap: DeviceVibrationCapability = DeviceVibrationCapability(true, true)) : VibrationHardware {
        var clock = 100L; var played = 0; var cancelled = 0; var last: VibrationWaveform? = null; var fail = false
        override fun capability() = cap
        override fun play(waveform: VibrationWaveform) { if (fail) error("hardware"); played++; last = waveform }
        override fun cancel() { cancelled++ }
        override fun now() = clock
        override fun wall() = 1_000_000L
    }
    private class History : VibrationHistoryRepository {
        var records = emptyList<VibrationRecord>(); var fail = false
        override suspend fun history(): List<VibrationRecord> { if (fail) error("load"); return records }
        override suspend fun save(record: VibrationRecord): List<VibrationRecord> {
            if (fail) error("save")
            records = (listOf(record) + records.filterNot { it.id == record.id }).take(20); return records
        }
        override suspend fun clear(): List<VibrationRecord> { if (fail) error("clear"); records = emptyList(); return records }
    }
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    private fun useCases(hardware: Hardware = Hardware(), history: History = History()) = VibrationUseCases(hardware, history)

    @Test fun everyPresetIsBoundedAndUsesMatchingRestAmplitudes() {
        VibrationPreset.entries.forEach { preset ->
            val waveform = VibrationPolicy.waveform(VibrationPattern(preset, 250, 70))
            assertEquals(0L, waveform.timings.first()); assertEquals(waveform.timings.size, waveform.amplitudes.size)
            assertTrue(waveform.totalMs in 1..30_000); assertEquals(0, waveform.amplitudes[0])
        }
    }
    @Test fun strengthMapsToAndroidAmplitudeRange() {
        val low = VibrationPolicy.waveform(VibrationPattern(VibrationPreset.SINGLE, 100, 5))
        val high = VibrationPolicy.waveform(VibrationPattern(VibrationPreset.SINGLE, 100, 100))
        assertEquals(12, low.amplitudes.max()); assertEquals(255, high.amplitudes.max())
    }
    @Test(expected = IllegalArgumentException::class) fun outOfRangeDurationIsRejected() {
        VibrationPolicy.waveform(VibrationPattern(durationMs = 2_001))
    }
    @Test fun noAmplitudeFallbackDropsAmplitudeArray() {
        val wave = VibrationPolicy.noAmplitudeWaveform(VibrationPattern(strengthPercent = 100))
        assertTrue(wave.timings.isNotEmpty()); assertTrue(wave.amplitudes.isEmpty()); assertFalse(wave.amplitudeSupported)
    }
    @Test fun recordsValidateAndCsvEscapesNoUserText() {
        val record = VibrationRecord("test-1", 1, VibrationPreset.WAVE, 200, 70, 720, true)
        assertTrue(VibrationPolicy.valid(record)); assertFalse(VibrationPolicy.valid(record.copy(actualDurationMs = 0)))
        assertEquals(2, VibrationPolicy.csv(listOf(record)).lines().size)
    }
    @Test fun noMotorNeverCallsHardware() = runTest(dispatcher) {
        val h = Hardware(DeviceVibrationCapability(false, false)); val m = VibratorViewModel(useCases(h), SavedStateHandle())
        runCurrent(); m.foreground(); m.play(); runCurrent()
        assertEquals(0, h.played); assertFalse(m.state.value.capability!!.hasVibrator)
    }
    @Test fun completeRunPlaysOnceAndSavesTruthfulDuration() = runTest(dispatcher) {
        val h = Hardware(); val hist = History(); val m = VibratorViewModel(useCases(h, hist), SavedStateHandle())
        runCurrent(); m.foreground(); m.selectPreset(VibrationPreset.DOUBLE); m.play(); runCurrent()
        assertEquals(VibrationPhase.PLAYING, m.state.value.phase); assertEquals(1, h.played)
        h.clock += 400; advanceTimeBy(500); runCurrent(); advanceUntilIdle()
        assertEquals(VibrationPhase.FINISHED, m.state.value.phase); assertEquals(1, hist.records.size)
        assertTrue(hist.records.single().actualDurationMs <= h.last!!.totalMs)
    }
    @Test fun manualStopCancelsAndDoesNotCreateCompleteHistory() = runTest(dispatcher) {
        val h = Hardware(); val hist = History(); val m = VibratorViewModel(useCases(h, hist), SavedStateHandle())
        runCurrent(); m.foreground(); m.play(); runCurrent(); m.stop(); runCurrent(); advanceUntilIdle()
        assertEquals(VibrationPhase.CANCELLED, m.state.value.phase); assertTrue(hist.records.isEmpty()); assertTrue(h.cancelled >= 1)
    }
    @Test fun backgroundCancelsActiveWaveformWithoutSaving() = runTest(dispatcher) {
        val h = Hardware(); val hist = History(); val m = VibratorViewModel(useCases(h, hist), SavedStateHandle())
        runCurrent(); m.foreground(); m.play(); runCurrent(); m.background(); advanceUntilIdle()
        assertEquals(VibrationPhase.CANCELLED, m.state.value.phase); assertTrue(hist.records.isEmpty()); assertTrue(h.cancelled >= 1)
    }
    @Test fun activeSavedStateIsCancelledInsteadOfResumed() = runTest(dispatcher) {
        val h = Hardware(); val hist = History();
        val m = VibratorViewModel(useCases(h, hist), SavedStateHandle(mapOf("vibActive" to true)))
        runCurrent(); assertTrue(h.cancelled >= 1); assertEquals(VibrationPhase.INTERRUPTED, m.state.value.phase)
    }
    @Test fun hardwareFailureDoesNotPretendToPlay() = runTest(dispatcher) {
        val h = Hardware().apply { fail = true }; val m = VibratorViewModel(useCases(h), SavedStateHandle())
        runCurrent(); m.foreground(); m.play(); runCurrent()
        assertTrue(m.state.value.hardwareError); assertEquals(VibrationPhase.IDLE, m.state.value.phase)
    }
    @Test fun failedSaveRetriesSameRecordAndClearDoesNotResurrect() = runTest(dispatcher) {
        val h = Hardware(); val hist = History().apply { fail = true }; val m = VibratorViewModel(useCases(h, hist), SavedStateHandle())
        runCurrent(); m.foreground(); m.selectPreset(VibrationPreset.SINGLE); m.play(); runCurrent(); h.clock += 300; advanceTimeBy(400); runCurrent()
        assertEquals(VibrationStorageError.SAVE, m.state.value.error)
        m.play(); assertEquals(1, h.played)
        m.selectPreset(VibrationPreset.WAVE)
        hist.fail = false; m.retry(); runCurrent(); assertEquals(1, hist.records.size)
        assertEquals(VibrationPreset.SINGLE, hist.records.single().preset)
        val id = hist.records.single().id; m.retry(); runCurrent(); assertEquals(1, hist.records.size); assertEquals(id, hist.records.single().id)
        m.clearHistory(); runCurrent(); assertTrue(hist.records.isEmpty())
    }
    @Test fun controlsCannotChangeWhilePlayingAndOnOffHardwareDropsAmplitudes() = runTest(dispatcher) {
        val h = Hardware(DeviceVibrationCapability(true, false)); val m = VibratorViewModel(useCases(h), SavedStateHandle())
        runCurrent(); m.foreground(); m.play(); runCurrent(); m.strength(20); m.duration(50)
        assertEquals(200, m.state.value.pattern.durationMs); assertEquals(70, m.state.value.pattern.strengthPercent)
        assertTrue(h.last!!.amplitudes.isEmpty())
    }
}
