package com.cangshuo.toolbox.feature.webtools.ui

import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.webtools.domain.SaveWebToolExportUseCase
import com.cangshuo.toolbox.feature.webtools.domain.WebToolExportException
import com.cangshuo.toolbox.feature.webtools.domain.WebToolExportRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebToolExportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private val jpeg = "data:image/jpeg;base64,/9j/2Q=="
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }
    private fun model(repository: WebToolExportRepository): WebToolExportViewModel = WebToolExportViewModel(SaveWebToolExportUseCase(repository), dispatcher).also { models.put("export", it) }

    @Test fun duplicateDownloadAndPickerRecreationDoNotReplacePendingExport() = runTest {
        val model = model { _, _ -> fail("No document selected") }
        model.request(jpeg); runCurrent()
        val pending = model.state.value.pending
        model.markPickerRequested()
        model.request("data:text/html;base64,AA=="); runCurrent()
        assertSame(pending, model.state.value.pending)
        assertTrue(model.state.value.pickerRequested)
        model.complete(null)
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.pending)
    }
    @Test fun chosenDocumentReceivesOriginalBytesAndBusyWriteRejectsExtraDownloads() = runTest {
        val finished = CompletableDeferred<Unit>()
        var calls = 0
        val model = model { destination, export ->
            calls++; assertEquals("content://qa/document", destination); assertEquals("qrcode.jpg", export.fileName)
            assertArrayEquals(byteArrayOf(255.toByte(), 216.toByte(), 255.toByte(), 217.toByte()), export.bytes)
            finished.await()
        }
        model.request(jpeg); runCurrent(); model.complete("content://qa/document"); runCurrent()
        model.request(jpeg); runCurrent(); assertEquals(1, calls); assertTrue(model.state.value.busy)
        finished.complete(Unit); runCurrent()
        assertEquals(WebExportMessage.SAVED, model.state.value.message)
        assertFalse(model.state.value.busy)
    }
    @Test fun malformedInputAndProviderFailureAllowRetryWithFixedFeedback() = runTest {
        val model = model { _, _ -> throw WebToolExportException() }
        model.request("data:text/html;base64,AA=="); runCurrent()
        assertEquals(WebExportMessage.FAILED, model.state.value.message)
        model.request(jpeg); runCurrent(); model.complete("content://qa/failure"); runCurrent()
        assertEquals(WebExportMessage.FAILED, model.state.value.message)
        assertFalse(model.state.value.busy)
        model.request(jpeg); runCurrent(); assertNotNull(model.state.value.pending)
    }
    @Test fun clearingViewModelCancelsPendingProviderWork() = runTest {
        val started = CompletableDeferred<Unit>()
        val wait = CompletableDeferred<Unit>()
        var cancelled = false
        val model = model { _, _ -> started.complete(Unit); try { wait.await() } finally { cancelled = true } }
        model.request(jpeg); runCurrent(); model.complete("content://qa/document"); runCurrent()
        assertTrue(started.isCompleted)
        models.clear(); runCurrent(); assertTrue(cancelled)
    }
}
