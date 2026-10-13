package com.cangshuo.toolbox.feature.regextester

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.regextester.data.LocalRegexTesterRepository
import com.cangshuo.toolbox.feature.regextester.domain.RegexCapture
import com.cangshuo.toolbox.feature.regextester.domain.RegexFailure
import com.cangshuo.toolbox.feature.regextester.domain.RegexLimits
import com.cangshuo.toolbox.feature.regextester.domain.RegexMatch
import com.cangshuo.toolbox.feature.regextester.domain.RegexMode
import com.cangshuo.toolbox.feature.regextester.domain.RegexOutcome
import com.cangshuo.toolbox.feature.regextester.domain.RegexReport
import com.cangshuo.toolbox.feature.regextester.domain.RegexRequest
import com.cangshuo.toolbox.feature.regextester.domain.RegexTesterRepository
import com.cangshuo.toolbox.feature.regextester.domain.TestRegexUseCase
import com.cangshuo.toolbox.feature.regextester.ui.RegexTesterUiState
import com.cangshuo.toolbox.feature.regextester.ui.RegexTesterViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class RegexTesterViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val models = ViewModelStore()
    private val repositories = mutableListOf<ControlledRepository>()
    private var modelId = 0

    @After
    fun tearDown() {
        models.clear()
        // Release even deliberately uncooperative fakes if an assertion failed before completion.
        repositories.flatMap { it.calls }.forEach {
            it.result.complete(RegexOutcome.Failure(RegexFailure.PROCESSING_FAILED))
        }
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
    }

    private fun controlled(ignoreCancellation: Boolean = false) = ControlledRepository(ignoreCancellation).also {
        repositories += it
    }

    private fun createModel(
        repository: RegexTesterRepository,
        saved: SavedStateHandle = SavedStateHandle(),
    ) = RegexTesterViewModel(TestRegexUseCase(repository), saved).also { models.put("regex-${modelId++}", it) }

    private fun success(value: String) = RegexOutcome.Success(
        RegexReport(listOf(RegexMatch(0, value.length, value, emptyList())), true, null),
    )

    private fun RegexTesterViewModel.enter(input: String = "a") {
        setPattern("[a-z]+")
        setInput(input)
    }

    @Test
    fun editingDoesNotExecuteAndRunPublishesOnlyTheCompletedRequest() = runTest {
        val repository = controlled()
        val model = createModel(repository)
        assertEquals(RegexTesterUiState(), model.uiState.value)
        model.enter()
        val request = RegexRequest(pattern = "[a-z]+", input = "a")
        assertEquals(RegexTesterUiState(request), model.uiState.value)
        assertTrue(repository.calls.isEmpty())

        model.run()
        assertEquals(RegexTesterUiState(request, processing = true), model.uiState.value)
        runCurrent()
        assertEquals(listOf(request), repository.calls.map { it.request })
        assertEquals(RegexTesterUiState(request, processing = true), model.uiState.value)
        repository.calls.single().result.complete(success("a"))
        runCurrent()
        assertEquals(RegexTesterUiState(request, success("a")), model.uiState.value)
    }

    @Test
    fun everyOptionIsPassedThroughTheUseCaseToTheRealRepository() = runTest {
        val model = createModel(LocalRegexTesterRepository(mainDispatcherRule.dispatcher))
        model.setPattern("(?P<letter>ab)")
        model.setInput("AB ab")
        model.setReplacement("[${'$'}{letter}]")
        model.setMode(RegexMode.REPLACE)
        model.setGlobal(false)
        model.setIgnoreCase(true)
        model.setMultiline(true)
        model.setDotAll(true)
        model.setExpandGroups(true)
        val request = RegexRequest(
            pattern = "(?P<letter>ab)", input = "AB ab", replacement = "[${'$'}{letter}]", mode = RegexMode.REPLACE,
            global = false, ignoreCase = true, multiline = true, dotAll = true, expandGroups = true,
        )
        model.run()
        runCurrent()
        assertEquals(
            RegexTesterUiState(request, RegexOutcome.Success(RegexReport(
                listOf(RegexMatch(0, 2, "AB", listOf(RegexCapture(1, "letter", 0, 2, "AB")))), false, "[AB] ab",
            ))), model.uiState.value,
        )
    }

    @Test
    fun typedFailuresExitLoadingAndCanBeRetriedAfterEditing() = runTest {
        val repository = controlled()
        val model = createModel(repository)
        model.setPattern("[")
        model.setInput("a")
        model.run()
        runCurrent()
        repository.calls[0].result.complete(RegexOutcome.Failure(RegexFailure.INVALID_PATTERN))
        runCurrent()
        assertEquals(
            RegexTesterUiState(RegexRequest(pattern = "[", input = "a"), RegexOutcome.Failure(RegexFailure.INVALID_PATTERN)),
            model.uiState.value,
        )
        model.setPattern("a")
        assertNull(model.uiState.value.outcome)
        model.run()
        runCurrent()
        repository.calls[1].result.complete(success("a"))
        runCurrent()
        assertEquals(RegexTesterUiState(RegexRequest(pattern = "a", input = "a"), success("a")), model.uiState.value)
    }

    @Test
    fun unexpectedExceptionsAreMappedToAFixedFailureAndRetryStillWorks() = runTest {
        var calls = 0
        val repository = object : RegexTesterRepository {
            override suspend fun evaluate(request: RegexRequest): RegexOutcome {
                if (++calls == 1) throw IllegalStateException("Internal parser details")
                return success(request.input)
            }
        }
        val model = createModel(repository)
        model.enter()
        model.run()
        runCurrent()
        val request = RegexRequest(pattern = "[a-z]+", input = "a")
        assertEquals(RegexTesterUiState(request, RegexOutcome.Failure(RegexFailure.PROCESSING_FAILED)), model.uiState.value)
        model.run()
        runCurrent()
        assertEquals(2, calls)
        assertEquals(RegexTesterUiState(request, success("a")), model.uiState.value)
    }

    @Test
    fun cancellationFromTheRepositoryIsNotShownAsProcessingFailure() = runTest {
        val repository = object : RegexTesterRepository {
            override suspend fun evaluate(request: RegexRequest): RegexOutcome = throw CancellationException("Cancelled work")
        }
        val model = createModel(repository)
        model.enter()
        model.run()
        runCurrent()
        assertEquals(RegexTesterUiState(RegexRequest(pattern = "[a-z]+", input = "a")), model.uiState.value)
    }

    @Test
    fun cancelStopsCooperativeWorkClearsLoadingAndAllowsRetry() = runTest {
        val repository = controlled()
        val model = createModel(repository)
        model.enter()
        val request = model.uiState.value.request
        model.run()
        runCurrent()
        model.cancel()
        assertEquals(RegexTesterUiState(request), model.uiState.value)
        runCurrent()
        assertTrue(repository.calls[0].cancelled)
        assertFalse(repository.calls[0].returned)
        repository.calls[0].result.complete(success("a"))
        runCurrent()
        assertEquals(RegexTesterUiState(request), model.uiState.value)

        model.run()
        runCurrent()
        repository.calls[1].result.complete(success("a"))
        runCurrent()
        assertEquals(RegexTesterUiState(request, success("a")), model.uiState.value)
    }

    @Test
    fun cancelAlsoRejectsAResultFromWorkThatIgnoresCancellation() = runTest {
        val repository = controlled(ignoreCancellation = true)
        val model = createModel(repository)
        model.enter("old")
        val request = model.uiState.value.request
        model.run()
        runCurrent()
        model.cancel()
        repository.calls.single().result.complete(success("old"))
        runCurrent()
        assertTrue(repository.calls.single().returned)
        assertEquals(RegexTesterUiState(request), model.uiState.value)
    }

    @Test
    fun lateSuccessCannotOverwriteEditedInputEvenBeforeAnotherRun() = runTest {
        val repository = controlled(ignoreCancellation = true)
        val model = createModel(repository)
        model.enter("old")
        model.run()
        runCurrent()
        model.setInput("new")
        val expected = RegexTesterUiState(RegexRequest(pattern = "[a-z]+", input = "new"))
        assertEquals(expected, model.uiState.value)
        repository.calls.single().result.complete(success("old"))
        runCurrent()
        assertTrue(repository.calls.single().returned)
        assertEquals(expected, model.uiState.value)
    }

    @Test
    fun oldCompletionCannotTurnOffLoadingForTheNewRequest() = runTest {
        val repository = controlled(ignoreCancellation = true)
        val model = createModel(repository)
        model.enter("old")
        model.run()
        runCurrent()
        model.setInput("new")
        model.run()
        runCurrent()
        assertEquals(listOf("old", "new"), repository.calls.map { it.request.input })
        repository.calls[0].result.complete(success("old"))
        runCurrent()
        assertTrue(repository.calls[0].returned)
        val request = RegexRequest(pattern = "[a-z]+", input = "new")
        assertEquals(RegexTesterUiState(request, processing = true), model.uiState.value)
        repository.calls[1].result.complete(success("new"))
        runCurrent()
        assertEquals(RegexTesterUiState(request, success("new")), model.uiState.value)
    }

    @Test
    fun lateOldSuccessCannotOverwriteANewerCompletedResult() = runTest {
        val repository = controlled(ignoreCancellation = true)
        val model = createModel(repository)
        model.enter("old")
        model.run()
        runCurrent()
        model.setInput("new")
        model.run()
        runCurrent()
        repository.calls[1].result.complete(success("new"))
        runCurrent()
        val expected = RegexTesterUiState(RegexRequest(pattern = "[a-z]+", input = "new"), success("new"))
        assertEquals(expected, model.uiState.value)
        repository.calls[0].result.complete(success("old"))
        runCurrent()
        assertTrue(repository.calls[0].returned)
        assertEquals(expected, model.uiState.value)
    }

    @Test
    fun lateOldFailureOrExceptionCannotReplaceANewerSuccess() = runTest {
        for (throwsException in listOf(false, true)) {
            val repository = controlled(ignoreCancellation = true)
            val model = createModel(repository)
            model.enter("old")
            model.run()
            runCurrent()
            model.setInput("new")
            model.run()
            runCurrent()
            repository.calls[1].result.complete(success("new"))
            runCurrent()
            val expected = RegexTesterUiState(RegexRequest(pattern = "[a-z]+", input = "new"), success("new"))
            assertEquals(expected, model.uiState.value)
            if (throwsException) repository.calls[0].result.completeExceptionally(IllegalStateException("Old work failed"))
            else repository.calls[0].result.complete(RegexOutcome.Failure(RegexFailure.INVALID_PATTERN))
            runCurrent()
            assertTrue(repository.calls[0].finished)
            assertEquals(expected, model.uiState.value)
        }
    }

    @Test
    fun editsToAnyRequestFieldInvalidatePendingResultsAndPersistTheNewRequest() = runTest {
        val initial = RegexRequest(pattern = "[a-z]+", input = "a")
        val edits: List<Pair<(RegexTesterViewModel) -> Unit, RegexRequest>> = listOf(
            ({ viewModel: RegexTesterViewModel -> viewModel.setPattern(".+") }) to initial.copy(pattern = ".+"),
            ({ viewModel: RegexTesterViewModel -> viewModel.setInput("new") }) to initial.copy(input = "new"),
            ({ viewModel: RegexTesterViewModel -> viewModel.setReplacement("X") }) to initial.copy(replacement = "X"),
            ({ viewModel: RegexTesterViewModel -> viewModel.setMode(RegexMode.REPLACE) }) to initial.copy(mode = RegexMode.REPLACE),
            ({ viewModel: RegexTesterViewModel -> viewModel.setGlobal(false) }) to initial.copy(global = false),
            ({ viewModel: RegexTesterViewModel -> viewModel.setIgnoreCase(true) }) to initial.copy(ignoreCase = true),
            ({ viewModel: RegexTesterViewModel -> viewModel.setMultiline(true) }) to initial.copy(multiline = true),
            ({ viewModel: RegexTesterViewModel -> viewModel.setDotAll(true) }) to initial.copy(dotAll = true),
            ({ viewModel: RegexTesterViewModel -> viewModel.setExpandGroups(true) }) to initial.copy(expandGroups = true),
        )
        for ((edit, expectedRequest) in edits) {
            val repository = controlled(ignoreCancellation = true)
            val saved = SavedStateHandle()
            val model = createModel(repository, saved)
            model.enter()
            model.run()
            runCurrent()
            edit(model)
            val expected = RegexTesterUiState(expectedRequest)
            assertEquals(expected, model.uiState.value)
            repository.calls.single().result.complete(success("a"))
            runCurrent()
            assertTrue(repository.calls.single().returned)
            assertEquals(expected, model.uiState.value)
            assertEquals(expected, createModel(controlled(), saved).uiState.value)
        }
    }

    @Test
    fun clearErasesInputReplacementAndPendingResultWhilePreservingPatternAndOptions() = runTest {
        val repository = controlled(ignoreCancellation = true)
        val saved = SavedStateHandle()
        val model = createModel(repository, saved)
        model.enter("old")
        model.setReplacement("X")
        model.setMode(RegexMode.REPLACE)
        model.setGlobal(false)
        model.setIgnoreCase(true)
        model.setMultiline(true)
        model.setDotAll(true)
        model.setExpandGroups(true)
        val request = model.uiState.value.request
        model.run()
        runCurrent()
        model.clear()
        val expected = RegexTesterUiState(request.copy(input = "", replacement = ""))
        assertEquals(expected, model.uiState.value)
        assertEquals("", saved.get<String>("input"))
        assertEquals("", saved.get<String>("replacement"))
        repository.calls.single().result.complete(RegexOutcome.Success(
            RegexReport(listOf(RegexMatch(0, 3, "old", emptyList())), true, "X"),
        ))
        runCurrent()
        assertTrue(repository.calls.single().returned)
        assertEquals(expected, model.uiState.value)
        assertEquals(expected, createModel(controlled(), saved).uiState.value)
    }

    @Test
    fun rerunningClearsThePreviousResultBeforeNewWorkCompletes() = runTest {
        val repository = controlled()
        val model = createModel(repository)
        model.enter()
        val request = model.uiState.value.request
        model.run()
        runCurrent()
        repository.calls[0].result.complete(success("a"))
        runCurrent()
        assertEquals(RegexTesterUiState(request, success("a")), model.uiState.value)
        model.run()
        assertEquals(RegexTesterUiState(request, processing = true), model.uiState.value)
        runCurrent()
        repository.calls[1].result.complete(RegexOutcome.Failure(RegexFailure.TIME_BUDGET))
        runCurrent()
        assertEquals(RegexTesterUiState(request, RegexOutcome.Failure(RegexFailure.TIME_BUDGET)), model.uiState.value)
        model.clear()
        assertEquals(RegexTesterUiState(request.copy(input = "")), model.uiState.value)
    }

    @Test
    fun oversizedPastesPreserveTheEntirePreviousRequestAndDoNotPersistATruncatedValue() = runTest {
        val pastes: List<Pair<(RegexTesterViewModel) -> Unit, RegexFailure>> = listOf(
            ({ viewModel: RegexTesterViewModel -> viewModel.setPattern("x".repeat(RegexLimits.MAX_PATTERN + 1)) }) to RegexFailure.PATTERN_TOO_LONG,
            ({ viewModel: RegexTesterViewModel -> viewModel.setInput("😀".repeat(RegexLimits.MAX_INPUT / 2) + "x") }) to RegexFailure.INPUT_TOO_LONG,
            ({ viewModel: RegexTesterViewModel -> viewModel.setReplacement("x".repeat(RegexLimits.MAX_REPLACEMENT + 1)) }) to RegexFailure.REPLACEMENT_TOO_LONG,
        )
        for ((paste, reason) in pastes) {
            val repository = controlled(ignoreCancellation = true)
            val saved = SavedStateHandle()
            val model = createModel(repository, saved)
            model.enter()
            model.setReplacement("original")
            val request = model.uiState.value.request
            model.run()
            runCurrent()
            paste(model)
            val expected = RegexTesterUiState(request, RegexOutcome.Failure(reason))
            assertEquals(expected, model.uiState.value)
            assertEquals(request.pattern, saved.get<String>("pattern"))
            assertEquals(request.input, saved.get<String>("input"))
            assertEquals(request.replacement, saved.get<String>("replacement"))
            repository.calls.single().result.complete(success("a"))
            runCurrent()
            assertTrue(repository.calls.single().returned)
            assertEquals(expected, model.uiState.value)
            assertEquals(RegexTesterUiState(request), createModel(controlled(), saved).uiState.value)
            model.setInput("fixed")
            assertEquals(RegexTesterUiState(request.copy(input = "fixed")), model.uiState.value)
        }
    }

    @Test
    fun textFieldsAcceptTheirExactUtf16LimitsWithoutTruncatingOrExecuting() = runTest {
        val repository = controlled()
        val saved = SavedStateHandle()
        val model = createModel(repository, saved)
        val pattern = "a".repeat(RegexLimits.MAX_PATTERN)
        val input = "😀".repeat(RegexLimits.MAX_INPUT / 2)
        val replacement = "x".repeat(RegexLimits.MAX_REPLACEMENT)
        model.setPattern(pattern)
        model.setInput(input)
        model.setReplacement(replacement)
        val expected = RegexTesterUiState(RegexRequest(pattern = pattern, input = input, replacement = replacement))
        assertEquals(expected, model.uiState.value)
        assertEquals(pattern, saved.get<String>("pattern"))
        assertEquals(input, saved.get<String>("input"))
        assertEquals(replacement, saved.get<String>("replacement"))
        assertTrue(repository.calls.isEmpty())
        assertEquals(expected, createModel(controlled(), saved).uiState.value)
    }

    @Test
    fun savedStateRestoresAllRequestFieldsWithoutRestoringAResultOrRunningWork() = runTest {
        val saved = SavedStateHandle()
        val repository = controlled()
        val model = createModel(repository, saved)
        model.setPattern("(?P<word>.+)")
        model.setInput("A😀\nB")
        model.setReplacement("[${'$'}{word}]")
        model.setMode(RegexMode.REPLACE)
        model.setGlobal(false)
        model.setIgnoreCase(true)
        model.setMultiline(true)
        model.setDotAll(true)
        model.setExpandGroups(true)
        val request = RegexRequest(
            pattern = "(?P<word>.+)", input = "A😀\nB", replacement = "[${'$'}{word}]", mode = RegexMode.REPLACE,
            global = false, ignoreCase = true, multiline = true, dotAll = true, expandGroups = true,
        )
        model.run()
        runCurrent()
        assertEquals(request, repository.calls.single().request)
        repository.calls.single().result.complete(RegexOutcome.Success(RegexReport(listOf(
            RegexMatch(0, 5, "A😀\nB", listOf(RegexCapture(1, "word", 0, 5, "A😀\nB"))),
        ), true, "[A😀\nB]")))
        runCurrent()
        assertTrue(model.uiState.value.outcome is RegexOutcome.Success)
        assertEquals(setOf("pattern", "input", "replacement", "mode", "global", "ignoreCase", "multiline", "dotAll", "expandGroups"), saved.keys())
        assertEquals("REPLACE", saved.get<String>("mode"))
        models.clear()

        val restoredRepository = controlled()
        val restored = createModel(restoredRepository, saved)
        assertEquals(RegexTesterUiState(request), restored.uiState.value)
        runCurrent()
        assertTrue(restoredRepository.calls.isEmpty())
        restored.run()
        runCurrent()
        assertEquals(request, restoredRepository.calls.single().request)
        restoredRepository.calls.single().result.complete(RegexOutcome.Failure(RegexFailure.TIME_BUDGET))
        runCurrent()
        assertFalse(restored.uiState.value.processing)
    }

    @Test
    fun restoringOversizedSavedFieldsRejectsWholeValuesAndUnknownModeUsesTheDefault() = runTest {
        val saved = SavedStateHandle(mapOf(
            "pattern" to "x".repeat(RegexLimits.MAX_PATTERN + 1),
            "input" to "x".repeat(RegexLimits.MAX_INPUT + 1),
            "replacement" to "x".repeat(RegexLimits.MAX_REPLACEMENT + 1),
            "mode" to "REMOVED_MODE",
            "global" to false,
            "ignoreCase" to true,
        ))
        val repository = controlled()
        val model = createModel(repository, saved)
        assertEquals(RegexTesterUiState(RegexRequest(global = false, ignoreCase = true)), model.uiState.value)
        runCurrent()
        assertTrue(repository.calls.isEmpty())
        model.enter("valid")
        assertEquals("[a-z]+", saved.get<String>("pattern"))
        assertEquals("valid", saved.get<String>("input"))
        assertEquals("", saved.get<String>("replacement"))
        assertEquals("MATCH", saved.get<String>("mode"))
    }

    private class PendingCall(val request: RegexRequest) {
        val result = CompletableDeferred<RegexOutcome>()
        var cancelled = false
        var returned = false
        var finished = false
    }

    private class ControlledRepository(private val ignoreCancellation: Boolean) : RegexTesterRepository {
        val calls = mutableListOf<PendingCall>()

        override suspend fun evaluate(request: RegexRequest): RegexOutcome {
            val call = PendingCall(request)
            calls += call
            try {
                val result = if (ignoreCancellation) withContext(NonCancellable) { call.result.await() }
                else call.result.await()
                call.returned = true
                return result
            } catch (cancelled: CancellationException) {
                call.cancelled = true
                throw cancelled
            } finally {
                call.finished = true
            }
        }
    }

    class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
        override fun starting(description: Description) { Dispatchers.setMain(dispatcher) }
        override fun finished(description: Description) { Dispatchers.resetMain() }
    }
}
