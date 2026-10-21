package com.cangshuo.toolbox.feature.fps

import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.fps.domain.*
import com.cangshuo.toolbox.feature.fps.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FpsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private class Fake : FpsRepository {
        val updates = MutableSharedFlow<FpsSnapshot>(extraBufferCapacity = 8)
        var records = emptyList<FpsRecord>()
        var fail = false
        var writes = 0
        override fun observe(request: FpsRequest, displayId: Int) = updates
        override suspend fun history(): List<FpsRecord> { check(!fail); return records }
        override suspend fun save(record: FpsRecord): List<FpsRecord> {
            check(!fail); writes++
            records = (listOf(record) + records.filterNot { it.id == record.id }).take(10)
            return records
        }
        override suspend fun clear(): List<FpsRecord> { check(!fail); records = emptyList(); return records }
    }
    private fun record(complete: Boolean): FpsRecord {
        val a = FpsAccumulator(FpsRequest(), 60.0)
        a.frame(0, 60.0)
        a.frame(if (complete) 10_000_000_000 else 16_666_667, 60.0)
        return requireNotNull(a.snapshot("test", 1))
    }
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun requiresForegroundAndFreezesParametersWhileRunning() = runTest(dispatcher) {
        val fake = Fake(); val m = FpsViewModel(FpsUseCases(fake), SavedStateHandle())
        runCurrent(); m.start(0); assertEquals(FpsStage.IDLE, m.state.value.stage)
        m.foreground(); m.configure(FpsRequest(30, 120, 64)); m.start(0); runCurrent()
        m.configure(FpsRequest()); m.start(0)
        assertEquals(FpsStage.WAITING, m.state.value.stage)
        assertEquals(FpsRequest(30, 120, 64), m.state.value.request)
        assertEquals(1, fake.updates.subscriptionCount.value)
        m.background(); runCurrent(); assertEquals(0, fake.updates.subscriptionCount.value)
    }
    @Test fun backgroundCancelsWithoutSavingAndProcessRestoreStaysInterrupted() = runTest(dispatcher) {
        val fake = Fake(); val saved = SavedStateHandle(); val m = FpsViewModel(FpsUseCases(fake), saved)
        runCurrent(); m.foreground(); m.start(0); runCurrent()
        fake.updates.emit(FpsSnapshot(FpsStage.RUNNING, record(false))); runCurrent()
        m.background(); runCurrent()
        fake.updates.emit(FpsSnapshot(FpsStage.COMPLETE, record(true))); runCurrent()
        assertEquals(FpsStage.INTERRUPTED, m.state.value.stage)
        assertEquals(0, fake.writes)
        val restored = FpsViewModel(FpsUseCases(fake), saved); runCurrent()
        assertEquals(FpsStage.INTERRUPTED, restored.state.value.stage)
    }
    @Test fun automaticCompletionPersistsAndRestoresWithoutDuplicateWrite() = runTest(dispatcher) {
        val fake = Fake(); val saved = SavedStateHandle(); val m = FpsViewModel(FpsUseCases(fake), saved)
        runCurrent(); m.foreground(); m.start(0); runCurrent()
        val result = record(true)
        fake.updates.emit(FpsSnapshot(FpsStage.COMPLETE, result)); runCurrent()
        assertEquals(listOf(result), m.state.value.history); assertEquals(1, fake.writes)
        fake.updates.emit(FpsSnapshot(FpsStage.FAILED)); runCurrent()
        assertEquals(result, m.state.value.result); assertEquals(FpsStage.COMPLETE, m.state.value.stage)
        m.background(); val restored = FpsViewModel(FpsUseCases(fake), saved); runCurrent()
        assertEquals(result, restored.state.value.result)
        assertEquals(FpsStage.COMPLETE, restored.state.value.stage); assertEquals(1, fake.writes)
    }
    @Test fun saveFailureBlocksResetAndRetryKeepsExactlyTheSameResult() = runTest(dispatcher) {
        val fake = Fake(); val saved = SavedStateHandle(); val m = FpsViewModel(FpsUseCases(fake), saved)
        runCurrent(); m.foreground(); m.start(0); runCurrent(); fake.fail = true
        fake.updates.emit(FpsSnapshot(FpsStage.COMPLETE, record(true))); runCurrent()
        assertEquals(FpsStorageError.SAVE, m.state.value.error)
        m.reset(); m.clear(); assertEquals(FpsStage.COMPLETE, m.state.value.stage)
        fake.fail = false; m.retry(); runCurrent(); m.retry(); runCurrent()
        assertEquals(1, fake.writes); assertEquals(m.state.value.result, fake.records.single())
        m.background()
    }
    @Test fun manualStopSavesPartialSnapshotAndCancelsSampling() = runTest(dispatcher) {
        val fake = Fake(); val m = FpsViewModel(FpsUseCases(fake), SavedStateHandle())
        runCurrent(); m.foreground(); m.start(0); runCurrent()
        fake.updates.emit(FpsSnapshot(FpsStage.RUNNING, record(false))); runCurrent()
        m.stop(); runCurrent(); assertEquals(FpsStage.STOPPED, m.state.value.stage)
        assertFalse(fake.records.single().complete); assertEquals(0, fake.updates.subscriptionCount.value)
    }
    @Test fun clearingHistoryDoesNotResurrectFromSavedState() = runTest(dispatcher) {
        val fake = Fake(); val saved = SavedStateHandle(); val m = FpsViewModel(FpsUseCases(fake), saved)
        runCurrent(); m.foreground(); m.start(0); runCurrent()
        fake.updates.emit(FpsSnapshot(FpsStage.COMPLETE, record(true))); runCurrent()
        m.clear(); runCurrent(); m.background()
        val restored = FpsViewModel(FpsUseCases(fake), saved); runCurrent()
        assertTrue(fake.records.isEmpty()); assertNotNull(restored.state.value.result); assertEquals(1, fake.writes)
    }
    @Test fun corruptedHistoryRequiresRecoveryBeforeStarting() = runTest(dispatcher) {
        val fake = Fake().apply { fail = true }; val m = FpsViewModel(FpsUseCases(fake), SavedStateHandle())
        runCurrent(); m.foreground(); m.start(0)
        assertEquals(FpsStorageError.LOAD, m.state.value.error); assertEquals(FpsStage.IDLE, m.state.value.stage)
        fake.fail = false; m.clear(); runCurrent(); m.start(0); runCurrent()
        assertEquals(FpsStage.WAITING, m.state.value.stage); m.background()
    }
    @Test fun selectedHistoryRestoresItsParametersAndRejectsCorruptSavedRecord() = runTest(dispatcher) {
        val fake = Fake(); val saved = SavedStateHandle(); val m = FpsViewModel(FpsUseCases(fake), saved)
        val selected = record(false).copy(request = FpsRequest(30, 120, 64), budgetHz = 120.0)
        runCurrent(); m.select(selected)
        val recovered = FpsViewModel(FpsUseCases(fake), saved); runCurrent()
        assertEquals(selected, recovered.state.value.result)
        m.reset(); assertEquals(selected.request, m.state.value.request)
        saved["fpsResultId"] = "broken"; saved["fpsResultLongs"] = longArrayOf(1)
        val restored = FpsViewModel(FpsUseCases(fake), saved); runCurrent()
        assertNull(restored.state.value.result); assertEquals(0, fake.writes)
    }
    @Test fun clearingAfterFailedRestoreDoesNotLaterResaveTheClearedRecord() = runTest(dispatcher) {
        val fake = Fake(); val saved = SavedStateHandle(); val m = FpsViewModel(FpsUseCases(fake), saved)
        runCurrent(); m.select(record(true))
        saved["fpsStored"] = false; fake.fail = true
        val restored = FpsViewModel(FpsUseCases(fake), saved); runCurrent()
        assertEquals(FpsStorageError.LOAD, restored.state.value.error)
        fake.fail = false; restored.clear(); runCurrent()
        FpsViewModel(FpsUseCases(fake), saved); runCurrent()
        assertTrue(fake.records.isEmpty()); assertEquals(0, fake.writes)
    }
}
