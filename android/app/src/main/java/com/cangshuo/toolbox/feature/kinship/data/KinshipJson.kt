package com.cangshuo.toolbox.feature.kinship.data

/**
 * Minimal reader for the shipped kinship tables: `[["id",["name",...]],...]` with the
 * usual JSON string escapes. Avoids org.json so the loader also runs in JVM unit tests.
 */
internal class KinshipJson(private val text: String) {
    private var index = 0

    fun parsePairs(): ArrayList<Pair<String, List<String>>> {
        expect('[')
        val pairs = ArrayList<Pair<String, List<String>>>()
        if (peek() == ']') {
            index++
            return pairs
        }
        while (true) {
            expect('[')
            val key = parseString()
            expect(',')
            pairs.add(key to parseStringArray())
            expect(']')
            when (peek()) {
                ',' -> index++
                ']' -> {
                    index++
                    return pairs
                }

                else -> throw IllegalArgumentException("unexpected token at $index")
            }
        }
    }

    private fun parseStringArray(): List<String> {
        expect('[')
        val values = ArrayList<String>()
        if (peek() == ']') {
            index++
            return values
        }
        while (true) {
            values.add(parseString())
            when (peek()) {
                ',' -> index++
                ']' -> {
                    index++
                    return values
                }

                else -> throw IllegalArgumentException("unexpected token at $index")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val builder = StringBuilder()
        while (true) {
            val char = text[index++]
            when (char) {
                '"' -> return builder.toString()
                '\\' -> {
                    when (val escape = text[index++]) {
                        '"', '\\', '/' -> builder.append(escape)
                        'b' -> builder.append('\b')
                        'f' -> builder.append('\u000C')
                        'n' -> builder.append('\n')
                        'r' -> builder.append('\r')
                        't' -> builder.append('\t')
                        'u' -> {
                            builder.append(text.substring(index, index + 4).toInt(16).toChar())
                            index += 4
                        }

                        else -> throw IllegalArgumentException("bad escape \\$escape at $index")
                    }
                }

                else -> builder.append(char)
            }
        }
    }

    private fun expect(expected: Char) {
        if (peek() != expected) throw IllegalArgumentException("expected $expected at $index")
        index++
    }

    private fun peek(): Char {
        while (index < text.length && text[index].isWhitespace()) index++
        return if (index < text.length) text[index] else '\u0000'
    }
}
