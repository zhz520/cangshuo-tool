package com.cangshuo.toolbox.feature.texttools

import com.cangshuo.toolbox.feature.chineseconvert.data.LocalChineseConversionRepository
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionCheckpoint
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionError
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionLimits
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionMode
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionOutcome
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionRepository
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionStatistics
import com.cangshuo.toolbox.feature.chineseconvert.domain.ConvertChineseUseCase
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Actual bundled tables checked against independent OpenCC 1.1.9 CLI results. */
class ChineseConversionRepositoryTest {
    private val checkpoint = ChineseConversionCheckpoint { }
    private val assets: File by lazy {
        generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
            .flatMap { base -> sequenceOf("src/main/assets/opencc", "app/src/main/assets/opencc", "android/app/src/main/assets/opencc").map { File(base, it) } }
            .first { File(it, "manifest.json").isFile }
    }
    private fun repository() = LocalChineseConversionRepository { name -> FileInputStream(File(assets, "$name.tsv")) }

    private fun compareOracle(mode: ChineseConversionMode) {
        val engine = repository()
        val corpus = requireNotNull(javaClass.getResourceAsStream("/chineseconvert/opencc-1.1.9.tsv"))
        var compared = 0
        corpus.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filterNot { it.startsWith("#") }.forEachIndexed { index, line ->
                val fields = line.split('\t')
                require(fields.size == 3) { "Invalid oracle row $index" }
                if (fields[0] == mode.name) {
                    val input = String(Base64.getDecoder().decode(fields[1]), Charsets.UTF_8)
                    val expected = String(Base64.getDecoder().decode(fields[2]), Charsets.UTF_8)
                    val result = engine.convert(input, mode, checkpoint)
                    assertEquals("$mode oracle row $index", expected, result.text)
                    assertEquals(input.codePointCount(0, input.length), result.statistics.inputCharacters)
                    assertEquals(expected.codePointCount(0, expected.length), result.statistics.outputCharacters)
                    compared++
                }
            }
        }
        assertTrue("Must exercise real dictionary and Unicode cases", compared >= 100)
    }

    @Test fun simplifiedToTraditionalMatchesUpstream() = compareOracle(ChineseConversionMode.S2T)
    @Test fun traditionalToSimplifiedMatchesUpstream() = compareOracle(ChineseConversionMode.T2S)
    @Test fun simplifiedToTaiwanMatchesUpstream() = compareOracle(ChineseConversionMode.S2TW)
    @Test fun taiwanToSimplifiedMatchesUpstream() = compareOracle(ChineseConversionMode.TW2S)
    @Test fun simplifiedToTaiwanVocabularyMatchesUpstream() = compareOracle(ChineseConversionMode.S2TWP)
    @Test fun taiwanVocabularyToSimplifiedMatchesUpstream() = compareOracle(ChineseConversionMode.TW2SP)
    @Test fun simplifiedToHongKongMatchesUpstream() = compareOracle(ChineseConversionMode.S2HK)
    @Test fun hongKongToSimplifiedMatchesUpstream() = compareOracle(ChineseConversionMode.HK2S)

    @Test fun unchangedUnicodeWhitespaceAndNulArePreserved() {
        val text = " A😀𠮷\u0000B\r\n\t \u0301"
        ChineseConversionMode.entries.forEach { mode ->
            val result = repository().convert(text, mode, checkpoint)
            assertEquals(text, result.text)
            assertEquals(0, result.statistics.changedSegments)
            assertEquals(text.codePointCount(0, text.length), result.statistics.outputCharacters)
        }
    }

    @Test fun emptyInputDoesNotLoadTables() = runTest {
        val engine = LocalChineseConversionRepository { error("Empty input must not open dictionaries") }
        val result = ConvertChineseUseCase(engine, StandardTestDispatcher(testScheduler))("", ChineseConversionMode.S2T)
        assertEquals(ChineseConversionOutcome.Success("", ChineseConversionStatistics(0, 0, 0)), result)
    }

    @Test fun malformedUnicodeIsRejectedBeforeDictionaryLoading() = runTest {
        val useCase = ConvertChineseUseCase(LocalChineseConversionRepository { error("Must validate Unicode first") }, StandardTestDispatcher(testScheduler))
        for (text in listOf("\uD800", "x\uDC00", "\uD800x")) {
            assertEquals(ChineseConversionOutcome.Failure(ChineseConversionError.INVALID_UNICODE), useCase(text, ChineseConversionMode.S2T))
        }
    }

    @Test fun oversizedInputIsRejectedBeforeLoading() = runTest {
        val useCase = ConvertChineseUseCase(LocalChineseConversionRepository { error("Must check size first") }, StandardTestDispatcher(testScheduler))
        assertEquals(ChineseConversionOutcome.Failure(ChineseConversionError.INPUT_TOO_LONG),
            useCase("字".repeat(ChineseConversionLimits.MAX_INPUT + 1), ChineseConversionMode.S2T))
    }

    @Test fun exactInputLimitIsAcceptedWithoutTruncation() {
        val text = "A😀".repeat(33_333) + "Z"
        assertEquals(ChineseConversionLimits.MAX_INPUT, text.length)
        val result = repository().convert(text, ChineseConversionMode.S2T, checkpoint)
        assertEquals(text, result.text)
        assertEquals(66_667, result.statistics.outputCharacters)
    }

    @Test fun failedDictionaryIsNotCachedAndCanBeRetried() = runTest {
        var damaged = true
        val engine = LocalChineseConversionRepository { name ->
            val bytes = File(assets, "$name.tsv").readBytes()
            if (damaged && name == "STPhrases") bytes[0] = 'X'.code.toByte()
            ByteArrayInputStream(bytes)
        }
        val useCase = ConvertChineseUseCase(engine, StandardTestDispatcher(testScheduler))
        assertEquals(ChineseConversionOutcome.Failure(ChineseConversionError.FAILED), useCase("头发发展", ChineseConversionMode.S2T))
        damaged = false
        assertEquals("頭髮發展", (useCase("头发发展", ChineseConversionMode.S2T) as ChineseConversionOutcome.Success).text)
    }

    @Test fun cancellationDuringLoadDoesNotPublishPartialTable() {
        val engine = repository()
        var checks = 0
        assertThrows(CancellationException::class.java) {
            engine.convert("头发发展", ChineseConversionMode.S2T, ChineseConversionCheckpoint {
                if (++checks >= 4) throw CancellationException("test")
            })
        }
        assertEquals("頭髮發展", engine.convert("头发发展", ChineseConversionMode.S2T, checkpoint).text)
    }

    @Test fun cancellationDuringConversionRemainsCancellation() = runTest {
        val useCase = ConvertChineseUseCase(ChineseConversionRepository { _, _, _ ->
            throw CancellationException("test")
        }, StandardTestDispatcher(testScheduler))
        var cancelled = false
        try { useCase("原文", ChineseConversionMode.S2T) } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }

    @Test fun conversionDeadlineHasExplicitFailureInsteadOfPartialResult() = runTest {
        var now = 0L
        val engine = repository()
        val timed = ChineseConversionRepository { text, mode, supplied ->
            var checks = 0
            engine.convert(text, mode, ChineseConversionCheckpoint {
                if (++checks == 3) now = ChineseConversionLimits.BUDGET_NANOS + 1
                supplied.check()
            })
        }
        val result = ConvertChineseUseCase(timed, StandardTestDispatcher(testScheduler), nanoTime = { now })("头发发展", ChineseConversionMode.S2T)
        assertEquals(ChineseConversionOutcome.Failure(ChineseConversionError.TIME_LIMIT), result)
    }

    @Test fun changedSegmentsUsesOriginalSegmentationRatherThanCharacterCount() {
        val result = repository().convert("头发", ChineseConversionMode.S2T, checkpoint)
        assertEquals("頭髮", result.text)
        assertEquals(1, result.statistics.changedSegments)
        assertFalse(result.text == "头发")
    }
}
