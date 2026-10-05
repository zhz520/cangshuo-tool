package com.cangshuo.toolbox.feature.text.data

import com.cangshuo.toolbox.feature.text.domain.TextFailure
import com.cangshuo.toolbox.feature.text.domain.TextProcessingException
import com.google.re2j.Matcher
import com.google.re2j.Pattern

internal class TextReplacementTemplate private constructor(private val parts: List<Part>) {
    private sealed interface Part {
        data class Literal(val text: String) : Part
        data class Group(val index: Int) : Part
    }

    fun append(output: BoundedTextBuilder, matcher: Matcher, input: String, guard: TextWorkGuard) {
        for (part in parts) {
            guard.checkpoint()
            when (part) {
                is Part.Literal -> output.append(part.text)
                is Part.Group -> {
                    val start = matcher.start(part.index)
                    val end = matcher.end(part.index)
                    if (start >= 0) output.append(input, start, end)
                }
            }
        }
    }

    companion object {
        fun parse(replacement: String, pattern: Pattern, expandGroups: Boolean, guard: TextWorkGuard): TextReplacementTemplate {
            if (!expandGroups) return TextReplacementTemplate(listOf(Part.Literal(replacement)))
            val parts = mutableListOf<Part>()
            val literal = StringBuilder()
            fun flush() { if (literal.isNotEmpty()) { parts.add(Part.Literal(literal.toString())); literal.clear() } }
            var index = 0
            while (index < replacement.length) {
                guard.checkpoint()
                when (val char = replacement[index++]) {
                    '\\' -> {
                        if (index == replacement.length) invalid()
                        literal.append(replacement[index++])
                    }
                    '$' -> {
                        flush()
                        val next = replacement.getOrNull(index) ?: invalid()
                        val group = if (next == '{') {
                            val end = replacement.indexOf('}', index + 1)
                            if (end < 0) invalid()
                            val name = replacement.substring(index + 1, end)
                            index = end + 1
                            pattern.namedGroups()[name] ?: invalid()
                        } else {
                            if (next !in '0'..'9') invalid()
                            var group = replacement[index++] - '0'
                            if (group > pattern.groupCount()) invalid()
                            while (replacement.getOrNull(index)?.let { it in '0'..'9' } == true) {
                                val expanded = group * 10 + (replacement[index] - '0')
                                if (expanded > pattern.groupCount()) break
                                group = expanded
                                index++
                            }
                            group
                        }
                        parts.add(Part.Group(group))
                    }
                    else -> literal.append(char)
                }
            }
            flush()
            return TextReplacementTemplate(parts)
        }

        private fun invalid(): Nothing = throw TextProcessingException(TextFailure.INVALID_REPLACEMENT)
    }
}
