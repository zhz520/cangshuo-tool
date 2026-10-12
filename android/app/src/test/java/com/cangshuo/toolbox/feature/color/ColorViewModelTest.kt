package com.cangshuo.toolbox.feature.color

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.color.data.LocalColorRepository
import com.cangshuo.toolbox.feature.color.domain.ColorError
import com.cangshuo.toolbox.feature.color.domain.ConvertColorUseCase
import com.cangshuo.toolbox.feature.color.ui.ColorOutcome
import com.cangshuo.toolbox.feature.color.ui.ColorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ColorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    private fun TestScope.observe(model: ViewModel, state: Flow<*>) {
        models.put(model.javaClass.name, model)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect {} }
        runCurrent()
    }

    private fun model(handle: SavedStateHandle = SavedStateHandle()) = ColorViewModel(
        ConvertColorUseCase(LocalColorRepository()),
        handle,
    )

    @Test
    fun emptyInputShowsGuidanceAndPartialInputKeepsTheError() = runTest {
        val viewModel = model()
        observe(viewModel, viewModel.uiState)
        assertNull(viewModel.uiState.value.outcome)
        viewModel.updateInput("rgb(1,2")
        runCurrent()
        assertEquals(ColorError.INVALID_FORMAT, (viewModel.uiState.value.outcome as ColorOutcome.Failure).error)
        viewModel.updateInput("#FF8800")
        runCurrent()
        val description = (viewModel.uiState.value.outcome as ColorOutcome.Success).description
        assertEquals("#FF8800", description.hex)
        assertEquals("hsl(32, 100%, 50%)", description.hsl)
    }

    @Test
    fun oversizedPasteKeepsThePreviousValue() = runTest {
        val viewModel = model(SavedStateHandle(mapOf("input" to "#123456")))
        observe(viewModel, viewModel.uiState)
        assertTrue(viewModel.uiState.value.outcome is ColorOutcome.Success)
        viewModel.updateInput("#".repeat(50) + "1")
        runCurrent()
        assertEquals("#123456", viewModel.uiState.value.inputText)
        assertEquals(ColorError.INPUT_TOO_LONG, (viewModel.uiState.value.outcome as ColorOutcome.Failure).error)
        viewModel.clear()
        runCurrent()
        assertNull(viewModel.uiState.value.outcome)
        assertEquals("", viewModel.uiState.value.inputText)
    }
}
