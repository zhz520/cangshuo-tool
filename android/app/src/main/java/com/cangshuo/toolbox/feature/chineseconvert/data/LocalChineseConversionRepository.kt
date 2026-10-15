package com.cangshuo.toolbox.feature.chineseconvert.data

import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionCheckpoint
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionError
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionException
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionLimits
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionMode
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionOutcome
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionRepository
import com.cangshuo.toolbox.feature.chineseconvert.domain.ChineseConversionStatistics
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * OpenCC 1.1.9 config semantics: maximum-prefix segmentation, ordered dictionary groups,
 * first-candidate selection, and chained conversion within each original segment.
 * Data: Apache-2.0, BYVoid/OpenCC commit 556ed22496d650bd0b13b6c163be9814637970ae.
 * No JNI/dependency or network; unmatched Unicode, whitespace and punctuation are preserved.
 * The use case serializes access, and only completely verified tables enter the cache.
 */
class LocalChineseConversionRepository(private val openTable: (String) -> InputStream) : ChineseConversionRepository {
    private val cache = HashMap<String, Dictionary>()

    override fun convert(
        text: String,
        mode: ChineseConversionMode,
        checkpoint: ChineseConversionCheckpoint,
    ): ChineseConversionOutcome.Success {
        if (text.length > ChineseConversionLimits.MAX_INPUT) {
            throw ChineseConversionException(ChineseConversionError.INPUT_TOO_LONG)
        }
        checkpoint.check()
        if (!Charsets.UTF_8.newEncoder().canEncode(text)) {
            throw ChineseConversionException(ChineseConversionError.INVALID_UNICODE)
        }
        if (text.isEmpty()) return ChineseConversionOutcome.Success("", ChineseConversionStatistics(0, 0, 0))
        val plan = planFor(mode)
        val segmentation = dictionary(plan.segmentation, checkpoint)
        val chain = plan.stages.map { group -> group.map { dictionary(it, checkpoint) } }
        val result = StringBuilder(text.length)
        var position = 0
        var unmatchedStart = 0
        var changed = 0
        var steps = 0

        fun emit(start: Int, end: Int) {
            val original = text.substring(start, end)
            var converted = original
            chain.forEach { group -> converted = convertSegment(converted, group, checkpoint) }
            if (converted != original) changed++
            appendBounded(result, converted)
        }

        while (position < text.length) {
            if (++steps and CHECK_MASK == 0) checkpoint.check()
            val match = segmentation.match(text, position)
            if (match == null) {
                position += Character.charCount(text.codePointAt(position))
            } else {
                if (unmatchedStart < position) emit(unmatchedStart, position)
                emit(position, position + match.length)
                position += match.length
                unmatchedStart = position
            }
        }
        if (unmatchedStart < text.length) emit(unmatchedStart, text.length)
        checkpoint.check()
        val output = result.toString()
        return ChineseConversionOutcome.Success(
            output,
            ChineseConversionStatistics(text.codePointCount(0, text.length), output.codePointCount(0, output.length), changed),
        )
    }

    private fun convertSegment(text: String, group: List<Dictionary>, checkpoint: ChineseConversionCheckpoint): String {
        val result = StringBuilder(text.length)
        var position = 0
        var steps = 0
        while (position < text.length) {
            if (++steps and CHECK_MASK == 0) checkpoint.check()
            // OpenCC 1.1.9 DictGroup is short-circuit: the first table with any match wins.
            val match = group.firstNotNullOfOrNull { it.match(text, position) }
            if (match == null) {
                val next = position + Character.charCount(text.codePointAt(position))
                appendBounded(result, text.substring(position, next))
                position = next
            } else {
                appendBounded(result, match.replacement)
                position += match.length
            }
        }
        checkpoint.check()
        return result.toString()
    }

    private fun dictionary(name: String, checkpoint: ChineseConversionCheckpoint): Dictionary {
        cache[name]?.let { return it }
        val spec = requireNotNull(TABLES[name])
        val digest = MessageDigest.getInstance("SHA-256")
        val entries = HashMap<String, String>(spec.count)
        val lengths = HashMap<Int, MutableSet<Int>>()
        var rows = 0
        DigestInputStream(openTable(name), digest).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (++rows and CHECK_MASK == 0) checkpoint.check()
                check(rows <= spec.count && line.length <= MAX_DICTIONARY_LINE)
                val tab = line.indexOf('\t')
                check(tab > 0 && tab < line.lastIndex && line.indexOf('\t', tab + 1) == -1)
                val key = line.substring(0, tab)
                val value = line.substring(tab + 1)
                check(entries.put(key, value) == null)
                lengths.getOrPut(key.codePointAt(0)) { HashSet() }.add(key.length)
            }
        }
        check(rows == spec.count)
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        check(hash == spec.sha256)
        val index = lengths.mapValues { (_, values) -> values.sortedDescending().toIntArray() }
        checkpoint.check()
        return Dictionary(entries, index).also { cache[name] = it }
    }

    private data class Dictionary(val entries: Map<String, String>, val lengths: Map<Int, IntArray>) {
        fun match(text: String, offset: Int): Match? {
            val candidates = lengths[text.codePointAt(offset)] ?: return null
            for (length in candidates) {
                if (offset + length > text.length) continue
                val replacement = entries[text.substring(offset, offset + length)]
                if (replacement != null) return Match(length, replacement)
            }
            return null
        }
    }

    private data class Match(val length: Int, val replacement: String)
    private data class Plan(val segmentation: String, val stages: List<List<String>>)
    private data class TableSpec(val count: Int, val sha256: String)

    private fun planFor(mode: ChineseConversionMode): Plan {
        val st = listOf("STPhrases", "STCharacters")
        val ts = listOf("TSPhrases", "TSCharacters")
        val tw = listOf("TWVariantsRevPhrases", "TWVariantsRev")
        return when (mode) {
            ChineseConversionMode.S2T -> Plan("STPhrases", listOf(st))
            ChineseConversionMode.T2S -> Plan("TSPhrases", listOf(ts))
            ChineseConversionMode.S2TW -> Plan("STPhrases", listOf(st, listOf("TWVariants")))
            ChineseConversionMode.TW2S -> Plan("TSPhrases", listOf(tw, ts))
            ChineseConversionMode.S2TWP -> Plan("STPhrases", listOf(st, listOf("TWPhrases"), listOf("TWVariants")))
            ChineseConversionMode.TW2SP -> Plan("TSPhrases", listOf(listOf("TWPhrasesRev") + tw, ts))
            ChineseConversionMode.S2HK -> Plan("STPhrases", listOf(st, listOf("HKVariants")))
            ChineseConversionMode.HK2S -> Plan("TSPhrases", listOf(listOf("HKVariantsRevPhrases", "HKVariantsRev"), ts))
        }
    }

    private fun appendBounded(builder: StringBuilder, value: String) {
        if (builder.length > ChineseConversionLimits.MAX_OUTPUT - value.length) {
            throw ChineseConversionException(ChineseConversionError.OUTPUT_TOO_LONG)
        }
        builder.append(value)
    }

    private companion object {
        const val CHECK_MASK = 0xff
        const val MAX_DICTIONARY_LINE = 256
        val TABLES = mapOf(
            "STCharacters" to TableSpec(3980, "5f030b6ec68074b26b9b8816fbcb7887f7e518a68174327ea6310aecc489e977"),
            "STPhrases" to TableSpec(49096, "76559d352bd3ac0bb1024cc80ed1b776efa8034b950c1bdf14784c97c07e081d"),
            "TSCharacters" to TableSpec(4113, "479a998eb07c19d9ccbadbb7e737843a3528f096cddd54fcdaf2ca118873784d"),
            "TSPhrases" to TableSpec(278, "5b76ff04a507a472bc5cb5366a405cd3a8976a5598226fdc22ecd6296aa73eff"),
            "TWVariants" to TableSpec(39, "30e6f8395edbfdd74e293fd8b9c62105d787c849fbb208d2a7832eac696734d7"),
            "TWVariantsRev" to TableSpec(39, "aed256cd515db700cbd96fae5d2158d6a98c9628ee51e14ac1e1c182f6041c90"),
            "TWVariantsRevPhrases" to TableSpec(68, "2f9145fce55f1f10ff6e401dc34a8e6cbf892d30d3392f52ce385f9a0018d281"),
            "TWPhrases" to TableSpec(512, "f6d68e00a98fed36a9dd172271df27ca45849654efc937faa676fe4c21ff3da4"),
            "TWPhrasesRev" to TableSpec(522, "d61e2bb021535748e36901ee723cbabd5fccd22a3d9f97dde2225eaaac5d6399"),
            "HKVariants" to TableSpec(63, "bebc74ecf90d6f250e67e9f18bdea2753ee9e525df9a8dc9791cfe5d730abef7"),
            "HKVariantsRev" to TableSpec(70, "1b9d1a6bae833245d547ba7299d53d99cb22ed22477241c6f5d16df1c0182404"),
            "HKVariantsRevPhrases" to TableSpec(156, "3da1d42168bf87a3fb6f4f9280b46ff70a554d4f68e6356cb5e0698300ac8a88"),
        )
    }
}
