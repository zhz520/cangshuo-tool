package com.cangshuo.toolbox.feature.text.data

import com.cangshuo.toolbox.feature.text.domain.TextFailure
import com.cangshuo.toolbox.feature.text.domain.TextProcessingException
import com.cangshuo.toolbox.feature.text.domain.TextProcessingPolicy

/** Conservative preflight only; RE2/J remains the syntax parser. */
internal object RegexBudgetPolicy {
    fun validate(pattern: String, guard: TextWorkGuard) {
        var index = 0
        var depth = 0
        var expansion = maxOf(1, pattern.length * 2).toLong()
        var inClass = false
        var firstInClass = false
        var negationAllowed = false
        while (index < pattern.length) {
            guard.checkpoint()
            val char = pattern[index]
            if (char == '\\') {
                val escaped = pattern.getOrNull(index + 1)
                if (!inClass && escaped == 'Q') {
                    val end = pattern.indexOf("\\E", index + 2)
                    index = if (end < 0) pattern.length else end + 2
                } else if ((escaped == 'p' || escaped == 'P' || escaped == 'x') && pattern.getOrNull(index + 2) == '{') {
                    val end = pattern.indexOf('}', index + 3)
                    index = if (end < 0) pattern.length else end + 1
                } else index += 2
                if (inClass) { firstInClass = false; negationAllowed = false }
                continue
            }
            if (inClass) {
                if (char == '[' && pattern.getOrNull(index + 1) == ':') {
                    val end = pattern.indexOf(":]", index + 2)
                    if (end >= 0) { index = end + 2; firstInClass = false; continue }
                }
                if (char == ']' && !firstInClass) inClass = false
                if (!(firstInClass && negationAllowed && char == '^')) firstInClass = false
                negationAllowed = false
                index++
                continue
            }
            when (char) {
                '[' -> { inClass = true; firstInClass = true; negationAllowed = true }
                '(' -> {
                    depth++
                    if (depth > TextProcessingPolicy.MAX_REGEX_DEPTH) tooComplex()
                }
                ')' -> depth = maxOf(0, depth - 1)
                '{' -> {
                    var cursor = index + 1
                    fun number(): Long? {
                        val start = cursor
                        var value = 0L
                        while (pattern.getOrNull(cursor)?.let { it in '0'..'9' } == true) {
                            value = minOf(1_000_000L, value * 10 + (pattern[cursor] - '0'))
                            cursor++
                        }
                        return if (cursor == start) null else value
                    }
                    val min = number()
                    var max = min
                    if (min != null && pattern.getOrNull(cursor) == ',') { cursor++; max = number() ?: min }
                    if (min != null && pattern.getOrNull(cursor) == '}') {
                        // Multiplying all repeat factors deliberately overestimates independent repeats.
                        val factor = maxOf(min, max ?: min) + 1
                        if (factor > TextProcessingPolicy.MAX_REGEX_EXPANSION / expansion) tooComplex()
                        expansion *= factor
                        index = cursor
                    }
                }
            }
            index++
        }
    }

    private fun tooComplex(): Nothing = throw TextProcessingException(TextFailure.REGEX_TOO_COMPLEX)
}
