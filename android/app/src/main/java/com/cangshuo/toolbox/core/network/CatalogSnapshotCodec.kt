package com.cangshuo.toolbox.core.network

import com.cangshuo.toolbox.core.network.model.ToolCatalogDto
import java.security.MessageDigest

/** Private cache format. Budgets apply while writing, before a UTF-8 byte array is allocated. */
internal object CatalogSnapshotCodec {
    const val VERSION = 1
    const val MAX_BYTES = 1_000_000

    fun encode(records: List<ToolCatalogDto>, checkpoint: () -> Unit = {}): String {
        validateWebToolSnapshot(records, checkpoint)
        val writer = Writer(checkpoint)
        writer.raw("{\"records\":[")
        records.forEachIndexed { index, record ->
            checkpoint()
            if (index != 0) writer.raw(",")
            writer.raw("{")
            writer.field("code", record.code)
            writer.raw(","); writer.field("name", record.name)
            writer.raw(","); writer.field("description", record.description)
            writer.raw(","); writer.field("categoryCode", record.categoryCode)
            writer.raw(",\"icon\":")
            if (record.icon == null) writer.raw("null") else writer.quoted(record.icon)
            writer.raw(",\"keywords\":[")
            record.keywords.forEachIndexed { wordIndex, word ->
                if (wordIndex != 0) writer.raw(",")
                writer.quoted(word)
            }
            writer.raw("]"); writer.raw(","); writer.field("mode", record.mode)
            writer.raw(",\"requiresLogin\":${record.requiresLogin}")
            writer.raw(","); writer.field("status", record.status)
            writer.raw(",\"version\":${record.version},\"sortOrder\":${record.sortOrder},\"isFeatured\":${record.isFeatured}}")
        }
        writer.raw("]}")
        return writer.result()
    }

    fun decode(payload: String, checkpoint: () -> Unit = {}): List<ToolCatalogDto> {
        // Count UTF-8 without allocating a potentially oversized encoded copy.
        val counter = Writer(checkpoint)
        counter.countText(payload)
        return CatalogJsonReader(payload, checkpoint).webSnapshot()
    }

    fun digest(boundedText: String): String = MessageDigest.getInstance("SHA-256")
        .digest(boundedText.toByteArray(Charsets.UTF_8)).joinToString("") { byte ->
            val hex = "0123456789abcdef"
            "${hex[(byte.toInt() and 255) ushr 4]}${hex[byte.toInt() and 15]}"
        }

    private class Writer(private val checkpoint: () -> Unit) {
        private val output = StringBuilder()
        private var bytes = 0
        private fun reserve(count: Int) {
            if (count > MAX_BYTES - bytes) throw ToolCatalogException(ToolCatalogFailure.RESOURCE_LIMIT)
            bytes += count
        }
        fun raw(ascii: String) { reserve(ascii.length); output.append(ascii) }
        fun field(key: String, value: String) { quoted(key); raw(":"); quoted(value) }
        fun quoted(value: String) {
            raw("\"")
            visit(value) { part, size ->
                when {
                    part == "\"" -> raw("\\\"")
                    part == "\\" -> raw("\\\\")
                    part.length == 1 && part[0].code < 32 -> raw("\\u" + part[0].code.toString(16).padStart(4, '0'))
                    else -> { reserve(size); output.append(part) }
                }
            }
            raw("\"")
        }
        fun countText(value: String) { visit(value) { _, size -> reserve(size) } }
        private fun visit(value: String, append: (String, Int) -> Unit) {
            var index = 0
            while (index < value.length) {
                if (index and 255 == 0) checkpoint()
                val char = value[index++]
                when {
                    char.isHighSurrogate() -> {
                        val low = value.getOrNull(index++)
                        if (low == null || !low.isLowSurrogate()) throw ToolCatalogException(ToolCatalogFailure.INVALID_RESPONSE)
                        append("$char$low", 4)
                    }
                    char.isLowSurrogate() -> throw ToolCatalogException(ToolCatalogFailure.INVALID_RESPONSE)
                    else -> append(char.toString(), when { char.code <= 127 -> 1; char.code <= 2047 -> 2; else -> 3 })
                }
            }
            checkpoint()
        }
        fun result(): String = output.toString()
    }
}
