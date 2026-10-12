package com.cangshuo.toolbox.feature.textcount

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.textcount.data.LocalTextCountRepository
import com.cangshuo.toolbox.feature.textcount.domain.AnalyzeTextUseCase
import com.cangshuo.toolbox.feature.textcount.domain.TextCountError
import com.cangshuo.toolbox.feature.textcount.domain.TextCountOutcome
import com.cangshuo.toolbox.feature.textcount.ui.TextCountViewModel
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TextCountViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    private fun TestScope.observe(model: ViewModel, state: Flow<*>) {
        models.put(model.javaClass.name, model)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect {} }
        runCurrent()
    }

    private fun model(handle: SavedStateHandle = SavedStateHandle()) = TextCountViewModel(
        AnalyzeTextUseCase(LocalTextCountRepository()),
        handle,
    )

    @Test
    fun blankInputShowsGuidanceAndTypedTextIsAnalyzed() = runTest {
        val viewModel = model()
        observe(viewModel, viewModel.uiState)
        assertNull(viewModel.uiState.value.outcome)
        viewModel.updateText("hello 你好")
        runCurrent()
        val stats = (viewModel.uiState.value.outcome as TextCountOutcome.Success).stats
        assertEquals(2, stats.cjkCharacters)
        assertEquals(1, stats.latinWords)
        viewModel.clear()
        runCurrent()
        assertNull(viewModel.uiState.value.outcome)
    }

    @Test
    fun oversizedPasteKeepsThePreviousText() = runTest {
        val viewModel = model(SavedStateHandle(mapOf("text" to "hello")))
        observe(viewModel, viewModel.uiState)
        viewModel.updateText("x".repeat(200_001))
        runCurrent()
        assertEquals("hello", viewModel.uiState.value.text)
        assertEquals(TextCountError.INPUT_TOO_LONG, (viewModel.uiState.value.outcome as TextCountOutcome.Failure).error)
        viewModel.updateText("world")
        runCurrent()
        assertEquals(1, (viewModel.uiState.value.outcome as TextCountOutcome.Success).stats.latinWords)
    }
}
