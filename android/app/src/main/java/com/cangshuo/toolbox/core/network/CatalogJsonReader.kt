package com.cangshuo.toolbox.core.network

import com.cangshuo.toolbox.core.network.model.ToolCatalogDto
import com.cangshuo.toolbox.core.network.model.ToolCatalogPageDto

/** Bounded RFC 8259 reader for wire data; no coercion, floating-point conversion or reflection. */
internal class CatalogJsonReader(private val text: String, private val checkpoint: () -> Unit) {
    private var index = 0
    private var tokens = 0
    private var maxTokens = 20_000
    private data class NumberLiteral(val text: String)

    fun page(traceId: String?): ToolCatalogPageDto {
        val envelope = document()
        if (envelope.long("code") != 0L) invalid()
        if (envelope.string("message").isBlank()) invalid()
        val trace = envelope.string("traceId")
        if (!TRACE_ID.matches(trace) || trace != traceId) invalid()
        val data = envelope["data"].objectValue()
        val dtos = records(data["records"], 100)
        val page = data.int("page")
        val pageSize = data.int("pageSize")
        val total = data.long("total")
        if (page < 1 || pageSize !in 1..100 || total < 0 || dtos.size > pageSize) invalid()
        checkpoint()
        return ToolCatalogPageDto(dtos, page, pageSize, total)
    }

    fun webSnapshot(): List<ToolCatalogDto> {
        maxTokens = 60_000
        return records(document()["records"], 300).also { validateWebToolSnapshot(it, checkpoint) }
    }

    private fun document(): Map<*, *> {
        val document = value(0).objectValue()
        whitespace()
        if (index != text.length) invalid()
        return document
    }

    private fun records(value: Any?, maxRecords: Int): List<ToolCatalogDto> {
        val records = value as? List<*> ?: invalid()
        if (records.size > maxRecords) limit()
        return records.map { record ->
            checkpoint()
            val item = record.objectValue()
            val keywords = item["keywords"] as? List<*> ?: invalid()
            if (keywords.size > 64) limit()
            val dto = ToolCatalogDto(
                code = item.string("code"),
                name = item.string("name"),
                description = item.string("description"),
                categoryCode = item.string("categoryCode"),
                icon = if (item.containsKey("icon") && item["icon"] == null) null else item.string("icon"),
                keywords = keywords.map { (it as? String ?: invalid()).also { word -> if (word.length > 512) limit() } },
                mode = item.string("mode"),
                requiresLogin = item.boolean("requiresLogin"),
                status = item.string("status"),
                version = item.int("version"),
                sortOrder = item.int("sortOrder"),
                isFeatured = item.boolean("isFeatured"),
            )
            if (dto.toMetadataOrNull() == null || dto.status != "ENABLED") invalid()
            dto
        }
    }

    private fun value(depth: Int): Any? {
        checkpoint()
        if (++tokens > maxTokens) limit()
        whitespace()
        return when (peek()) {
            '{' -> objectValue(depth + 1)
            '[' -> arrayValue(depth + 1)
            '"' -> string()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            '-', in '0'..'9' -> number()
            else -> invalid()
        }
    }

    private fun objectValue(depth: Int): Map<String, Any?> {
        if (depth > 16) limit()
        index++
        whitespace()
        val result = LinkedHashMap<String, Any?>()
        if (take('}')) return result
        while (true) {
            if (++tokens > maxTokens) limit()
            val key = string()
            if (result.containsKey(key)) invalid()
            whitespace()
            expect(':')
            result[key] = value(depth)
            whitespace()
            if (take('}')) return result
            expect(',')
            whitespace()
        }
    }

    private fun arrayValue(depth: Int): List<Any?> {
        if (depth > 16) limit()
        index++
        whitespace()
        val result = ArrayList<Any?>()
        if (take(']')) return result
        while (true) {
            result += value(depth)
            whitespace()
            if (take(']')) return result
            expect(',')
        }
    }

    private fun string(): String {
        expect('"')
        val result = StringBuilder()
        var pendingHigh = false
        while (index < text.length) {
            step()
            var char = text[index++]
            if (char == '"') {
                if (pendingHigh) invalid()
                return result.toString()
            }
            if (char.code < 0x20) invalid()
            if (char == '\\') {
                char = when (text.getOrNull(index++)) {
                    '"' -> '"'
                    '\\' -> '\\'
                    '/' -> '/'
                    'b' -> '\b'
                    'f' -> '\u000C'
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    'u' -> unicode()
                    else -> invalid()
                }
            }
            if (pendingHigh) {
                if (!char.isLowSurrogate()) invalid()
                pendingHigh = false
            } else when {
                char.isHighSurrogate() -> pendingHigh = true
                char.isLowSurrogate() -> invalid()
            }
            if (result.length >= 4096) limit()
            result.append(char)
        }
        invalid()
    }

    private fun unicode(): Char {
        var code = 0
        repeat(4) {
            val digit = when (val char = text.getOrNull(index++)) {
                in '0'..'9' -> requireNotNull(char) - '0'
                in 'a'..'f' -> requireNotNull(char) - 'a' + 10
                in 'A'..'F' -> requireNotNull(char) - 'A' + 10
                else -> invalid()
            }
            code = code * 16 + digit
        }
        return code.toChar()
    }

    private fun number(): NumberLiteral {
        val start = index
        take('-')
        if (take('0')) {
            if (digit()) invalid()
        } else {
            if (peek() !in '1'..'9') invalid()
            digits(start)
        }
        if (take('.')) {
            if (!digit()) invalid()
            digits(start)
        }
        if (take('e') || take('E')) {
            if (!take('+')) take('-')
            if (!digit()) invalid()
            digits(start)
        }
        if (index - start > 64) limit()
        return NumberLiteral(text.substring(start, index))
    }

    private fun digits(start: Int) {
        while (digit()) {
            if (index - start >= 64) limit()
            step()
            index++
        }
    }

    private fun literal(expected: String, result: Any?): Any? {
        if (!text.startsWith(expected, index)) invalid()
        index += expected.length
        return result
    }

    private fun whitespace() {
        while (peek() == ' ' || peek() == '\t' || peek() == '\r' || peek() == '\n') {
            step()
            index++
        }
    }

    private fun step() { if (index and 255 == 0) checkpoint() }
    private fun peek(): Char? = text.getOrNull(index)
    private fun digit() = peek() in '0'..'9'
    private fun take(char: Char): Boolean = if (peek() == char) { index++; true } else false
    private fun expect(char: Char) { if (!take(char)) invalid() }
    private fun Any?.objectValue(): Map<*, *> = this as? Map<*, *> ?: invalid()
    private fun Map<*, *>.string(key: String): String = this[key] as? String ?: invalid()
    private fun Map<*, *>.boolean(key: String): Boolean = this[key] as? Boolean ?: invalid()
    private fun Map<*, *>.long(key: String): Long {
        val number = (this[key] as? NumberLiteral)?.text ?: invalid()
        if (!INTEGER.matches(number)) invalid()
        return number.toLongOrNull() ?: invalid()
    }
    private fun Map<*, *>.int(key: String): Int = long(key).let {
        if (it !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) invalid()
        it.toInt()
    }
    private fun invalid(): Nothing = throw ToolCatalogException(ToolCatalogFailure.INVALID_RESPONSE)
    private fun limit(): Nothing = throw ToolCatalogException(ToolCatalogFailure.RESOURCE_LIMIT)

    private companion object {
        val TRACE_ID = Regex("[0-9a-f]{32}")
        val INTEGER = Regex("-?(0|[1-9][0-9]*)")
    }
}
