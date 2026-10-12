package com.cangshuo.toolbox.feature.charconvert.data

import com.cangshuo.toolbox.feature.charconvert.domain.CaseMode
import com.cangshuo.toolbox.feature.charconvert.domain.CharConvertOptions
import com.cangshuo.toolbox.feature.charconvert.domain.CharConvertRepository
import com.cangshuo.toolbox.feature.charconvert.domain.WidthMode
import java.util.Locale

/**
 * Local text clean-up. Half/full-width conversion shifts the Unicode Halfwidth and Fullwidth
 * Forms block (FF01-FF5E ↔ 21-7E, U+3000 ↔ space) the same way the MIT-licensed niwaringo/moji
 * library does with its range converter; case conversion, whitespace collapsing, zero-width
 * removal and newline unification are independent helpers.
 */
class LocalCharConvertRepository : CharConvertRepository {

    override fun transform(text: String, options: CharConvertOptions): String {
        var value = text
        if (options.removeZeroWidth) value = value.filterNot { it.code in ZERO_WIDTH }
        if (options.unifyNewlines) value = value.replace("\r\n", "\n").replace('\r', '\n')
        value = when (options.width) {
            WidthMode.TO_HALF -> convertWidth(value, toFull = false)
            WidthMode.TO_FULL -> convertWidth(value, toFull = true)
            WidthMode.NONE -> value
        }
        value = when (options.case) {
            CaseMode.UPPER -> value.uppercase(Locale.ROOT)
            CaseMode.LOWER -> value.lowercase(Locale.ROOT)
            CaseMode.NONE -> value
        }
        if (options.collapseWhitespace) value = collapse(value)
        return value
    }

    private fun convertWidth(text: String, toFull: Boolean): String = buildString(text.length) {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            index += Character.charCount(codePoint)
            when {
                !toFull && codePoint in 0xFF01..0xFF5E -> appendCodePoint(codePoint - 0xFEE0)
                !toFull && codePoint == 0x3000 -> append(' ')
                toFull && codePoint in 0x21..0x7E -> appendCodePoint(codePoint + 0xFEE0)
                else -> appendCodePoint(codePoint)
            }
        }
    }

    private fun collapse(text: String): String = text.split('\n').joinToString("\n") { line ->
        line.replace(Regex("[ \t\u3000\u00A0]+"), " ").trim(' ')
    }

    private companion object {
        val ZERO_WIDTH = setOf(0x200B, 0x200C, 0x200D, 0x2060, 0xFEFF)
    }
}
