package com.cangshuo.toolbox.feature.imagebase64

import android.net.Uri
import com.cangshuo.toolbox.feature.imagebase64.domain.*
import com.cangshuo.toolbox.feature.imagebase64.ui.ImageBase64ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImageBase64ViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    private class FakeRepository : ImageBase64Repository {
        val encoded = mutableListOf<String>()
        val decoded = mutableListOf<String>()
        var encodeResult: suspend (String) -> Result<ImageBase64Result> = {
            Result.failure(ImageBase64Exception(ImageBase64Error.INVALID_INPUT))
        }
        var decodeResult: suspend (String) -> Result<ImageBase64Result> = {
            Result.failure(ImageBase64Exception(ImageBase64Error.TOO_LARGE))
        }
        override suspend fun encode(uri: String): Result<ImageBase64Result> { encoded += uri; return encodeResult(uri) }
        override suspend fun decode(text: String): Result<ImageBase64Result> { decoded += text; return decodeResult(text) }
        override suspend fun saveText(uri: String, text: String): Result<Unit> = Result.success(Unit)
        override suspend fun prepareShareText(text: String): Result<Uri> = Result.failure(ImageBase64Exception(ImageBase64Error.SHARE_FAILED))
    }

    private fun model(repository: ImageBase64Repository) = ImageBase64ViewModel(
        LoadImageBase64UseCase(repository), DecodeImageBase64UseCase(repository),
        SaveImageBase64TextUseCase(repository), PrepareImageBase64ShareUseCase(repository),
    )

    @Test fun cancelledPickerDoesNotReadOrClearInput() = runTest(dispatcher) {
        val repo = FakeRepository(); val vm = model(repo)
        vm.updateInput("kept")
        vm.onImagePicked(null)
        advanceUntilIdle()
        assertTrue(repo.encoded.isEmpty())
        assertEquals("kept", vm.uiState.value.input)
        assertNull(vm.uiState.value.error)
    }

    @Test fun imageFailureMapsTypedErrorAndRetryUsesSameSource() = runTest(dispatcher) {
        val repo = FakeRepository(); val vm = model(repo)
        vm.onImagePicked("content://test/image")
        advanceUntilIdle()
        assertEquals(ImageBase64Error.INVALID_INPUT, vm.uiState.value.error)
        assertFalse(vm.uiState.value.loading)
        vm.onRetry(); advanceUntilIdle()
        assertEquals(listOf("content://test/image", "content://test/image"), repo.encoded)
    }

    @Test fun decodeRequiresContentAndFailureEndsLoading() = runTest(dispatcher) {
        val repo = FakeRepository(); val vm = model(repo)
        vm.onDecode(); advanceUntilIdle()
        assertTrue(repo.decoded.isEmpty())
        vm.updateInput("data:image/png;base64,AAAA")
        vm.onDecode(); advanceUntilIdle()
        assertEquals(1, repo.decoded.size)
        assertEquals(ImageBase64Error.TOO_LARGE, vm.uiState.value.error)
        assertFalse(vm.uiState.value.loading)
    }

    @Test fun modeSwitchClearsResultStateButKeepsDecodeInput() = runTest(dispatcher) {
        val vm = model(FakeRepository())
        vm.updateInput("payload")
        vm.onModeChanged(ImageBase64Mode.DECODE)
        assertEquals("payload", vm.uiState.value.input)
        assertEquals(ImageBase64Mode.DECODE, vm.uiState.value.mode)
        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.loading)
    }

    @Test fun inputBudgetAndOptionCombinationsStayBounded() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = model(repo)
        vm.updateInput("kept")
        vm.updateInput("A".repeat(ImageBase64Limits.MAX_INPUT_CHARS + 1))
        assertEquals("kept", vm.uiState.value.input)
        assertEquals(ImageBase64Error.TOO_LARGE, vm.uiState.value.error)
        vm.onDecode(); advanceUntilIdle()
        assertTrue(repo.decoded.isEmpty())
        vm.updateInput("ok")
        assertEquals("ok", vm.uiState.value.input)
        assertNull(vm.uiState.value.error)
        vm.setDataUrl(false); vm.setUrlSafe(true); vm.setLineWrap(true)
        assertFalse(vm.uiState.value.options.dataUrl)
        assertTrue(vm.uiState.value.options.urlSafe)
        assertTrue(vm.uiState.value.options.lineWrap)
        vm.setDataUrl(true)
        assertTrue(vm.uiState.value.options.dataUrl)
        assertFalse(vm.uiState.value.options.urlSafe)
        assertFalse(vm.uiState.value.options.lineWrap)
    }

    @Test fun optionChangeDuringLoadDoesNotOrphanTheImageResult() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeRepository().apply {
            encodeResult = { gate.await(); Result.failure(ImageBase64Exception(ImageBase64Error.TOO_LARGE)) }
        }
        val vm = model(repo)
        vm.onImagePicked("content://test/slow")
        runCurrent()
        assertTrue(vm.uiState.value.loading)
        vm.setDataUrl(false)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(ImageBase64Error.TOO_LARGE, vm.uiState.value.error)
        assertFalse(vm.uiState.value.loading)
    }
}
