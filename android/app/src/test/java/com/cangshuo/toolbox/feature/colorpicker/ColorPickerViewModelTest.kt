package com.cangshuo.toolbox.feature.colorpicker

import com.cangshuo.toolbox.feature.colorpicker.domain.*
import com.cangshuo.toolbox.feature.colorpicker.ui.ColorPickerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ColorPickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    private class FakeRepository : ColorPickerRepository {
        val calls = mutableListOf<String>()
        var execute: suspend (String) -> Result<LoadedImage> = {
            Result.failure(ColorPickerException(ColorPickerError.INVALID_IMAGE))
        }
        override suspend fun load(uri: String): Result<LoadedImage> { calls += uri; return execute(uri) }
        override fun sample(image: LoadedImage, x: Int, y: Int): Int = error("No image available in failure test")
    }
    private fun model(repository: ColorPickerRepository) =
        ColorPickerViewModel(LoadImageUseCase(repository), SampleColorUseCase(repository))

    @Test fun selectingNoFileDoesNotLoadOrLoseCurrentState() = runTest(dispatcher) {
        val source = FakeRepository()
        val model = model(source)
        model.onImagePicked(null)
        advanceUntilIdle()
        assertTrue(source.calls.isEmpty())
        assertNull(model.uiState.value.error)
        assertFalse(model.uiState.value.loading)
    }

    @Test fun failureEndsLoadingWithTypedErrorAndRetryUsesSameSource() = runTest(dispatcher) {
        val source = FakeRepository()
        val model = model(source)
        model.onImagePicked("content://test/invalid")
        advanceUntilIdle()
        assertEquals(ColorPickerError.INVALID_IMAGE, model.uiState.value.error)
        assertFalse(model.uiState.value.loading)
        model.onRetry()
        advanceUntilIdle()
        assertEquals(listOf("content://test/invalid", "content://test/invalid"), source.calls)
    }

    @Test fun unexpectedExceptionBecomesRecoverableAndNoSpinner() = runTest(dispatcher) {
        val source = FakeRepository().apply { execute = { error("Provider failed") } }
        val model = model(source)
        model.onImagePicked("content://test/failure")
        advanceUntilIdle()
        assertEquals(ColorPickerError.LOAD_FAILED, model.uiState.value.error)
        assertFalse(model.uiState.value.loading)
    }

    @Test fun secondSelectionCancelsFirstAndRetainsOnlyItsOwnError() = runTest(dispatcher) {
        var firstCancelled = false
        val source = FakeRepository().apply {
            execute = { value ->
                if (value.endsWith("first")) {
                    try { awaitCancellation() } finally { firstCancelled = true }
                }
                Result.failure(ColorPickerException(ColorPickerError.TOO_LARGE))
            }
        }
        val model = model(source)
        model.onImagePicked("content://test/first")
        runCurrent()
        assertTrue(model.uiState.value.loading)
        model.onImagePicked("content://test/second")
        advanceUntilIdle()
        assertTrue(firstCancelled)
        assertEquals(ColorPickerError.TOO_LARGE, model.uiState.value.error)
        assertFalse(model.uiState.value.loading)
    }

    @Test fun cancelAndClearStopReadsAndClearRevokesRetry() = runTest(dispatcher) {
        var cancelled = 0
        val source = FakeRepository().apply { execute = { try { awaitCancellation() } finally { cancelled++ } } }
        val model = model(source)
        model.onImagePicked("content://test/reading")
        runCurrent()
        model.onCancelLoad()
        advanceUntilIdle()
        assertEquals(1, cancelled)
        assertFalse(model.uiState.value.loading)
        model.onRetry()
        runCurrent()
        model.onClearImage()
        advanceUntilIdle()
        model.onRetry()
        advanceUntilIdle()
        assertEquals(2, cancelled)
        assertEquals(2, source.calls.size)
        assertNull(model.uiState.value.image)
        assertNull(model.uiState.value.error)
        assertTrue(model.uiState.value.palette.isEmpty())
    }

    @Test fun pickerUnavailableHasAVisibleErrorAndNoImageOperations() = runTest(dispatcher) {
        val source = FakeRepository()
        val model = model(source)
        model.onPickerUnavailable()
        model.onSample(null)
        model.onSample(SamplePoint(1, 1, 0f, 0f))
        model.onPaletteSelected(PickedColor(0))
        assertEquals(ColorPickerError.PICKER_UNAVAILABLE, model.uiState.value.error)
        assertTrue(source.calls.isEmpty())
        assertNull(model.uiState.value.selected)
    }
}
