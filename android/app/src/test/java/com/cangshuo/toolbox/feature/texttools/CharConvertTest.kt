package com.cangshuo.toolbox.feature.texttools

import com.cangshuo.toolbox.feature.charconvert.data.LocalCharConvertRepository
import com.cangshuo.toolbox.feature.charconvert.domain.CaseMode
import com.cangshuo.toolbox.feature.charconvert.domain.CharConvertOptions
import com.cangshuo.toolbox.feature.charconvert.domain.TransformCharsUseCase
import com.cangshuo.toolbox.feature.charconvert.domain.WidthMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CharConvertTest {
    private val transform = TransformCharsUseCase(LocalCharConvertRepository())
    private val plain = CharConvertOptions(
        width = WidthMode.NONE,
        collapseWhitespace = false,
        removeZeroWidth = false,
        unifyNewlines = false,
    )

    @Test
    fun halfAndFullWidthRoundTrip() {
        assertEquals("ABC123!?", transform("ＡＢＣ１２３！？", plain.copy(width = WidthMode.TO_HALF)))
        assertEquals("a b", transform("ａ\u3000ｂ", plain.copy(width = WidthMode.TO_HALF)))
        assertEquals("ＡＢＣ１２３！？", transform("ABC123!?", plain.copy(width = WidthMode.TO_FULL)))
        assertEquals("ｋｅｅｐ 中文 😀", transform("keep 中文 😀", plain.copy(width = WidthMode.TO_FULL)))
        assertEquals("already half", transform("already half", plain.copy(width = WidthMode.TO_HALF)))
    }

    @Test
    fun caseConversionOnlyTouchesLetters() {
        assertEquals("ABC 123 中文", transform("abc 123 中文", plain.copy(case = CaseMode.UPPER)))
        assertEquals("abc", transform("ABC", plain.copy(case = CaseMode.LOWER)))
    }

    @Test
    fun whitespaceCollapseTrimsEveryLine() {
        assertEquals(
            "a b\nc",
            transform("  a\t\tb  \n\u3000 c ", plain.copy(collapseWhitespace = true)),
        )
    }

    @Test
    fun zeroWidthAndBomAreRemoved() {
        assertEquals("ab", transform("a\u200Bb\uFEFF", plain.copy(removeZeroWidth = true)))
    }

    @Test
    fun newlinesAreUnified() {
        assertEquals("a\nb\nc", transform("a\r\nb\rc", plain.copy(unifyNewlines = true)))
    }
}
