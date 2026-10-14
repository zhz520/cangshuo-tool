package com.cangshuo.toolbox.feature.texttools

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.pinyin.data.LocalPinyinRepository
import com.cangshuo.toolbox.feature.pinyin.domain.ConvertToPinyinUseCase
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinCaseMode
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinCheckpoint
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinConversion
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinError
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinMode
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinOptions
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinOutcome
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinRepository
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinSeparator
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinStatistics
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinToneStyle
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinVCharType
import com.cangshuo.toolbox.feature.pinyin.ui.PinyinUiState
import com.cangshuo.toolbox.feature.pinyin.ui.PinyinViewModel
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression cases checked against the shipped readings.tsv on 2026-10-14. Exceptional tone
 * spellings were also reviewed in python-pinyin's phonetic_symbol.py and _tone_convert.py:
 * https://github.com/mozillazg/python-pinyin/blob/master/pypinyin/phonetic_symbol.py
 * https://github.com/mozillazg/python-pinyin/blob/master/pypinyin/style/_tone_convert.py
 * Number-style neutral-tone policy is deliberately left to the main implementation task.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PinyinRegressionTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private var modelId = 0

    private val readingsFile: File by lazy {
        var directory: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        var found: File? = null
        while (directory != null && found == null) {
            val base = directory
            found = listOf(
                "src/main/assets/pinyin/readings.tsv",
                "app/src/main/assets/pinyin/readings.tsv",
                "android/app/src/main/assets/pinyin/readings.tsv",
            ).map { File(base, it) }.firstOrNull { it.isFile }
            directory = directory.parentFile
        }
        requireNotNull(found) { "Cannot locate the bundled pinyin readings from the test directory" }
    }
    private val repository = LocalPinyinRepository { FileInputStream(readingsFile) }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        try {
            models.clear()
            dispatcher.scheduler.runCurrent()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun success(
        text: String,
        options: PinyinOptions = PinyinOptions(),
        source: PinyinRepository = repository,
    ): PinyinOutcome.Success {
        val outcome = ConvertToPinyinUseCase(source, dispatcher)(text, options)
        assertTrue("Expected conversion success, got $outcome", outcome is PinyinOutcome.Success)
        return outcome as PinyinOutcome.Success
    }

    private fun model(
        source: PinyinRepository = repository,
        handle: SavedStateHandle = SavedStateHandle(),
        debounceMillis: Long = 0,
    ): PinyinViewModel = PinyinViewModel(
        ConvertToPinyinUseCase(source, dispatcher), handle, debounceMillis,
    ).also { models.put("pinyin-${modelId++}", it) }

    private fun TestScope.observe(model: PinyinViewModel): MutableList<PinyinUiState> {
        val states = mutableListOf<PinyinUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.uiState.collect { states.add(it) }
        }
        return states
    }

    private fun TestScope.finishDebounce() {
        runCurrent()
        advanceTimeBy(DEBOUNCE_MILLIS)
        runCurrent()
    }

    private fun assertNoStaleResult(states: List<PinyinUiState>) {
        assertFalse(
            "A superseded conversion was published, even transiently",
            states.any { (it.outcome as? PinyinOutcome.Success)?.text == "result-1" },
        )
    }

    @Test
    fun phraseStatisticsCountEveryCodePointInAllModes() = runTest(dispatcher) {
        val phrase = "重庆银行便宜音乐"
        val samples = listOf(
            phrase to PinyinStatistics(8, 8, 0, 0),
            "😀${phrase}𠮷A" to PinyinStatistics(11, 9, 2, 1),
        )
        PinyinMode.entries.forEach { mode ->
            samples.forEach { (input, expected) ->
                val result = success(input, PinyinOptions(mode = mode))
                assertEquals("Statistics for $mode / $input", expected, result.statistics)
            }
        }
        assertEquals("chóng qìng yín háng pián yi yīn yuè", success(phrase).text)
        assertEquals("cqyhpyyy", success(phrase, PinyinOptions(mode = PinyinMode.INITIALS)).text)
    }

    @Test
    fun filteringNonChineseRunsKeepsTheConfiguredSyllableSeparator() = runTest(dispatcher) {
        val inputs = listOf("中国ABC北京", "ABC😀中国!\r\n北京123")
        PinyinSeparator.entries.forEach { separator ->
            inputs.forEach { input ->
                val result = success(input, PinyinOptions(separator = separator, keepNonChinese = false))
                assertEquals(
                    "Filtered boundaries with $separator",
                    listOf("zhōng", "guó", "běi", "jīng").joinToString(separator.value),
                    result.text,
                )
                val total = input.codePointCount(0, input.length)
                assertEquals(PinyinStatistics(total, 4, total - 4, 0), result.statistics)
            }
        }
    }

    @Test
    fun zeroAndSupplementaryIdeographsUseTheirBundledReadingsInEveryMode() = runTest(dispatcher) {
        // U+30000 itself has no entry; use actual extension G/H entries instead of guessing.
        val cases = listOf(
            ReadingCase(0x3007, "ling2", "líng", "l"),
            ReadingCase(0x3400, "qiu1", "qiū", "q"),
            ReadingCase(0x20000, "he1", "hē", "h"),
            ReadingCase(0x2EBF4, "ni2", "ní", "n"),
            ReadingCase(0x30021, "qian1", "qiān", "q"),
            ReadingCase(0x30078, "dang4", "dàng", "d"),
            ReadingCase(0x31350, "qi2", "qí", "q"),
        )
        val input = cases.joinToString("") { it.character }
        val expectedStatistics = PinyinStatistics(cases.size, cases.size, 0, 0)
        val full = success(input, PinyinOptions(keepNonChinese = false))
        assertEquals(cases.joinToString(" ") { it.marked }, full.text)
        assertEquals(expectedStatistics, full.statistics)
        assertEquals(
            cases.joinToString(" ") { it.numbered },
            success(input, PinyinOptions(toneStyle = PinyinToneStyle.TONE_NUMBER)).text,
        )
        val initials = success(input, PinyinOptions(mode = PinyinMode.INITIALS, keepNonChinese = false))
        assertEquals(cases.joinToString("") { it.initial }, initials.text)
        assertEquals(expectedStatistics, initials.statistics)
        val query = success(input, PinyinOptions(mode = PinyinMode.POLYPHONE, keepNonChinese = false))
        assertEquals(expectedStatistics, query.statistics)
        val rows = query.text.lines()
        assertEquals(cases.size, rows.size)
        cases.zip(rows).forEach { (entry, row) ->
            assertTrue("Missing code point U+${entry.codePoint.toString(16)}", row.startsWith("${entry.character}: "))
            assertTrue(row, entry.marked in row.substringAfter(": ").split(" / "))
        }
    }

    @Test
    fun unsupportedChineseStaysVisibleAndEmojiCountsAsOneCodePoint() = runTest(dispatcher) {
        val input = "中𠮷国😀A"
        PinyinMode.entries.forEach { mode ->
            listOf(true, false).forEach { keep ->
                val result = success(input, PinyinOptions(mode = mode, keepNonChinese = keep))
                assertEquals("$mode / keep=$keep", PinyinStatistics(5, 3, 2, 1), result.statistics)
                val suffix = if (keep) "😀A" else ""
                when (mode) {
                    PinyinMode.FULL -> assertEquals("zhōng𠮷guó$suffix", result.text)
                    PinyinMode.INITIALS -> assertEquals("z𠮷g$suffix", result.text)
                    PinyinMode.POLYPHONE -> {
                        assertEquals(listOf("中", "𠮷", "国"), result.text.lines().map { it.substringBefore(": ") })
                        assertEquals("𠮷", result.text.lines()[1])
                    }
                }
            }
        }
    }

    @Test
    fun unpairedSurrogatesReturnInvalidUnicodeInEveryModeEvenWhenFiltered() = runTest(dispatcher) {
        val malformed = listOf("\uD800", "\uDC00", "中\uD800国", "中\uDC00国", "\uD800\uD800", "\uDC00\uD800", "中国\uD800")
        PinyinMode.entries.forEach { mode ->
            listOf(true, false).forEach { keep ->
                malformed.forEach { input ->
                    val result = ConvertToPinyinUseCase(repository, dispatcher)(
                        input, PinyinOptions(mode = mode, keepNonChinese = keep),
                    )
                    assertEquals("$mode / keep=$keep", PinyinOutcome.Failure(PinyinError.INVALID_UNICODE), result)
                }
            }
        }
    }

    @Test
    fun umlautFormsRespectCaseAndNeutralToneRemainsUnmarked() = runTest(dispatcher) {
        val forms = listOf(
            Triple(PinyinVCharType.UNICODE, "NÜ3-LÜ4", "NÜ-LÜ"),
            Triple(PinyinVCharType.V, "NV3-LV4", "NV-LV"),
            Triple(PinyinVCharType.U_COLON, "NU:3-LU:4", "NU:-LU:"),
        )
        forms.forEach { (vChar, numbered, bare) ->
            val options = PinyinOptions(
                caseMode = PinyinCaseMode.UPPER,
                separator = PinyinSeparator.HYPHEN,
                vCharType = vChar,
            )
            assertEquals("NǙ-LǛ", success("女绿", options).text)
            assertEquals(numbered, success("女绿", options.copy(toneStyle = PinyinToneStyle.TONE_NUMBER)).text)
            assertEquals(bare, success("女绿", options.copy(toneStyle = PinyinToneStyle.NO_TONE)).text)
        }
        val capitalized = PinyinOptions(caseMode = PinyinCaseMode.CAPITALIZE)
        assertEquals("Pián Yi Wèi Le", success("便宜为了", capitalized).text)
        assertEquals("Pian Yi Wei Le", success("便宜为了", capitalized.copy(toneStyle = PinyinToneStyle.NO_TONE)).text)
    }

    @Test
    fun exceptionalVowelsAndSyllabicNasalsKeepTheirToneMarks() = runTest(dispatcher) {
        // These exact forms come from the upstream symbol table, including combining marks.
        val expected = linkedMapOf(
            "㕶" to listOf("ň", "ňg"),
            "呣" to listOf("ḿ", "m\u0300", "móu"),
            "嗯" to listOf("ń", "ńg", "ňg", "ǹg", "ň", "ǹ"),
            "欸" to listOf("āi", "ǎi", "ê\u0304", "ế", "ê\u030c", "ề", "xiè", "éi", "ěi", "èi", "ēi"),
        )
        val result = success(expected.keys.joinToString(""), PinyinOptions(mode = PinyinMode.POLYPHONE))
        assertEquals(PinyinStatistics(4, 4, 0, 0), result.statistics)
        assertEquals(expected.size, result.text.lines().size)
        result.text.lines().forEach { row ->
            val character = row.substringBefore(": ")
            val readings = row.substringAfter(": ").split(" / ")
            assertEquals("Exceptional readings for $character", expected.getValue(character).toSet(), readings.toSet())
            assertEquals("Duplicate readings for $character", readings.size, readings.distinct().size)
        }
    }

    @Test
    fun cancellationDuringFirstTableLoadClosesTheStreamAndRetryLoadsACompleteTable() = runTest(dispatcher) {
        var opened = 0
        var closed = 0
        val coldRepository = LocalPinyinRepository {
            opened++
            object : FileInputStream(readingsFile) {
                private var countedClose = false
                override fun close() {
                    // FileInputStream may re-enter close while closing its channel. Count
                    // closed streams, rather than how often the idempotent method is called.
                    if (!countedClose) {
                        countedClose = true
                        closed++
                    }
                    super.close()
                }
            }
        }
        val cancelled = CancellationException("Stop the first table load")
        var checks = 0
        val thrown = runCatching {
            coldRepository.convert("中国", PinyinOptions(), PinyinCheckpoint {
                if (++checks == 2) throw cancelled
            })
        }.exceptionOrNull()
        assertSame(cancelled, thrown)
        assertEquals(1, opened)
        assertEquals(1, closed)
        // This late table entry cannot come from the partially parsed first attempt.
        assertEquals("qí", success(String(Character.toChars(0x31350)), source = coldRepository).text)
        assertEquals(2, opened)
        assertEquals(2, closed)
        assertEquals("zhōng guó", success("中国", source = coldRepository).text)
        assertEquals("A completed table should be reused", 2, opened)
    }

    @Test
    fun editedOptionsSurviveCopyingAllKeysIntoANewSavedStateHandle() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val original = model(handle = handle)
        original.updateInput("女绿ABC")
        original.selectMode(PinyinMode.POLYPHONE)
        original.selectToneStyle(PinyinToneStyle.TONE_NUMBER)
        original.selectCaseMode(PinyinCaseMode.UPPER)
        original.selectSeparator(PinyinSeparator.HYPHEN)
        original.selectVCharType(PinyinVCharType.U_COLON)
        original.toggleKeepNonChinese(false)
        val expected = PinyinOptions(
            PinyinMode.POLYPHONE, PinyinToneStyle.TONE_NUMBER, PinyinCaseMode.UPPER,
            PinyinSeparator.HYPHEN, PinyinVCharType.U_COLON, false,
        )
        val copiedKeys = handle.keys().associateWith { key -> handle.get<Any?>(key) }
        assertEquals(
            setOf("input", "mode", "tone_style", "case_mode", "separator", "v_char", "keep_non_chinese"),
            copiedKeys.keys,
        )
        models.clear()
        val restoredHandle = SavedStateHandle(copiedKeys)
        val restored = model(handle = restoredHandle)
        assertEquals("女绿ABC", restored.uiState.value.input)
        assertEquals(expected, restored.uiState.value.options)
        advanceUntilIdle()
        assertTrue(restored.uiState.value.outcome is PinyinOutcome.Success)
        restored.selectMode(PinyinMode.FULL)
        advanceUntilIdle()
        assertEquals("NU:3-LU:4", (restored.uiState.value.outcome as PinyinOutcome.Success).text)
        assertEquals("FULL", restoredHandle.get<String>("mode"))
    }

    @Test
    fun unknownSavedEnumNamesFallBackWithoutLosingInputOrTheKeepSwitch() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf(
            "input" to "女ABC绿",
            "mode" to "removed_mode",
            "tone_style" to "removed_tone",
            "case_mode" to "removed_case",
            "separator" to "removed_separator",
            "v_char" to "removed_v_char",
            "keep_non_chinese" to false,
        ))
        val restored = model(handle = handle)
        assertEquals(PinyinOptions(keepNonChinese = false), restored.uiState.value.options)
        advanceUntilIdle()
        assertEquals("女ABC绿", restored.uiState.value.input)
        assertEquals("nǚ lǜ", (restored.uiState.value.outcome as PinyinOutcome.Success).text)
        assertFalse(restored.uiState.value.processing)
    }

    @Test
    fun inputEditDuringConversionRejectsTheReturnedOldResultAndKeepsNewWorkProcessing() = runTest(dispatcher) {
        val fake = PassiveRepository()
        val model = model(fake, debounceMillis = DEBOUNCE_MILLIS)
        val states = observe(model)
        fake.beforeReturn = { model.updateInput("北京") }
        model.updateInput("中国")
        finishDebounce()
        assertEquals(listOf("result-1"), fake.returned)
        assertEquals("北京", model.uiState.value.input)
        assertTrue("Old job cleanup must not clear the new job's loading state", model.uiState.value.processing)
        assertNull(model.uiState.value.outcome)
        advanceUntilIdle()
        assertEquals(listOf("中国", "北京"), fake.requests.map { it.first })
        assertEquals("result-2", (model.uiState.value.outcome as PinyinOutcome.Success).text)
        assertFalse(model.uiState.value.processing)
        assertNoStaleResult(states)
    }

    @Test
    fun optionEditDuringConversionKeepsTheLatestSnapshotAndDropsOldOutput() = runTest(dispatcher) {
        val fake = PassiveRepository()
        val model = model(fake, debounceMillis = DEBOUNCE_MILLIS)
        val states = observe(model)
        fake.beforeReturn = {
            model.selectCaseMode(PinyinCaseMode.UPPER)
            model.selectSeparator(PinyinSeparator.HYPHEN)
        }
        model.updateInput("中国")
        finishDebounce()
        assertEquals(listOf("result-1"), fake.returned)
        assertTrue(model.uiState.value.processing)
        assertNull(model.uiState.value.outcome)
        advanceUntilIdle()
        val expected = PinyinOptions(caseMode = PinyinCaseMode.UPPER, separator = PinyinSeparator.HYPHEN)
        assertEquals(listOf("中国" to PinyinOptions(), "中国" to expected), fake.requests)
        assertEquals(expected, model.uiState.value.options)
        assertEquals("result-2", (model.uiState.value.outcome as PinyinOutcome.Success).text)
        assertNoStaleResult(states)
    }

    @Test
    fun cancelAndRetryDuringConversionKeepTheRetryRevisionAndLoadingState() = runTest(dispatcher) {
        val fake = PassiveRepository()
        val model = model(fake, debounceMillis = DEBOUNCE_MILLIS)
        val states = observe(model)
        fake.beforeReturn = {
            model.cancel()
            assertTrue(model.uiState.value.cancelled)
            assertFalse(model.uiState.value.processing)
            model.retry()
        }
        model.updateInput("中国")
        finishDebounce()
        assertEquals(listOf("result-1"), fake.returned)
        assertTrue(model.uiState.value.processing)
        assertFalse(model.uiState.value.cancelled)
        assertNull(model.uiState.value.outcome)
        advanceUntilIdle()
        assertEquals(2, fake.requests.size)
        assertEquals("result-2", (model.uiState.value.outcome as PinyinOutcome.Success).text)
        assertFalse(model.uiState.value.processing)
        assertNoStaleResult(states)
    }

    @Test
    fun clearDuringConversionDoesNotResurrectTheReturnedOldOutput() = runTest(dispatcher) {
        val fake = PassiveRepository()
        val handle = SavedStateHandle()
        val model = model(fake, handle, DEBOUNCE_MILLIS)
        val states = observe(model)
        fake.beforeReturn = { model.clear() }
        model.updateInput("中国")
        finishDebounce()
        advanceUntilIdle()
        assertEquals(listOf("result-1"), fake.returned)
        assertEquals(1, fake.requests.size)
        assertEquals("", model.uiState.value.input)
        assertEquals("", handle.get<String>("input"))
        assertFalse(model.uiState.value.processing)
        assertFalse(model.uiState.value.cancelled)
        assertNull(model.uiState.value.outcome)
        assertNoStaleResult(states)
    }

    @Test
    fun rapidEditsDebounceToOneSnapshotAndClearingTheStoreCancelsPendingWork() = runTest(dispatcher) {
        val fake = PassiveRepository()
        val model = model(fake, debounceMillis = DEBOUNCE_MILLIS)
        model.updateInput("中国")
        runCurrent()
        advanceTimeBy(DEBOUNCE_MILLIS - 1)
        model.updateInput("北京")
        model.selectToneStyle(PinyinToneStyle.NO_TONE)
        model.selectCaseMode(PinyinCaseMode.UPPER)
        runCurrent()
        advanceTimeBy(DEBOUNCE_MILLIS - 1)
        runCurrent()
        assertTrue("Superseded debounce must never enter the repository", fake.requests.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        val expected = PinyinOptions(toneStyle = PinyinToneStyle.NO_TONE, caseMode = PinyinCaseMode.UPPER)
        assertEquals(listOf("北京" to expected), fake.requests)
        assertFalse(model.uiState.value.processing)
        model.updateInput("上海")
        runCurrent()
        models.clear()
        advanceUntilIdle()
        assertEquals("Cleared model must not execute pending work", listOf("北京" to expected), fake.requests)
    }

    private data class ReadingCase(
        val codePoint: Int,
        val numbered: String,
        val marked: String,
        val initial: String,
    ) {
        val character: String get() = String(Character.toChars(codePoint))
    }

    /** Returns obsolete data without invoking the checkpoint or cooperating with cancellation. */
    private class PassiveRepository : PinyinRepository {
        val requests = mutableListOf<Pair<String, PinyinOptions>>()
        val returned = mutableListOf<String>()
        var beforeReturn: (() -> Unit)? = null

        override fun convert(text: String, options: PinyinOptions, checkpoint: PinyinCheckpoint): PinyinConversion {
            requests.add(text to options)
            val result = PinyinConversion("result-${requests.size}", PinyinStatistics())
            // The test injects a synchronous UI event at this boundary; no threads or sleeps.
            val event = beforeReturn
            beforeReturn = null
            event?.invoke()
            returned.add(result.text)
            return result
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 50L
    }
}
