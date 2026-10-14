package com.cangshuo.toolbox.feature.pinyin.data

import com.cangshuo.toolbox.feature.pinyin.domain.PinyinCaseMode
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinCheckpoint
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinConversion
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinError
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinLimits
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinMode
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinOptions
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinProcessingException
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinRepository
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinStatistics
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinToneStyle
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinVCharType
import java.io.InputStream

/**
 * Offline conversion over the MIT-licensed mozillazg/pinyin-data table (reviewed at commit
 * 9193766130af24d2ac54230be979b2e98ac66223, Unicode 17.0.0). The bundled asset keeps one line
 * per code point - `HEX<TAB>tone-number readings` with `v` standing for `ü` - and covers 44,435
 * code points. A curated phrase table resolves the most frequent polyphone words before the
 * per-character table is used.
 *
 * The conversion runs on the caller's dispatcher with no hidden threads: it reports progress
 * through [PinyinCheckpoint] so the view model can cancel superseded work, and it stops with a
 * fixed error instead of returning a truncated result when a documented budget is exceeded.
 */
class LocalPinyinRepository(
    private val openReadings: () -> InputStream,
) : PinyinRepository {

    private var readings: Map<Int, List<String>>? = null

    override fun convert(
        text: String,
        options: PinyinOptions,
        checkpoint: PinyinCheckpoint,
    ): PinyinConversion {
        if (text.length > PinyinLimits.MAX_INPUT_LENGTH) {
            throw PinyinProcessingException(PinyinError.INPUT_TOO_LONG)
        }
        if (text.isEmpty()) return PinyinConversion("", PinyinStatistics())
        checkpoint.check()
        if (readings == null) {
            readings = loadReadings(openReadings(), checkpoint)
        }
        checkpoint.check()
        val counts = Counts()
        val output = when (options.mode) {
            PinyinMode.FULL -> renderFull(text, options, counts, checkpoint)
            PinyinMode.INITIALS -> renderInitials(text, options, counts, checkpoint)
            PinyinMode.POLYPHONE -> renderPolyphone(text, options, counts, checkpoint)
        }
        checkpoint.check()
        return PinyinConversion(output, counts.toStatistics())
    }

    private fun renderFull(
        text: String,
        options: PinyinOptions,
        counts: Counts,
        checkpoint: PinyinCheckpoint,
    ): String {
        val output = StringBuilder(minOf(text.length * 4, OUTPUT_CAPACITY))
        val separator = options.separator.value
        var previousWasSyllable = false
        var index = 0
        var steps = 0
        while (index < text.length) {
            if (++steps and CHECK_INTERVAL_MASK == 0) checkpoint.check()
            val codePoint = text.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            if (isChinese(codePoint)) {
                counts.chinese++
                val phrase = matchPhrase(text, index)
                if (phrase != null) {
                    counts.chinese += text.codePointCount(index, index + phrase.length) - 1
                    for (syllable in phrase.readings) {
                        appendSyllable(output, formatSyllable(syllable, options), separator, previousWasSyllable)
                        previousWasSyllable = true
                    }
                    index += phrase.length
                    continue
                }
                val pronunciation = readings?.get(codePoint)?.firstOrNull()
                if (pronunciation == null) {
                    counts.unsupported++
                    append(output, text.substring(index, index + charCount))
                    previousWasSyllable = false
                } else {
                    appendSyllable(output, formatSyllable(pronunciation, options), separator, previousWasSyllable)
                    previousWasSyllable = true
                }
            } else {
                counts.nonChinese++
                if (options.keepNonChinese) {
                    append(output, text.substring(index, index + charCount))
                    previousWasSyllable = false
                }
            }
            index += charCount
        }
        return output.toString()
    }

    private fun renderInitials(
        text: String,
        options: PinyinOptions,
        counts: Counts,
        checkpoint: PinyinCheckpoint,
    ): String {
        val output = StringBuilder(minOf(text.length, OUTPUT_CAPACITY))
        var firstEmitted = false
        var index = 0
        var steps = 0
        fun emitInitial(syllable: String) {
            append(output, caseInitial(initialsOf(syllable), options.caseMode, !firstEmitted))
            firstEmitted = true
        }
        while (index < text.length) {
            if (++steps and CHECK_INTERVAL_MASK == 0) checkpoint.check()
            val codePoint = text.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            if (isChinese(codePoint)) {
                counts.chinese++
                val phrase = matchPhrase(text, index)
                if (phrase != null) {
                    counts.chinese += text.codePointCount(index, index + phrase.length) - 1
                    for (syllable in phrase.readings) emitInitial(syllable)
                    index += phrase.length
                    continue
                }
                val pronunciation = readings?.get(codePoint)?.firstOrNull()
                if (pronunciation == null) {
                    counts.unsupported++
                    append(output, text.substring(index, index + charCount))
                } else {
                    emitInitial(pronunciation)
                }
            } else {
                counts.nonChinese++
                if (options.keepNonChinese) append(output, text.substring(index, index + charCount))
            }
            index += charCount
        }
        return output.toString()
    }

    private fun renderPolyphone(
        text: String,
        options: PinyinOptions,
        counts: Counts,
        checkpoint: PinyinCheckpoint,
    ): String {
        val output = StringBuilder()
        val readable = options.copy(toneStyle = PinyinToneStyle.TONE_MARK)
        var index = 0
        var steps = 0
        while (index < text.length) {
            if (++steps and CHECK_INTERVAL_MASK == 0) checkpoint.check()
            val codePoint = text.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            if (isChinese(codePoint)) {
                counts.chinese++
                val pronunciations = readings?.get(codePoint)
                if (pronunciations.isNullOrEmpty()) {
                    counts.unsupported++
                    if (output.isNotEmpty()) append(output, "\n")
                    append(output, text.substring(index, index + charCount))
                } else {
                    if (output.isNotEmpty()) append(output, "\n")
                    append(output, text.substring(index, index + charCount))
                    append(output, ": ")
                    append(
                        output,
                        pronunciations.distinct().joinToString(READING_SEPARATOR) {
                            formatSyllable(it, readable)
                        },
                    )
                }
            } else {
                counts.nonChinese++
            }
            index += charCount
        }
        return output.toString()
    }

    // ---- Phrase matching ----

    private class PhraseMatch(val length: Int, val readings: Array<String>)

    private fun matchPhrase(text: String, start: Int): PhraseMatch? {
        val maxLength = minOf(PHRASE_MAX_LENGTH, text.length - start)
        for (length in maxLength downTo PHRASE_MIN_LENGTH) {
            val phrase = text.substring(start, start + length)
            val phraseReadings = PinyinPhrases.readings[phrase] ?: continue
            if (phraseReadings.size != length) continue
            if (defersToFollowingEntry(text, start, length, phraseReadings)) continue
            return PhraseMatch(length, phraseReadings)
        }
        return null
    }

    /**
     * True when the text right after [start] starts a longer dictionary entry that shares this
     * match's last character with a different reading (for example “有空调” must not lock 空 to
     * the reading of “有空” when “空调” follows). The lookahead is one step and only fires on a
     * real pronunciation conflict, so matches such as “重庆” stay intact.
     */
    private fun defersToFollowingEntry(
        text: String,
        start: Int,
        length: Int,
        readings: Array<String>,
    ): Boolean {
        // A two-character match still shares its last character with an entry starting one
        // code unit later, so the lookahead applies from the minimum phrase length on.
        if (length < PHRASE_MIN_LENGTH) return false
        val nextStart = start + 1
        val maxNextLength = minOf(PHRASE_MAX_LENGTH, text.length - nextStart)
        for (nextLength in maxNextLength downTo PHRASE_MIN_LENGTH) {
            val nextReadings = PinyinPhrases.readings[text.substring(nextStart, nextStart + nextLength)]
                ?: continue
            if (nextReadings.size != nextLength) continue
            if (nextStart + nextLength <= start + length) return false
            val sharedIndex = length - 2
            return sharedIndex in nextReadings.indices && nextReadings[sharedIndex] != readings[length - 1]
        }
        return false
    }

    // ---- Rendering ----

    private fun appendSyllable(
        output: StringBuilder,
        syllable: String,
        separator: String,
        previousWasSyllable: Boolean,
    ) {
        if (previousWasSyllable && separator.isNotEmpty()) append(output, separator)
        append(output, syllable)
    }

    private fun formatSyllable(numbered: String, options: PinyinOptions): String {
        val tone = numbered.lastOrNull()?.takeIf { it in '1'..'5' } ?: NEUTRAL_TONE
        val base = if (numbered.isNotEmpty() && numbered.last() in '1'..'5') {
            numbered.dropLast(1)
        } else {
            numbered
        }
        val formatted = when (options.toneStyle) {
            PinyinToneStyle.TONE_MARK -> applyToneMark(base, tone)
            PinyinToneStyle.TONE_NUMBER -> if (isNeutral(tone)) applyVChar(base, options.vCharType) else applyVChar(base, options.vCharType) + tone
            PinyinToneStyle.NO_TONE -> applyVChar(base, options.vCharType)
        }
        return applyCase(formatted, options.caseMode)
    }

    private fun isNeutral(tone: Char): Boolean = tone == NEUTRAL_TONE

    private fun applyToneMark(base: String, tone: Char): String {
        if (isNeutral(tone)) return toUnicodeV(base)
        val toneIndex = tone - '1'
        if (toneIndex !in 0..3) return toUnicodeV(base)
        val syllable = toUnicodeV(base)
        val lower = syllable.lowercase()
        val target = when {
            lower.indexOf('a') >= 0 -> lower.indexOf('a')
            lower.indexOf('e') >= 0 -> lower.indexOf('e')
            lower.indexOf("ou") >= 0 -> lower.indexOf("ou")
            else -> lower.indexOfLast { it in "iouü" }
        }
        if (target < 0) return markExceptional(syllable, toneIndex)
        val marks = TONE_MARKS[syllable[target].lowercaseChar()] ?: return syllable
        val marked = if (syllable[target].isUpperCase()) marks[toneIndex].uppercaseChar() else marks[toneIndex]
        return syllable.replaceRange(target, target + 1, marked.toString())
    }

    /** ê and the syllabic nasals m/n/ng carry the mark themselves instead of a vowel. */
    private fun markExceptional(base: String, toneIndex: Int): String {
        val position = base.indexOfFirst { it.isLetter() }
        if (position < 0) return base
        val letter = base[position]
        val forms = EXCEPTIONAL_MARKS[letter.lowercaseChar()] ?: return base
        val replacement = forms[toneIndex].let { if (letter.isUpperCase()) it.uppercase() else it }
        return base.replaceRange(position, position + 1, replacement)
    }

    private fun toUnicodeV(base: String): String = base.replace('v', 'ü').replace('V', 'Ü')

    private fun applyVChar(base: String, vCharType: PinyinVCharType): String = when (vCharType) {
        PinyinVCharType.UNICODE -> toUnicodeV(base)
        PinyinVCharType.V -> base
        PinyinVCharType.U_COLON -> base.replace("v", "u:").replace("V", "U:")
    }

    private fun applyCase(value: String, mode: PinyinCaseMode): String = when (mode) {
        PinyinCaseMode.LOWER -> value.lowercase()
        PinyinCaseMode.UPPER -> value.uppercase()
        PinyinCaseMode.CAPITALIZE -> value.replaceFirstChar {
            if (it.isLowerCase()) it.titlecaseChar() else it
        }
    }

    private fun caseInitial(initial: String, mode: PinyinCaseMode, first: Boolean): String = when (mode) {
        PinyinCaseMode.UPPER -> initial.uppercase()
        PinyinCaseMode.LOWER -> initial
        PinyinCaseMode.CAPITALIZE -> if (first) initial.replaceFirstChar { it.uppercaseChar() } else initial
    }

    private fun initialsOf(numbered: String): String {
        // The conventional abbreviation uses the first letter of each syllable (zhong -> z),
        // not the full initial consonant.
        return bareReading(numbered).take(1).lowercase()
    }

    private fun bareReading(numbered: String): String =
        if (numbered.isNotEmpty() && numbered.last() in '1'..'5') numbered.dropLast(1) else numbered

    private fun append(output: StringBuilder, value: String) {
        if (output.length + value.length > PinyinLimits.MAX_OUTPUT_LENGTH) {
            throw PinyinProcessingException(PinyinError.OUTPUT_TOO_LONG)
        }
        output.append(value)
    }

    // ---- Table loading and character classification ----

    private fun loadReadings(stream: InputStream, checkpoint: PinyinCheckpoint): Map<Int, List<String>> {
        val loaded = HashMap<Int, List<String>>(READING_TABLE_CAPACITY)
        var linesRead = 0
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (++linesRead and CHECK_INTERVAL_MASK == 0) checkpoint.check()
                if (line.isEmpty() || line[0] == '#') return@forEach
                val tab = line.indexOf('\t')
                if (tab <= 0 || tab == line.length - 1) return@forEach
                val codePoint = line.substring(0, tab).toIntOrNull(16) ?: return@forEach
                val values = line.substring(tab + 1).split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (values.isNotEmpty()) loaded[codePoint] = values
            }
        }
        check(loaded.size == EXPECTED_READINGS) { "Incomplete bundled pinyin table" }
        checkpoint.check()
        return loaded
    }

    private fun isChinese(codePoint: Int): Boolean =
        codePoint == 0x3007 || readings?.containsKey(codePoint) == true ||
            codePoint in 0x4E00..0x9FFF ||
            codePoint in 0x3400..0x4DBF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0x20000..0x2A6DF ||
            codePoint in 0x2A700..0x2B73F ||
            codePoint in 0x2B740..0x2B81F ||
            codePoint in 0x2B820..0x2CEAF ||
            codePoint in 0x2CEB0..0x2EBEF ||
            codePoint in 0x2EBF0..0x2EE5F ||
            codePoint in 0x2F800..0x2FA1F ||
            codePoint in 0x30000..0x3347F

    private class Counts {
        var chinese = 0
        var nonChinese = 0
        var unsupported = 0

        fun toStatistics() = PinyinStatistics(
            totalChars = chinese + nonChinese,
            chineseChars = chinese,
            nonChineseChars = nonChinese,
            unsupportedChineseChars = unsupported,
        )
    }

    private companion object {
        const val OUTPUT_CAPACITY = 64 * 1024
        const val CHECK_INTERVAL_MASK = 0xFF
        const val PHRASE_MIN_LENGTH = 2
        const val PHRASE_MAX_LENGTH = 4
        const val NEUTRAL_TONE = '5'
        const val READING_TABLE_CAPACITY = 45_000
        const val EXPECTED_READINGS = 44_435
        const val READING_SEPARATOR = " / "

        val TONE_MARKS: Map<Char, CharArray> = mapOf(
            'a' to charArrayOf('ā', 'á', 'ǎ', 'à'),
            'e' to charArrayOf('ē', 'é', 'ě', 'è'),
            'i' to charArrayOf('ī', 'í', 'ǐ', 'ì'),
            'o' to charArrayOf('ō', 'ó', 'ǒ', 'ò'),
            'u' to charArrayOf('ū', 'ú', 'ǔ', 'ù'),
            'ü' to charArrayOf('ǖ', 'ǘ', 'ǚ', 'ǜ'),
        )

        /** First tone has no precomposed form for the nasals, so combining marks are used. */
        val EXCEPTIONAL_MARKS: Map<Char, Array<String>> = mapOf(
            'm' to arrayOf("m\u0304", "ḿ", "m\u030c", "m̀"),
            'n' to arrayOf("n\u0304", "ń", "ň", "ǹ"),
            'ê' to arrayOf("ê\u0304", "ế", "ê\u030c", "ề"),
        )
    }
}
