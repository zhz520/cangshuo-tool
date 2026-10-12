package com.cangshuo.toolbox.feature.textcount

import com.cangshuo.toolbox.feature.textcount.data.LocalTextCountRepository
import com.cangshuo.toolbox.feature.textcount.domain.AnalyzeTextUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

class TextCountTest {
    private val analyze = AnalyzeTextUseCase(LocalTextCountRepository())

    @Test
    fun mixedTextCountsCjkPerCharacterAndLatinPerWord() {
        val stats = analyze("hello means 你好")
        assertEquals(14, stats.charactersWithSpaces)
        assertEquals(12, stats.charactersWithoutSpaces)
        assertEquals(2, stats.cjkCharacters)
        assertEquals(2, stats.latinWords)
        assertEquals(4, stats.wordCount)
        assertEquals(1, stats.lines)
        assertEquals(1, stats.nonEmptyLines)
        assertEquals(1, stats.paragraphs)
    }

    @Test
    fun surrogatePairsCountAsOneCharacter() {
        val extensionB = analyze("\uD840\uDC00")
        assertEquals(1, extensionB.charactersWithSpaces)
        assertEquals(1, extensionB.cjkCharacters)
        assertEquals(4, extensionB.utf8Bytes)
        val emoji = analyze("😀")
        assertEquals(1, emoji.charactersWithSpaces)
        assertEquals(0, emoji.wordCount)
        assertEquals(4, emoji.utf8Bytes)
    }

    @Test
    fun lineAndParagraphSemanticsMatchJavaSplit() {
        val multiline = analyze("a\nb\n\nc")
        assertEquals(4, multiline.lines)
        assertEquals(3, multiline.nonEmptyLines)
        assertEquals(2, multiline.paragraphs)
        val trailing = analyze("a\n")
        assertEquals(1, trailing.lines)
        assertEquals(1, trailing.nonEmptyLines)
        val blankOnly = analyze("   \n  ")
        assertEquals(2, blankOnly.lines)
        assertEquals(0, blankOnly.nonEmptyLines)
        assertEquals(0, blankOnly.paragraphs)
    }

    @Test
    fun wordsHandleHyphensApostrophesAndDigits() {
        val stats = analyze("state-of-the-art it's 2026")
        assertEquals(3, stats.latinWords)
        val trailingMark = analyze("hello- world")
        assertEquals(2, trailingMark.latinWords)
    }

    @Test
    fun readingTimeUsesMixedRatesWithAOneSecondFloor() {
        assertEquals(60, analyze("字".repeat(300)).readingSeconds)
        val words = (1..200).joinToString(" ") { "word" }
        assertEquals(60, analyze(words).readingSeconds)
        assertEquals(1, analyze("hi").readingSeconds)
        assertEquals(0, analyze("   ").readingSeconds)
    }

    @Test
    fun emptyTextYieldsZeros() {
        val stats = analyze("")
        assertEquals(0, stats.charactersWithSpaces)
        assertEquals(0, stats.lines)
        assertEquals(0, stats.utf8Bytes)
    }
}
