package com.cangshuo.toolbox.core.network

import org.junit.Assert.*
import org.junit.Test

class CatalogJsonReaderTest {
    private fun parse(text: String, trace: String? = CATALOG_TRACE) = CatalogJsonReader(text) {}.page(trace)
    private fun fails(text: String, reason: ToolCatalogFailure = ToolCatalogFailure.INVALID_RESPONSE, trace: String? = CATALOG_TRACE) {
        val error = assertThrows(ToolCatalogException::class.java) { parse(text, trace) }
        assertEquals(text.take(60), reason, error.reason)
        assertNull(error.cause)
    }

    @Test
    fun readsAllTypedFieldsAndChineseWithoutChanges() {
        val result = parse(catalogPage())
        assertEquals(1L, result.total)
        val dto = result.records.single()
        assertEquals("二维码工作台", dto.name)
        assertNull(dto.icon)
        assertFalse(dto.requiresLogin)
        assertEquals(listOf("qr"), dto.keywords)
    }

    @Test
    fun acceptsEmptyAndBoundedUnknownFields() {
        val empty = catalogPage(emptyList())
        assertTrue(parse(empty).records.isEmpty())
        assertEquals(0L, parse(empty.dropLast(1) + """, "future":[null,true,false,1.25e-2,{},[]]}""").total)
    }

    @Test
    fun rejectsBusinessErrorsAndMissingEnvelopeFields() {
        listOf(
            "{}", "[]", "null", "", "   ",
            catalogPage().replace("\"code\":0", "\"code\":10008"),
            catalogPage().replace("\"message\":\"success\",", ""),
            catalogPage().replace("\"message\":\"success\"", "\"message\":null"),
            catalogPage().replace("\"data\":{", "\"wrongData\":{"),
            catalogPage().replace("\"records\":[", "\"wrongRecords\":["),
        ).forEach { fails(it) }
    }

    @Test
    fun traceMustBePresentValidAndMatchHeader() {
        fails(catalogPage(), trace = null)
        fails(catalogPage(), trace = "abcdef0123456789abcdef0123456789")
        fails(catalogPage().replace(CATALOG_TRACE, "uppercaseINVALID"), trace = "uppercaseINVALID")
        fails(catalogPage().replace("\"traceId\":\"$CATALOG_TRACE\",", ""))
    }

    @Test
    fun rejectsCoercionMissingAndNullMetadataFields() {
        val good = catalogPage()
        listOf(
            good.replace("\"requiresLogin\":false", "\"requiresLogin\":\"false\""),
            good.replace("\"isFeatured\":false", "\"isFeatured\":0"),
            good.replace("\"version\":1", "\"version\":\"1\""),
            good.replace("\"version\":1", "\"version\":1.0"),
            good.replace("\"version\":1", "\"version\":1e0"),
            good.replace("\"description\":\"生成二维码\",", ""),
            good.replace("\"icon\":null,", ""),
            good.replace("\"keywords\":[\"qr\"]", "\"keywords\":[123]"),
            good.replace("\"keywords\":[\"qr\"]", "\"keywords\":[null]"),
            good.replace("\"name\":\"二维码工作台\"", "\"name\":null"),
        ).forEach { fails(it) }
    }

    @Test
    fun rejectsInvalidDomainsAndDisabledRecords() {
        val good = catalogPage()
        listOf(
            good.replace("web_qr", "../escape"),
            good.replace("\"mode\":\"WEB\"", "\"mode\":\"FUTURE\""),
            good.replace("\"categoryCode\":\"QR\"", "\"categoryCode\":\"UNKNOWN\""),
            good.replace("\"status\":\"ENABLED\"", "\"status\":\"DISABLED\""),
            good.replace("\"version\":1", "\"version\":0"),
            good.replace("\"keywords\":[\"qr\"]", "\"keywords\":[\" \" ]"),
            good.replace("\"icon\":null", "\"icon\":\"/file\""),
        ).forEach { fails(it) }
    }

    @Test
    fun acceptsAllKnownModesButDoesNotFilterDuringParse() {
        listOf("LOCAL", "SERVER", "HYBRID", "WEB").forEach { mode ->
            assertEquals(mode, parse(catalogPage(listOf(catalogRecord(mode = mode)))).records.single().mode)
        }
    }

    @Test
    fun preservesIntegerPrecisionAndRejectsOverflow() {
        assertEquals(Long.MAX_VALUE, parse(catalogPage(total = Long.MAX_VALUE)).total)
        fails(catalogPage().replace("\"total\":1", "\"total\":9223372036854775808"))
        fails(catalogPage(total = -1))
        fails(catalogPage().replace("\"sortOrder\":0", "\"sortOrder\":2147483648"))
        assertEquals(Int.MIN_VALUE, parse(catalogPage().replace("\"sortOrder\":0", "\"sortOrder\":-2147483648")).records.single().sortOrder)
    }

    @Test
    fun rejectsNonStandardGrammarAndTrailingDocuments() {
        val good = catalogPage()
        listOf(
            good + "{}", good + "secret", good.replace("\"code\":0", "code:0"),
            good.replace("\"success\"", "'success'"), "/* comment */$good", "# comment\n$good",
            good.replace("false", "False"), good.replace("\"code\":0", "\"code\":01"),
            good.replace("\"code\":0", "\"code\":NaN"), good.replace("\"code\":0", "\"code\":+0"),
            good.replace("\"total\":1", "\"total\":1."), good.replace("\"total\":1", "\"total\":1e"),
            good.dropLast(1) + ",}", good.replace("[\"qr\"]", "[\"qr\",]"),
            good.dropLast(3), good.replace("\"qr\"", "\"q\nr\""), "\uFEFF$good",
        ).forEach { fails(it) }
    }

    @Test
    fun duplicateMembersIncludingEscapedNamesAreRejected() {
        val good = catalogPage()
        fails(good.replace("\"code\":0", "\"code\":0,\"code\":0"))
        fails(good.replace("\"code\":0", "\"code\":0,\"\\u0063ode\":10008"))
        fails(good.replace("\"icon\":null", "\"icon\":null,\"icon\":null"))
    }

    @Test
    fun decodesEscapesAndRejectsInvalidUnicode() {
        val good = catalogPage()
        val escaped = good.replace("二维码工作台", "\\u4e2d\\ud83d\\ude00\\n\\t\\b\\f\\r\\/\\\"\\\\")
        assertEquals("中😀\n\t\b\u000C\r/\"\\", parse(escaped).records.single().name)
        listOf("\\ud800", "\\udc00", "\\ud800x", "\\u12zz", "\\q", "\\'", "\\\n").forEach {
            fails(good.replace("二维码工作台", it))
        }
    }

    @Test
    fun enforcesDepthTokenStringAndKeywordBudgets() {
        val good = catalogPage()
        fails(good.dropLast(1) + ",\"unknown\":" + "[".repeat(17) + "null" + "]".repeat(17) + "}", ToolCatalogFailure.RESOURCE_LIMIT)
        fails(good.dropLast(1) + ",\"unknown\":[" + List(20_001) { "null" }.joinToString(",") + "]}", ToolCatalogFailure.RESOURCE_LIMIT)
        fails(good.dropLast(1) + ",\"unknown\":\"" + "x".repeat(4097) + "\"}", ToolCatalogFailure.RESOURCE_LIMIT)
        fails(good.replace("[\"qr\"]", "[" + List(65) { "\"qr\"" }.joinToString(",") + "]"), ToolCatalogFailure.RESOURCE_LIMIT)
        fails(good.replace("[\"qr\"]", "[\"" + "x".repeat(513) + "\"]"), ToolCatalogFailure.RESOURCE_LIMIT)
        fails(good.dropLast(1) + ",\"unknown\":" + "9".repeat(65) + "}", ToolCatalogFailure.RESOURCE_LIMIT)
    }

    @Test
    fun parserHonorsCancellationOrDeadlineCheckpoints() {
        val error = assertThrows(ToolCatalogException::class.java) {
            CatalogJsonReader(catalogPage()) { throw ToolCatalogException(ToolCatalogFailure.TIMEOUT) }.page(CATALOG_TRACE)
        }
        assertEquals(ToolCatalogFailure.TIMEOUT, error.reason)
    }
}
