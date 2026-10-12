package com.cangshuo.toolbox.feature.urlextract

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.urlextract.data.LocalUrlExtractRepository
import com.cangshuo.toolbox.feature.urlextract.domain.ExtractUrlsUseCase
import com.cangshuo.toolbox.feature.urlextract.domain.UrlExtractError
import com.cangshuo.toolbox.feature.urlextract.domain.UrlExtractOutcome
import com.cangshuo.toolbox.feature.urlextract.domain.UrlOutputFormat
import com.cangshuo.toolbox.feature.urlextract.ui.UrlExtractViewModel
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
class UrlExtractViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    private fun TestScope.observe(model: ViewModel, state: Flow<*>) {
        models.put(model.javaClass.name, model)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect {} }
        runCurrent()
    }

    private fun model(handle: SavedStateHandle = SavedStateHandle()) = UrlExtractViewModel(
        ExtractUrlsUseCase(LocalUrlExtractRepository()),
        handle,
    )

    @Test
    fun blankInputShowsGuidanceAndModestSwitchesUpdateResults() = runTest {
        val viewModel = model()
        observe(viewModel, viewModel.uiState)
        assertNull(viewModel.uiState.value.outcome)
        viewModel.updateText("看看 example.org 和 https://a.com")
        runCurrent()
        var links = (viewModel.uiState.value.outcome as UrlExtractOutcome.Success).result.links
        assertEquals(listOf("https://a.com"), links)
        viewModel.toggleBareDomains(true)
        runCurrent()
        links = (viewModel.uiState.value.outcome as UrlExtractOutcome.Success).result.links
        assertEquals(listOf("example.org", "https://a.com"), links)
        assertEquals("example.org\nhttps://a.com", viewModel.uiState.value.copyText)
        viewModel.selectFormat(UrlOutputFormat.MARKDOWN)
        runCurrent()
        assertTrue(viewModel.uiState.value.copyText.startsWith("[example.org]"))
    }

    @Test
    fun oversizedPasteKeepsThePreviousText() = runTest {
        val viewModel = model(SavedStateHandle(mapOf("text" to "https://a.com")))
        observe(viewModel, viewModel.uiState)
        viewModel.updateText("x".repeat(200_001))
        runCurrent()
        assertEquals("https://a.com", viewModel.uiState.value.text)
        assertEquals(UrlExtractError.INPUT_TOO_LONG, (viewModel.uiState.value.outcome as UrlExtractOutcome.Failure).error)
        viewModel.clear()
        runCurrent()
        assertNull(viewModel.uiState.value.outcome)
    }
}
