package com.cangshuo.toolbox.feature.kinship

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.kinship.domain.AskKinshipUseCase
import com.cangshuo.toolbox.feature.kinship.domain.KinshipError
import com.cangshuo.toolbox.feature.kinship.domain.KinshipOutcome
import com.cangshuo.toolbox.feature.kinship.domain.KinshipQuery
import com.cangshuo.toolbox.feature.kinship.domain.KinshipRepository
import com.cangshuo.toolbox.feature.kinship.domain.KinshipSex
import com.cangshuo.toolbox.feature.kinship.ui.KinshipViewModel
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
class KinshipViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    private fun TestScope.observe(model: ViewModel, state: Flow<*>) {
        models.put(model.javaClass.name, model)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect {} }
        runCurrent()
    }

    private class FileRepository : KinshipRepository {
        override fun query(query: KinshipQuery): List<String> = KinshipTestFiles.engine.query(query)
    }

    private fun TestScope.model(handle: SavedStateHandle = SavedStateHandle()) = KinshipViewModel(
        AskKinshipUseCase(FileRepository()),
        handle,
        workerDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    @Test
    fun quickChipsBuildAChainAndProduceTitles() = runTest {
        val viewModel = model()
        observe(viewModel, viewModel.uiState)
        assertNull(viewModel.uiState.value.outcome)
        viewModel.appendRelation("爸爸")
        runCurrent()
        viewModel.appendRelation("妈妈")
        runCurrent()
        assertEquals("爸爸的妈妈", viewModel.uiState.value.text)
        assertEquals(
            listOf("奶奶"),
            (viewModel.uiState.value.outcome as KinshipOutcome.Success).titles,
        )
        viewModel.removeLastRelation()
        runCurrent()
        assertEquals("爸爸", viewModel.uiState.value.text)
        viewModel.clear()
        runCurrent()
        assertNull(viewModel.uiState.value.outcome)
    }

    @Test
    fun reverseAndGenderOptionsChangeTheResult() = runTest {
        val viewModel = model(SavedStateHandle(mapOf("text" to "爸爸")))
        observe(viewModel, viewModel.uiState)
        assertEquals(
            listOf("爸爸"),
            (viewModel.uiState.value.outcome as KinshipOutcome.Success).titles,
        )
        viewModel.toggleReverse(true)
        viewModel.selectSex(KinshipSex.MALE)
        runCurrent()
        assertEquals(
            listOf("儿子"),
            (viewModel.uiState.value.outcome as KinshipOutcome.Success).titles,
        )
    }

    @Test
    fun oversizedInputIsRejectedWithoutOverwriting() = runTest {
        val viewModel = model(SavedStateHandle(mapOf("text" to "爸爸")))
        observe(viewModel, viewModel.uiState)
        viewModel.updateText("爸".repeat(61))
        runCurrent()
        assertEquals("爸爸", viewModel.uiState.value.text)
        assertEquals(
            KinshipError.INPUT_TOO_LONG,
            (viewModel.uiState.value.outcome as KinshipOutcome.Failure).error,
        )
    }
}
