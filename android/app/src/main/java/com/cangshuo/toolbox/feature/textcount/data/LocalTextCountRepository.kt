package com.cangshuo.toolbox.feature.textcount.data

import com.cangshuo.toolbox.feature.textcount.domain.TextCountRepository
import com.cangshuo.toolbox.feature.textcount.domain.TextStats
import kotlin.math.roundToInt

/**
 * Code-point aware statistics. CJK ideographs count per character (including the Ext-A,
 * compatibility and Ext-B planes) while letters, digits and inner apostrophes/hyphens count
 * as one word — the same mixed convention as the MIT-licensed lepture/word-count reference.
 * Whitespace never counts towards the "without spaces" total; lines follow Java split
 * semantics so a trailing newline does not create an extra empty line.
 */
class LocalTextCountRepository : TextCountRepository {

    override fun analyze(text: String): TextStats {
        if (text.isEmpty()) {
            return TextStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        }
        var withSpaces = 0
        var withoutSpaces = 0
        var cjk = 0
        var latinWords = 0
        var inWord = false
        var lineHasContent = false
        var lines = 0
        var nonEmptyLines = 0
        var paragraphs = 0
        var inParagraph = false
        var endedWithNewline = false

        fun finishLine() {
            lines += 1
            if (lineHasContent) {
                nonEmptyLines += 1
                if (!inParagraph) {
                    paragraphs += 1
                    inParagraph = true
                }
            } else {
                inParagraph = false
            }
            lineHasContent = false
        }

        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            index += Character.charCount(codePoint)
            endedWithNewline = false
            withSpaces += 1
            val whitespace = Character.isWhitespace(codePoint)
            if (!whitespace) {
                withoutSpaces += 1
                lineHasContent = true
            }
            when {
                codePoint == '\n'.code -> {
                    if (inWord) { latinWords += 1; inWord = false }
                    finishLine()
                    endedWithNewline = true
                    continue
                }

                isCjk(codePoint) -> {
                    if (inWord) { latinWords += 1; inWord = false }
                    cjk += 1
                }

                isWordCodePoint(codePoint) -> inWord = true
                inWord && isInnerWordMark(codePoint) -> Unit
                else -> if (inWord) { latinWords += 1; inWord = false }
            }
        }
        if (inWord) latinWords += 1
        if (!endedWithNewline) finishLine()

        val units = cjk + latinWords
        val minutes = cjk / 300.0 + latinWords / 200.0
        var readingSeconds = (minutes * 60).roundToInt()
        if (readingSeconds == 0 && units > 0) readingSeconds = 1
        return TextStats(
            charactersWithSpaces = withSpaces,
            charactersWithoutSpaces = withoutSpaces,
            cjkCharacters = cjk,
            latinWords = latinWords,
            wordCount = units,
            lines = lines,
            nonEmptyLines = nonEmptyLines,
            paragraphs = paragraphs,
            utf8Bytes = text.toByteArray(Charsets.UTF_8).size,
            readingSeconds = readingSeconds,
        )
    }

    private fun isCjk(codePoint: Int): Boolean =
        codePoint in 0x4E00..0x9FFF ||
            codePoint in 0x3400..0x4DBF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0x20000..0x2FA1F

    private fun isWordCodePoint(codePoint: Int): Boolean = Character.isLetterOrDigit(codePoint)

    private fun isInnerWordMark(codePoint: Int): Boolean =
        codePoint == '\''.code || codePoint == '’'.code || codePoint == '-'.code || codePoint == '_'.code
}
