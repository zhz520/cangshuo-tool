package com.cangshuo.toolbox.feature.radix

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.radix.data.LocalRadixRepository
import com.cangshuo.toolbox.feature.radix.domain.*
import com.cangshuo.toolbox.feature.radix.ui.RadixViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RadixViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    private fun TestScope.model(handle: SavedStateHandle = SavedStateHandle()): RadixViewModel {
        val model = RadixViewModel(ConvertRadixUseCase(LocalRadixRepository()), handle, dispatcher)
        models.put("radix", model)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        runCurrent()
        return model
    }
    @Test fun autoModeIsNullAtTheRepositoryBoundaryAndSelectedInUi() = runTest {
        val model = model()
        assertNull(model.uiState.value.selectedRadix)
        model.updateInput("0x10"); runCurrent()
        assertEquals("16", (model.uiState.value.result as RadixResult.Success).normalized)
        model.selectRadix(10); runCurrent()
        assertTrue(model.uiState.value.result is RadixResult.Error)
        model.selectRadix(null); runCurrent()
        assertNull(model.uiState.value.selectedRadix)
        assertEquals("16", (model.uiState.value.result as RadixResult.Success).normalized)
    }
    @Test fun savedDefaultAutoAndCustomBaseRestoreCorrectly() = runTest {
        val model = model(SavedStateHandle(mapOf("input" to "11", "radix" to -1)))
        assertEquals("11", (model.uiState.value.result as RadixResult.Success).normalized)
        model.enableCustomMode(); model.setCustomRadix(2); runCurrent()
        assertEquals("3", (model.uiState.value.result as RadixResult.Success).normalized)
        model.setUppercase(true); model.setCustomRadix(16); model.updateInput("ff"); runCurrent()
        assertEquals("FF", (model.uiState.value.result as RadixResult.Success).outputs.first { it.radix == 16 }.value)
    }
    @Test fun oversizedPasteIsRejectedWithFeedbackAndPreviousInputIsPreserved() = runTest {
        val model = model()
        model.updateInput("255"); runCurrent()
        model.updateInput("1".repeat(10_001)); runCurrent()
        assertEquals("255", model.uiState.value.inputText)
        assertEquals(RadixError.INPUT_TOO_LARGE, (model.uiState.value.result as RadixResult.Error).reason)
        model.clear(); runCurrent()
        assertEquals("", model.uiState.value.inputText)
        assertNull(model.uiState.value.result)
        assertFalse(model.uiState.value.isProcessing)
    }
    @Test fun latestInputAndOptionsOwnTheResultAfterRapidChanges() = runTest {
        val model = model()
        model.updateInput("10"); model.selectRadix(2)
        model.updateInput("ff"); model.selectRadix(16)
        model.updateInput("100"); model.selectRadix(null); runCurrent()
        val result = model.uiState.value.result as RadixResult.Success
        assertEquals("100", model.uiState.value.inputText)
        assertEquals("100", result.normalized)
        assertEquals(10, result.input.radix)
        assertFalse(model.uiState.value.isProcessing)
    }
}
