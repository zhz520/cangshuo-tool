package com.cangshuo.toolbox.feature.json.data

import com.cangshuo.toolbox.feature.json.domain.JsonFailure
import com.cangshuo.toolbox.feature.json.domain.JsonProcessingException
import com.cangshuo.toolbox.feature.json.domain.JsonProcessingPolicy

/** Grammar validation with bounded whitespace rewriting; no numeric conversion or object tree. */
internal class StrictJsonReader(
    private val input: String,
    private val guard: JsonWorkGuard,
    private val indent: Int = 0,
) {
    private var index = 0
    private var tokens = 0
    var duplicateKeyCount = 0
        private set
    private val output = JsonOutput(input.length, guard)
    private val padding = " ".repeat(indent * JsonProcessingPolicy.MAX_DEPTH)

    fun document(): String? {
        skipWhitespace()
        if (index == input.length) return null
        value(0)
        skipWhitespace()
        if (index != input.length) fail(JsonFailure.TRAILING_CONTENT)
        return output.toString()
    }

    fun decodedString(quoted: Boolean): String {
        val result = if (quoted) {
            skipWhitespace()
            if (peek() != '"') fail(JsonFailure.INVALID_STRING)
            index++
            stringBody(quoted = true, decode = true)
        } else stringBody(quoted = false, decode = true)
        if (quoted) skipWhitespace()
        if (index != input.length) fail(JsonFailure.TRAILING_CONTENT)
        return result
    }

    private fun value(depth: Int) {
        guard.checkpoint()
        token()
        skipWhitespace()
        val start = index
        when (peek()) {
            '{' -> container(depth + 1, objectMode = true)
            '[' -> container(depth + 1, objectMode = false)
            '"' -> { index++; stringBody(quoted = true, decode = false); output.append(input, start, index) }
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            '-', in '0'..'9' -> number()
            else -> fail(JsonFailure.EXPECTED_VALUE)
        }
    }

    private fun container(depth: Int, objectMode: Boolean) {
        if (depth > JsonProcessingPolicy.MAX_DEPTH) fail(JsonFailure.DEPTH_LIMIT)
        val close = if (objectMode) '}' else ']'
        output.append(input[index++])
        skipWhitespace()
        if (peek() == close) { index++; output.append(close); return }
        val keys = if (objectMode) HashSet<String>() else null
        while (true) {
            guard.checkpoint()
            newline(depth)
            if (objectMode) {
                token()
                if (peek() != '"') fail(JsonFailure.EXPECTED_KEY)
                val start = index++
                val key = stringBody(quoted = true, decode = true)
                if (!requireNotNull(keys).add(key)) duplicateKeyCount++
                output.append(input, start, index)
                skipWhitespace()
                if (peek() != ':') fail(JsonFailure.EXPECTED_COLON)
                index++
                output.append(':')
                if (indent > 0) output.append(' ')
            }
            value(depth)
            skipWhitespace()
            when (peek()) {
                close -> { index++; newline(depth - 1); output.append(close); return }
                ',' -> { index++; output.append(','); skipWhitespace() }
                else -> fail(JsonFailure.EXPECTED_SEPARATOR)
            }
        }
    }

    private fun number() {
        val start = index
        if (peek() == '-') index++
        when (peek()) {
            '0' -> { index++; if (digit()) fail(JsonFailure.INVALID_NUMBER) }
            in '1'..'9' -> digits()
            else -> fail(JsonFailure.INVALID_NUMBER)
        }
        if (peek() == '.') { index++; if (!digit()) fail(JsonFailure.INVALID_NUMBER); digits() }
        if (peek() == 'e' || peek() == 'E') {
            index++
            if (peek() == '+' || peek() == '-') index++
            if (!digit()) fail(JsonFailure.INVALID_NUMBER)
            digits()
        }
        if (!delimiter()) fail(JsonFailure.INVALID_NUMBER)
        output.append(input, start, index)
    }

    private fun literal(text: String) {
        if (!input.regionMatches(index, text, 0, text.length)) fail(JsonFailure.EXPECTED_VALUE)
        val start = index
        index += text.length
        if (!delimiter()) fail(JsonFailure.EXPECTED_VALUE)
        output.append(input, start, index)
    }

    private fun stringBody(quoted: Boolean, decode: Boolean): String {
        val decoded = if (decode) JsonOutput(minOf(input.length, 256), guard) else null
        var pendingHigh: Int? = null
        while (index < input.length) {
            guard.step()
            val origin = index
            var char = input[index++]
            if (char == '"') {
                if (!quoted) fail(JsonFailure.INVALID_STRING, origin)
                pendingHigh?.let { fail(JsonFailure.INVALID_UNICODE, it) }
                return decoded?.toString().orEmpty()
            }
            if (char.code < 0x20) fail(JsonFailure.INVALID_STRING, origin)
            if (char == '\\') {
                val escaped = input.getOrNull(index++) ?: fail(JsonFailure.INVALID_ESCAPE, origin)
                char = when (escaped) {
                    '"', '\\', '/' -> escaped
                    'b' -> '\b'
                    'f' -> '\u000C'
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    'u' -> unicodeEscape(origin)
                    else -> fail(JsonFailure.INVALID_ESCAPE, origin)
                }
            }
            if (pendingHigh != null) {
                if (!char.isLowSurrogate()) fail(JsonFailure.INVALID_UNICODE, pendingHigh)
                pendingHigh = null
            } else when {
                char.isHighSurrogate() -> pendingHigh = origin
                char.isLowSurrogate() -> fail(JsonFailure.INVALID_UNICODE, origin)
            }
            decoded?.append(char)
        }
        pendingHigh?.let { fail(JsonFailure.INVALID_UNICODE, it) }
        if (quoted) fail(JsonFailure.INVALID_STRING)
        return decoded?.toString().orEmpty()
    }

    private fun unicodeEscape(origin: Int): Char {
        var code = 0
        repeat(4) {
            val char = input.getOrNull(index++) ?: fail(JsonFailure.INVALID_ESCAPE, origin)
            val digit = when (char) { in '0'..'9' -> char - '0'; in 'a'..'f' -> char - 'a' + 10; in 'A'..'F' -> char - 'A' + 10; else -> fail(JsonFailure.INVALID_ESCAPE, origin) }
            code = code * 16 + digit
        }
        return code.toChar()
    }

    private fun token() { if (++tokens > JsonProcessingPolicy.MAX_TOKENS) fail(JsonFailure.TOKEN_LIMIT) }
    private fun digits() { while (digit()) { guard.step(); index++ } }
    private fun digit() = peek()?.let { it in '0'..'9' } == true
    private fun peek(): Char? = input.getOrNull(index)
    private fun delimiter(): Boolean = when (val char = peek()) {
        null, ',', ']', '}' -> true
        else -> whitespace(char)
    }
    private fun whitespace(char: Char?) = char == ' ' || char == '\t' || char == '\r' || char == '\n'
    private fun skipWhitespace() { while (whitespace(peek())) { guard.step(); index++ } }
    private fun newline(depth: Int) {
        if (indent == 0) return
        output.append('\n')
        output.append(padding, 0, depth * indent)
    }
    private fun fail(reason: JsonFailure, offset: Int = index): Nothing = throw JsonProcessingException(reason, offset)
}
