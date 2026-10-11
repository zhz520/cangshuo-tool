package com.cangshuo.toolbox.feature.morse

import com.cangshuo.toolbox.feature.morse.data.LocalMorseRepository
import com.cangshuo.toolbox.feature.morse.domain.MorseDirection
import com.cangshuo.toolbox.feature.morse.domain.MorseError
import com.cangshuo.toolbox.feature.morse.domain.MorseResult
import com.cangshuo.toolbox.feature.morse.domain.MorseTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MorseCodingTest {

    private val repo = LocalMorseRepository()

    @Test
    fun encodesLettersDigitsAndPunctuation() {
        assertEquals(".... .. / --", (repo.encode("HI M") as MorseResult.Success).output)
        assertEquals("... --- ...", (repo.encode("SOS") as MorseResult.Success).output)
        assertEquals("....- ..---", (repo.encode("42") as MorseResult.Success).output)
        assertEquals(".-.-.- --..--", (repo.encode(".,") as MorseResult.Success).output)
        assertEquals(".- -...", (repo.encode("ab") as MorseResult.Success).output)
    }

    @Test
    fun unsupportedCharactersAreReportedNotReplaced() {
        val result = repo.encode("A😀B") as MorseResult.Success
        assertEquals(".- -...", result.output)
        assertEquals(listOf("😀"), result.unsupported)
        assertTrue(repo.encode("😀😀") is MorseResult.Success)
        assertEquals("", (repo.encode("😀😀") as MorseResult.Success).output)
    }

    @Test
    fun decodesWordsBackToText() {
        assertEquals("SOS", (repo.decode("... --- ...") as MorseResult.Success).output)
        assertEquals("HI M", (repo.decode(".... .. / --") as MorseResult.Success).output)
        assertEquals("42", (repo.decode("....- ..---") as MorseResult.Success).output)
        // Extra whitespace and multiple slashes are tolerated.
        assertEquals("HI", (repo.decode("  ....   ..  ") as MorseResult.Success).output)
        assertEquals("HI M", (repo.decode(".... .. // --") as MorseResult.Success).output)
    }

    @Test
    fun invalidSymbolReportsOneBasedIndex() {
        val error = repo.decode("... ..x ---") as MorseResult.Error
        assertEquals(MorseError.INVALID_SYMBOL, error.reason)
        assertEquals(2, error.position)
    }

    @Test
    fun emptyInputsAreRejected() {
        assertEquals(MorseError.EMPTY, (repo.encode("") as MorseResult.Error).reason)
        assertEquals(MorseError.EMPTY, (repo.encode("  ") as MorseResult.Error).reason)
        assertEquals(MorseError.EMPTY, (repo.decode("") as MorseResult.Error).reason)
    }

    @Test
    fun timelineBuildsStandardGaps() {
        val tones = repo.timeline("EI", MorseDirection.ENCODE)!!
        assertEquals(
            listOf(
                MorseTone.Sound(1),
                MorseTone.Gap(3),
                MorseTone.Sound(1), MorseTone.Gap(1), MorseTone.Sound(1),
            ),
            tones,
        )
    }

    @Test
    fun timelineWordsUseSevenUnitGap() {
        val tones = repo.timeline("E E", MorseDirection.ENCODE)!!
        assertEquals(
            listOf(MorseTone.Sound(1), MorseTone.Gap(7), MorseTone.Sound(1)),
            tones,
        )
    }

    @Test
    fun timelineFromDecodeDirectionAndInvalidSymbols() {
        val tones = repo.timeline(".- -...", MorseDirection.DECODE)!!
        assertEquals(
            listOf(
                MorseTone.Sound(1), MorseTone.Gap(1), MorseTone.Sound(3),
                MorseTone.Gap(3),
                MorseTone.Sound(3), MorseTone.Gap(1), MorseTone.Sound(1), MorseTone.Gap(1),
                MorseTone.Sound(1), MorseTone.Gap(1), MorseTone.Sound(1),
            ),
            tones,
        )
        assertNull(repo.timeline("..x", MorseDirection.DECODE))
        assertNull(repo.timeline("", MorseDirection.ENCODE))
    }
}
