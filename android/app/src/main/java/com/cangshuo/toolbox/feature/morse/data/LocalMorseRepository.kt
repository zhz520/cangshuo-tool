package com.cangshuo.toolbox.feature.morse.data

import com.cangshuo.toolbox.feature.morse.domain.MorseAlphabet
import com.cangshuo.toolbox.feature.morse.domain.MorseDirection
import com.cangshuo.toolbox.feature.morse.domain.MorseError
import com.cangshuo.toolbox.feature.morse.domain.MorseRepository
import com.cangshuo.toolbox.feature.morse.domain.MorseResult
import com.cangshuo.toolbox.feature.morse.domain.MorseTone

class LocalMorseRepository : MorseRepository {

    override fun encode(text: String): MorseResult {
        if (text.isBlank()) return MorseResult.Error(MorseError.EMPTY)
        val parts = mutableListOf<String>()
        val unsupported = mutableListOf<String>()
        var symbolCount = 0
        var pendingWordGap = false

        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            if (charCount > 1) {
                // Surrogate pairs (emoji and other astral characters) are unsupported as a unit.
                unsupported += String(Character.toChars(codePoint))
                i += charCount
                continue
            }
            val ch = text[i]
            when {
                ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' -> {
                    if (parts.isNotEmpty()) pendingWordGap = true
                }
                else -> {
                    val code = MorseAlphabet.ENCODE_MAP[ch.uppercaseChar()]
                    if (code == null) {
                        unsupported += ch.toString()
                    } else {
                        if (pendingWordGap) {
                            parts += WORD_GAP
                            pendingWordGap = false
                        }
                        parts += code
                        symbolCount += code.length
                    }
                }
            }
            i += charCount
        }
        if (parts.isEmpty()) {
            return if (unsupported.isEmpty()) {
                MorseResult.Error(MorseError.EMPTY)
            } else {
                MorseResult.Success(output = "", unsupported = unsupported.toList(), symbolCount = 0)
            }
        }
        return MorseResult.Success(
            output = parts.joinToString(" "),
            unsupported = unsupported.toList(),
            symbolCount = symbolCount,
        )
    }

    override fun decode(code: String): MorseResult {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return MorseResult.Error(MorseError.EMPTY)
        val words = trimmed.split(WORD_SEPARATOR)
        val out = StringBuilder()
        var symbolIndex = 0
        var wroteWord = false
        for (word in words) {
            val symbols = word.trim().split(SYMBOL_SEPARATOR).filter { it.isNotEmpty() }
            if (symbols.isEmpty()) continue
            if (wroteWord && out.isNotEmpty()) out.append(' ')
            wroteWord = true
            for (symbol in symbols) {
                symbolIndex++
                val ch = MorseAlphabet.DECODE_MAP[symbol]
                    ?: return MorseResult.Error(MorseError.INVALID_SYMBOL, symbolIndex)
                out.append(ch)
            }
        }
        if (out.isEmpty()) return MorseResult.Error(MorseError.EMPTY)
        return MorseResult.Success(output = out.toString(), unsupported = emptyList(), symbolCount = symbolIndex)
    }

    override fun timeline(text: String, direction: MorseDirection): List<MorseTone>? {
        val letters: List<List<String>> = when (direction) {
            MorseDirection.ENCODE -> {
                val result = encode(text)
                if (result !is MorseResult.Success || result.output.isEmpty()) return null
                splitWords(result.output).map { word ->
                    word.split(' ').filter { it.isNotEmpty() }
                }
            }
            MorseDirection.DECODE -> {
                val trimmed = text.trim()
                if (trimmed.isEmpty()) return null
                splitWords(trimmed)
                    .map { word -> word.split(' ', '\t', '\n').filter { it.isNotEmpty() } }
            }
        }
        val tones = mutableListOf<MorseTone>()
        val validWords = letters.filter { it.isNotEmpty() }
        if (validWords.isEmpty()) return null
        for ((wordIndex, word) in validWords.withIndex()) {
            if (wordIndex > 0) tones += MorseTone.Gap(7)
            for ((letterIndex, symbol) in word.withIndex()) {
                if (letterIndex > 0) tones += MorseTone.Gap(3)
                for ((signalIndex, signal) in symbol.withIndex()) {
                    if (signalIndex > 0) tones += MorseTone.Gap(1)
                    tones += when (signal) {
                        '.' -> MorseTone.Sound(1)
                        '-' -> MorseTone.Sound(3)
                        else -> return null
                    }
                }
            }
        }
        return tones
    }

    private fun splitWords(code: String): List<String> =
        code.split(WORD_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        private const val WORD_GAP = "/"
        private val WORD_SEPARATOR = Regex("/+")
        private val SYMBOL_SEPARATOR = Regex("\\s+")
    }
}
