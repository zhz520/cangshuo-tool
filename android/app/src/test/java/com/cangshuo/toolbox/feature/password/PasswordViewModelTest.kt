package com.cangshuo.toolbox.feature.password

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.password.data.LocalPasswordRepository
import com.cangshuo.toolbox.feature.password.domain.GeneratePasswordsUseCase
import com.cangshuo.toolbox.feature.password.domain.PasswordError
import com.cangshuo.toolbox.feature.password.domain.PasswordOutcome
import com.cangshuo.toolbox.feature.password.domain.RandomSource
import com.cangshuo.toolbox.feature.password.ui.PasswordViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    private fun TestScope.observe(model: ViewModel, state: Flow<*>) {
        models.put(model.javaClass.name, model)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect {} }
        runCurrent()
    }

    private fun model(handle: SavedStateHandle = SavedStateHandle()) = PasswordViewModel(
        GeneratePasswordsUseCase(LocalPasswordRepository(RandomSource { 0 })),
        handle,
    )

    @Test
    fun defaultsGenerateAndLengthErrorsSurfaceImmediately() = runTest {
        val viewModel = model()
        observe(viewModel, viewModel.uiState)
        val first = (viewModel.uiState.value.outcome as PasswordOutcome.Success).passwords.single().value
        assertEquals(16, first.length)
        viewModel.updateLength("3")
        runCurrent()
        assertEquals(PasswordError.LENGTH_RANGE, (viewModel.uiState.value.outcome as PasswordOutcome.Failure).error)
        viewModel.updateLength("24")
        runCurrent()
        assertEquals(24, (viewModel.uiState.value.outcome as PasswordOutcome.Success).passwords.single().value.length)
    }

    @Test
    fun togglesAndRegenerateRefreshTheResult() = runTest {
        val viewModel = model()
        observe(viewModel, viewModel.uiState)
        viewModel.toggleSymbols(false)
        viewModel.toggleUppercase(false)
        viewModel.toggleDigits(false)
        runCurrent()
        val lowercaseOnly = (viewModel.uiState.value.outcome as PasswordOutcome.Success).passwords.single().value
        assertTrue(lowercaseOnly.all { it in 'a'..'z' })
        assertEquals(26 - 3, (viewModel.uiState.value.outcome as PasswordOutcome.Success).poolSize)
        viewModel.regenerate()
        runCurrent()
        assertEquals(
            lowercaseOnly,
            (viewModel.uiState.value.outcome as PasswordOutcome.Success).passwords.single().value,
        )
    }

    @Test
    fun oversizedExcludeTextIsRejectedWithoutOverwritingTheOldValue() = runTest {
        val viewModel = model(SavedStateHandle(mapOf("exclude" to "abc")))
        observe(viewModel, viewModel.uiState)
        viewModel.updateExclude("x".repeat(33))
        runCurrent()
        assertEquals("abc", viewModel.uiState.value.excludeText)
        assertEquals(PasswordError.INPUT_TOO_LONG, (viewModel.uiState.value.outcome as PasswordOutcome.Failure).error)
        viewModel.updateExclude("xyz")
        runCurrent()
        assertTrue(viewModel.uiState.value.outcome is PasswordOutcome.Success)
    }
}
