package com.cangshuo.toolbox.feature.texttools

import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionError
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionLimits
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionMode
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionOutcome
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionRepository
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionStatistics
import com.cangshuo.toolbox.feature.chineseconvert.domain.ConvertChineseUseCase
import com.cangshuo.toolbox.feature.chineseconvert.ui.ChineseConversionViewModel
import com.cangshuo.toolbox.feature.chineseconvert.ui.previewChineseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChineseConversionViewModelTest {
    private fun result(text: String) = ChineseConversionOutcome.Success(text, ChineseConversionStatistics(1, 1, 1))

    @Test
    fun rapidEditsOnlyEvaluateLatestInput() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val calls = mutableListOf<String>()
            val model = ChineseConversionViewModel(ConvertChineseUseCase(ChineseConversionRepository { text, _, _ ->
                calls += text
                result(text)
            }, dispatcher), SavedStateHandle())
            model.updateInput("头发")
            runCurrent()
            advanceTimeBy(50)
            model.updateInput("发展")
            advanceUntilIdle()
            assertEquals(listOf("发展"), calls)
            assertEquals("发展", (model.uiState.value.outcome as ChineseConversionOutcome.Success).text)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun overLimitPasteKeepsPreviousInputAndStopsPendingWork() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            var calls = 0
            val saved = SavedStateHandle()
            val model = ChineseConversionViewModel(ConvertChineseUseCase(ChineseConversionRepository { text, _, _ ->
                calls++; result(text)
            }, dispatcher), saved)
            model.updateInput("原文")
            model.updateInput("字".repeat(ChineseConversionLimits.MAX_INPUT + 1))
            advanceUntilIdle()
            assertEquals("原文", model.uiState.value.input)
            assertEquals("原文", saved.get<String>("input"))
            assertEquals(0, calls)
            assertEquals(ChineseConversionOutcome.Failure(ChineseConversionError.INPUT_TOO_LONG), model.uiState.value.outcome)
            assertFalse(model.uiState.value.processing)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun cancelCanRetryAndClearDoesNotEvaluate() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            var calls = 0
            val model = ChineseConversionViewModel(ConvertChineseUseCase(ChineseConversionRepository { text, _, _ ->
                calls++; result(text)
            }, dispatcher), SavedStateHandle())
            model.updateInput("测试")
            model.cancel()
            advanceUntilIdle()
            assertEquals(0, calls)
            assertTrue(model.uiState.value.cancelled)
            model.retry()
            advanceUntilIdle()
            assertEquals(1, calls)
            assertFalse(model.uiState.value.cancelled)
            model.clear()
            advanceUntilIdle()
            assertEquals(1, calls)
            assertEquals("", model.uiState.value.input)
            assertNull(model.uiState.value.outcome)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun restoredModelUsesEditedInputAndMode() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val useCase = ConvertChineseUseCase(ChineseConversionRepository { text, mode, _ -> result("${mode.name}:$text") }, dispatcher)
            val saved = SavedStateHandle()
            val original = ChineseConversionViewModel(useCase, saved)
            original.updateInput("滑鼠")
            original.selectMode(ChineseConversionMode.TW2SP)
            advanceUntilIdle()
            val restored = ChineseConversionViewModel(useCase, SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
            advanceUntilIdle()
            assertEquals(original.uiState.value, restored.uiState.value)
            assertEquals("TW2SP:滑鼠", (restored.uiState.value.outcome as ChineseConversionOutcome.Success).text)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun invalidRestoredStateFallsBackSafely() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val model = ChineseConversionViewModel(ConvertChineseUseCase(ChineseConversionRepository { text, _, _ -> result(text) }, dispatcher),
                SavedStateHandle(mapOf("input" to "文".repeat(100_001), "mode" to "unknown")))
            advanceUntilIdle()
            assertEquals("", model.uiState.value.input)
            assertEquals(ChineseConversionMode.S2T, model.uiState.value.mode)
            assertFalse(model.uiState.value.processing)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun reverseReusesFullResultAndPairedModeAtomically() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val calls = mutableListOf<Pair<String, ChineseConversionMode>>()
            val converted = "軟體".repeat(7_000)
            val model = ChineseConversionViewModel(ConvertChineseUseCase(ChineseConversionRepository { text, mode, _ ->
                calls += text to mode
                result(converted)
            }, dispatcher), SavedStateHandle())
            model.selectMode(ChineseConversionMode.S2TWP)
            model.updateInput("软件")
            advanceUntilIdle()
            model.reverseResult()
            advanceUntilIdle()
            assertEquals(2, calls.size)
            assertEquals(converted to ChineseConversionMode.TW2SP, calls.last())
            assertEquals(converted, model.uiState.value.input)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun repositoryFailureDoesNotLeaveLoadingStuck() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val model = ChineseConversionViewModel(ConvertChineseUseCase(ChineseConversionRepository { _, _, _ ->
                error("unavailable data")
            }, dispatcher), SavedStateHandle())
            model.updateInput("字")
            advanceUntilIdle()
            assertFalse(model.uiState.value.processing)
            assertEquals(ChineseConversionOutcome.Failure(ChineseConversionError.FAILED), model.uiState.value.outcome)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun deadlineFailureIsExplicit() = runTest {
        var clock = 0L
        val useCase = ConvertChineseUseCase(ChineseConversionRepository { text, _, checkpoint ->
            clock += ChineseConversionLimits.BUDGET_NANOS + 1
            checkpoint.check()
            result(text)
        }, StandardTestDispatcher(testScheduler), nanoTime = { clock })
        assertEquals(ChineseConversionOutcome.Failure(ChineseConversionError.TIME_LIMIT), useCase("文本", ChineseConversionMode.S2T))
    }

    @Test
    fun previewDoesNotCutSurrogatePairOrModifyShortOutput() {
        val text = "字".repeat(11_999) + "😀" + "末"
        assertEquals("字".repeat(11_999), previewChineseResult(text))
        assertEquals("文😀", previewChineseResult("文😀"))
    }

    @Test
    fun reversePairsAreInvolutions() {
        ChineseConversionMode.entries.forEach { assertEquals(it, it.reverse.reverse) }
    }
}
