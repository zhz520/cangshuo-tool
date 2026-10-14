package com.cangshuo.toolbox.feature.texttools

import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.pinyin.data.LocalPinyinRepository
import com.cangshuo.toolbox.feature.pinyin.domain.ConvertToPinyinUseCase
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinCaseMode
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinCheckpoint
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinError
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinLimits
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinMode
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinOptions
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinOutcome
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinSeparator
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinToneStyle
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinVCharType
import com.cangshuo.toolbox.feature.pinyin.ui.PinyinViewModel
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PinyinTest {

    /** Locates the app module from the test working directory and reads the shipped table. */
    private val readingsFile: File by lazy {
        var dir: File? = File(System.getProperty("user.dir")!!).absoluteFile
        while (dir != null && !File(dir, "src/main/assets/pinyin/readings.tsv").isFile) {
            dir = dir.parentFile
        }
        File(
            requireNotNull(dir) { "cannot locate the app module from ${System.getProperty("user.dir")}" },
            "src/main/assets/pinyin/readings.tsv",
        )
    }

    private val repository = LocalPinyinRepository { FileInputStream(readingsFile) }

    private fun useCase(dispatcher: CoroutineDispatcher) = ConvertToPinyinUseCase(repository, dispatcher)

    private suspend fun TestScope.convert(
        text: String,
        options: PinyinOptions = PinyinOptions(),
    ): PinyinOutcome = useCase(UnconfinedTestDispatcher(testScheduler)).invoke(text, options)

    private suspend fun TestScope.successText(
        text: String,
        options: PinyinOptions = PinyinOptions(),
    ): String {
        val outcome = convert(text, options)
        assertTrue("expected a converted result for \"$text\" but was $outcome", outcome is PinyinOutcome.Success)
        return (outcome as PinyinOutcome.Success).text
    }

    @Test
    fun standardConversionWithToneMarks() = runTest {
        val outcome = convert("中国") as PinyinOutcome.Success
        assertEquals("zhōng guó", outcome.text)
        assertEquals(2, outcome.statistics.totalChars)
        assertEquals(2, outcome.statistics.chineseChars)
        assertEquals(0, outcome.statistics.nonChineseChars)
        assertEquals(0, outcome.statistics.unsupportedChineseChars)
    }

    @Test
    fun phraseDictionaryResolvesCommonPolyphones() = runTest {
        assertEquals("chóng qìng", successText("重庆"))
        assertEquals("yín háng", successText("银行"))
        assertEquals("yīn yuè", successText("音乐"))
        assertEquals("pián yi", successText("便宜"))
    }

    @Test
    fun neutralToneNeverPrintsTheDigitFive() = runTest {
        assertEquals("pián yi", successText("便宜"))
        assertEquals("pian2 yi", successText("便宜", PinyinOptions(toneStyle = PinyinToneStyle.TONE_NUMBER)))
        assertEquals("pian yi", successText("便宜", PinyinOptions(toneStyle = PinyinToneStyle.NO_TONE)))
        assertEquals("wèi le", successText("为了"))
    }

    @Test
    fun toneMarkStyleKeepsUnicodeUmlautForEveryVCharOption() = runTest {
        PinyinVCharType.entries.forEach { vCharType ->
            assertEquals(
                "tone marks ignore the ü option",
                "lǜ",
                successText("绿", PinyinOptions(vCharType = vCharType)),
            )
        }
        assertEquals("nǚ", successText("女"))
    }

    @Test
    fun toneNumbersAndVCharForms() = runTest {
        assertEquals("bei3 jing1", successText("北京", PinyinOptions(toneStyle = PinyinToneStyle.TONE_NUMBER)))
        val number = PinyinOptions(toneStyle = PinyinToneStyle.TONE_NUMBER)
        assertEquals("lv4", successText("绿", number.copy(vCharType = PinyinVCharType.V)))
        assertEquals("lu:4", successText("绿", number.copy(vCharType = PinyinVCharType.U_COLON)))
        assertEquals("lü4", successText("绿", number.copy(vCharType = PinyinVCharType.UNICODE)))
        assertEquals("bei jing", successText("北京", PinyinOptions(toneStyle = PinyinToneStyle.NO_TONE)))
    }

    @Test
    fun caseModes() = runTest {
        assertEquals("NǏ HǍO", successText("你好", PinyinOptions(caseMode = PinyinCaseMode.UPPER)))
        assertEquals("Nǐ Hǎo", successText("你好", PinyinOptions(caseMode = PinyinCaseMode.CAPITALIZE)))
    }

    @Test
    fun separatorsIncludingSingleLetterSyllables() = runTest {
        assertEquals(
            "běi-jīng-huān-yíng-nǐ",
            successText("北京欢迎你", PinyinOptions(separator = PinyinSeparator.HYPHEN)),
        )
        assertEquals(
            "běijīnghuānyíngnǐ",
            successText("北京欢迎你", PinyinOptions(separator = PinyinSeparator.NONE)),
        )
        assertEquals(
            "a-yi",
            successText(
                "阿姨",
                PinyinOptions(separator = PinyinSeparator.HYPHEN, toneStyle = PinyinToneStyle.NO_TONE),
            ),
        )
        assertEquals("a yi", successText("阿姨", PinyinOptions(toneStyle = PinyinToneStyle.NO_TONE)))
        assertEquals("ā-yí", successText("阿姨", PinyinOptions(separator = PinyinSeparator.HYPHEN)))
    }

    @Test
    fun initialsMode() = runTest {
        assertEquals(
            "zhrmghg",
            successText("中华人民共和国", PinyinOptions(mode = PinyinMode.INITIALS)),
        )
        assertEquals(
            "ZHRMGHG",
            successText(
                "中华人民共和国",
                PinyinOptions(mode = PinyinMode.INITIALS, caseMode = PinyinCaseMode.UPPER),
            ),
        )
        assertEquals(
            "Zg!",
            successText("中国!", PinyinOptions(mode = PinyinMode.INITIALS, caseMode = PinyinCaseMode.CAPITALIZE)),
        )
    }

    @Test
    fun polyphoneQueryListsEveryReadingOnce() = runTest {
        val text = successText("重", PinyinOptions(mode = PinyinMode.POLYPHONE))
        assertTrue(text, text.startsWith("重: "))
        assertTrue(text, text.contains("zhòng"))
        assertTrue(text, text.contains("chóng"))
        val readings = text.substringAfter(": ").split(" / ")
        assertEquals(readings.size, readings.distinct().size)
    }

    @Test
    fun followingPhraseWinsWhenTheReadingsConflict() = runTest {
        assertEquals("yǒu kōng tiáo", successText("有空调"))
    }

    @Test
    fun unsupportedCharactersStayVisibleAndAreCounted() = runTest {
        val filtered = successText("𠮷", PinyinOptions(keepNonChinese = false))
        assertEquals("𠮷", filtered)
        val outcome = convert("𠮷", PinyinOptions(keepNonChinese = false)) as PinyinOutcome.Success
        assertEquals(1, outcome.statistics.chineseChars)
        assertEquals(1, outcome.statistics.unsupportedChineseChars)
        assertEquals(0, outcome.statistics.nonChineseChars)
    }

    @Test
    fun nonChineseCharactersFollowTheKeepSwitch() = runTest {
        val mixed = convert("Hello 中国 2026!") as PinyinOutcome.Success
        assertEquals("Hello zhōng guó 2026!", mixed.text)
        assertEquals(2, mixed.statistics.chineseChars)
        assertEquals(12, mixed.statistics.nonChineseChars)
        assertEquals("zhōng guó", successText("Hello 中国 2026!", PinyinOptions(keepNonChinese = false)))
    }

    @Test
    fun inputTooLongFailsGracefully() = runTest {
        val outcome = convert("中".repeat(PinyinLimits.MAX_INPUT_LENGTH + 1))
        assertTrue(outcome is PinyinOutcome.Failure)
        assertEquals(PinyinError.INPUT_TOO_LONG, (outcome as PinyinOutcome.Failure).error)
    }

    @Test
    fun outputLimitStopsInsteadOfTruncating() = runTest {
        val outcome = convert("中".repeat(70_000), PinyinOptions(mode = PinyinMode.POLYPHONE))
        assertTrue("expected the output limit to stop the conversion but was $outcome", outcome is PinyinOutcome.Failure)
        assertEquals(PinyinError.OUTPUT_TOO_LONG, (outcome as PinyinOutcome.Failure).error)
    }

    @Test
    fun repositoryHonoursTheCheckpoint() = runTest {
        var calls = 0
        val failure = runCatching {
            repository.convert("中".repeat(8_000), PinyinOptions()) {
                calls++
                if (calls > 2) throw IllegalStateException("stop")
            }
        }.exceptionOrNull()
        assertTrue("checkpoint was never asked to stop", calls > 2)
        assertTrue(failure is IllegalStateException)
    }

    private fun TestScope.viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        dispatcher: CoroutineDispatcher,
    ): PinyinViewModel {
        Dispatchers.setMain(dispatcher)
        return PinyinViewModel(useCase(dispatcher), handle, debounceMillis = 0)
    }

    @Test
    fun viewModelConvertsAndPublishesOneResult() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        try {
            val model = viewModel(dispatcher = dispatcher)
            assertNull(model.uiState.value.outcome)
            model.updateInput("中国")
            assertTrue(model.uiState.value.processing)
            advanceUntilIdle()
            val state = model.uiState.value
            assertFalse(state.processing)
            assertEquals("zhōng guó", (state.outcome as PinyinOutcome.Success).text)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun viewModelDropsSupersededResults() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        try {
            val model = viewModel(dispatcher = dispatcher)
            model.updateInput("中国")
            runCurrent()
            model.updateInput("北京")
            advanceUntilIdle()
            assertEquals("běi jīng", (model.uiState.value.outcome as PinyinOutcome.Success).text)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun cancelStopsTheResultAndRetryConvertsAgain() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        try {
            val model = viewModel(dispatcher = dispatcher)
            model.updateInput("中国")
            model.cancel()
            advanceUntilIdle()
            assertTrue(model.uiState.value.cancelled)
            assertNull(model.uiState.value.outcome)
            assertFalse(model.uiState.value.processing)
            model.retry()
            advanceUntilIdle()
            assertFalse(model.uiState.value.cancelled)
            assertEquals("zhōng guó", (model.uiState.value.outcome as PinyinOutcome.Success).text)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun clearStopsThePendingConversion() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        try {
            val model = viewModel(dispatcher = dispatcher)
            model.updateInput("中国")
            model.clear()
            advanceUntilIdle()
            val state = model.uiState.value
            assertEquals("", state.input)
            assertNull(state.outcome)
            assertFalse(state.processing)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun overLongInputKeepsThePreviousTextAndReportsTheLimit() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        try {
            val model = viewModel(dispatcher = dispatcher)
            model.updateInput("沧烁工具箱")
            advanceUntilIdle()
            model.updateInput("字".repeat(PinyinLimits.MAX_INPUT_LENGTH + 1))
            advanceUntilIdle()
            val state = model.uiState.value
            assertEquals("沧烁工具箱", state.input)
            assertEquals(PinyinError.INPUT_TOO_LONG, (state.outcome as PinyinOutcome.Failure).error)
            assertFalse(state.processing)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun viewModelRestoresSavedOptionsAndConverts() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        try {
            val handle = SavedStateHandle(
                mapOf(
                    "input" to "重庆",
                    "mode" to PinyinMode.POLYPHONE.name,
                    "tone_style" to PinyinToneStyle.TONE_NUMBER.name,
                    "case_mode" to PinyinCaseMode.UPPER.name,
                    "separator" to PinyinSeparator.HYPHEN.name,
                    "v_char" to PinyinVCharType.V.name,
                    "keep_non_chinese" to false,
                ),
            )
            val model = viewModel(handle = handle, dispatcher = dispatcher)
            advanceUntilIdle()
            val state = model.uiState.value
            assertEquals("重庆", state.input)
            assertEquals(PinyinMode.POLYPHONE, state.mode)
            assertEquals(PinyinToneStyle.TONE_NUMBER, state.toneStyle)
            assertEquals(PinyinCaseMode.UPPER, state.caseMode)
            assertEquals(PinyinSeparator.HYPHEN, state.separator)
            assertEquals(PinyinVCharType.V, state.vCharType)
            assertFalse(state.keepNonChinese)
            assertTrue(state.outcome is PinyinOutcome.Success)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun exampleFillsTheInputAndConverts() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        try {
            val model = viewModel(dispatcher = dispatcher)
            model.useExample("重庆银行便宜音乐")
            advanceUntilIdle()
            assertEquals("重庆银行便宜音乐", model.uiState.value.input)
            val text = (model.uiState.value.outcome as PinyinOutcome.Success).text
            assertTrue(text, text.contains("chóng"))
            assertTrue(text, text.contains("pián yi"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun checkpointsAreReachableForCancellation() = runTest {
        var invoked = false
        val checkpoint = PinyinCheckpoint { invoked = true }
        repository.convert("中国", PinyinOptions(), checkpoint)
        assertTrue(invoked)
    }
}
