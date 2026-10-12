package com.cangshuo.toolbox.feature.texttools

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.charconvert.data.LocalCharConvertRepository
import com.cangshuo.toolbox.feature.charconvert.domain.CaseMode
import com.cangshuo.toolbox.feature.charconvert.domain.CharConvertError
import com.cangshuo.toolbox.feature.charconvert.domain.CharConvertOutcome
import com.cangshuo.toolbox.feature.charconvert.domain.TransformCharsUseCase
import com.cangshuo.toolbox.feature.charconvert.ui.CharConvertViewModel
import com.cangshuo.toolbox.feature.numberchinese.data.LocalNumberChineseRepository
import com.cangshuo.toolbox.feature.numberchinese.domain.ConvertNumberUseCase
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberChineseError
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberChineseOutcome
import com.cangshuo.toolbox.feature.numberchinese.ui.NumberChineseViewModel
import com.cangshuo.toolbox.feature.similarity.data.LocalSimilarityRepository
import com.cangshuo.toolbox.feature.similarity.domain.CompareTextsUseCase
import com.cangshuo.toolbox.feature.similarity.domain.SimilarityError
import com.cangshuo.toolbox.feature.similarity.domain.SimilarityOutcome
import com.cangshuo.toolbox.feature.similarity.ui.SimilarityViewModel
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
class TextToolsViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    private fun TestScope.observe(model: ViewModel, state: Flow<*>) {
        models.put(model.javaClass.name, model)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect {} }
        runCurrent()
    }

    @Test
    fun numberChineseConvertsAndRejectsOverlongInput() = runTest {
        val viewModel = NumberChineseViewModel(ConvertNumberUseCase(LocalNumberChineseRepository()), SavedStateHandle())
        observe(viewModel, viewModel.uiState)
        assertNull(viewModel.uiState.value.outcome)
        viewModel.updateInput("1234")
        runCurrent()
        assertEquals(
            "一千二百三十四",
            (viewModel.uiState.value.outcome as NumberChineseOutcome.Success).text,
        )
        viewModel.updateInput("1".repeat(61))
        runCurrent()
        assertEquals("1234", viewModel.uiState.value.input)
        assertEquals(
            NumberChineseError.TOO_LONG,
            (viewModel.uiState.value.outcome as NumberChineseOutcome.Failure).error,
        )
        viewModel.clear()
        runCurrent()
        assertNull(viewModel.uiState.value.outcome)
    }

    @Test
    fun charConvertAppliesWidthAndCase() = runTest {
        val viewModel = CharConvertViewModel(TransformCharsUseCase(LocalCharConvertRepository()), SavedStateHandle())
        observe(viewModel, viewModel.uiState)
        viewModel.updateInput("ＡＢＣ abc")
        runCurrent()
        assertEquals(
            "ABC abc",
            (viewModel.uiState.value.outcome as CharConvertOutcome.Success).text,
        )
        viewModel.selectCase(CaseMode.UPPER)
        runCurrent()
        assertEquals(
            "ABC ABC",
            (viewModel.uiState.value.outcome as CharConvertOutcome.Success).text,
        )
        viewModel.updateInput("x".repeat(200_001))
        runCurrent()
        assertEquals("ＡＢＣ abc", viewModel.uiState.value.input)
        assertEquals(
            CharConvertError.INPUT_TOO_LONG,
            (viewModel.uiState.value.outcome as CharConvertOutcome.Failure).error,
        )
    }

    @Test
    fun similarityNeedsBothTextsAndComparesThem() = runTest {
        val viewModel = SimilarityViewModel(
            CompareTextsUseCase(LocalSimilarityRepository()),
            SavedStateHandle(),
            workerDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        observe(viewModel, viewModel.uiState)
        viewModel.updateFirst("kitten")
        runCurrent()
        assertNull(viewModel.uiState.value.outcome)
        viewModel.updateSecond("sitting")
        runCurrent()
        val result = (viewModel.uiState.value.outcome as SimilarityOutcome.Success).result
        assertEquals(3, result.distance)
        viewModel.updateSecond("x".repeat(3001))
        runCurrent()
        assertEquals("sitting", viewModel.uiState.value.second)
        assertEquals(
            SimilarityError.INPUT_TOO_LONG,
            (viewModel.uiState.value.outcome as SimilarityOutcome.Failure).error,
        )
        assertTrue(viewModel.uiState.value.first == "kitten")
    }
}
