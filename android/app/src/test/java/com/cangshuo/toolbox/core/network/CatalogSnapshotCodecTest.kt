package com.cangshuo.toolbox.core.network

import com.cangshuo.toolbox.core.network.model.ToolCatalogDto
import org.junit.Assert.*
import org.junit.Test

class CatalogSnapshotCodecTest {
    private val dto = ToolCatalogDto("web_qr", "中😀\"\\\n", "说明", "QR", null, listOf("qr", "中文"), "WEB", false, "ENABLED", 1, 0, true)

    @Test
    fun roundTripsEveryFieldIncludingUnicodeEscapesNullAndBoolean() {
        assertEquals(listOf(dto), CatalogSnapshotCodec.decode(CatalogSnapshotCodec.encode(listOf(dto))))
        assertEquals(listOf(dto.copy(icon = "qr", requiresLogin = true, sortOrder = -1)),
            CatalogSnapshotCodec.decode(CatalogSnapshotCodec.encode(listOf(dto.copy(icon = "qr", requiresLogin = true, sortOrder = -1)))))
    }

    @Test
    fun emptySnapshotIsDifferentFromMissingCache() {
        assertEquals("{\"records\":[]}", CatalogSnapshotCodec.encode(emptyList()))
        assertTrue(CatalogSnapshotCodec.decode("{\"records\":[]}").isEmpty())
    }

    @Test
    fun snapshotSupportsMoreThanOneNetworkPageAndMaximumCatalogSize() {
        val records = List(300) { dto.copy(code = "web_${it.toString().padStart(3, '0')}") }
        assertEquals(records, CatalogSnapshotCodec.decode(CatalogSnapshotCodec.encode(records)))
        assertThrows(ToolCatalogException::class.java) { CatalogSnapshotCodec.encode(records + dto.copy(code = "web_999")) }
    }

    @Test
    fun rejectsNonWebDisabledUnknownMetadataDuplicateAndOutOfOrderRecords() {
        listOf(
            listOf(dto.copy(mode = "LOCAL")), listOf(dto.copy(status = "DISABLED")), listOf(dto.copy(categoryCode = "UNKNOWN")),
            listOf(dto, dto), listOf(dto, dto.copy(code = "alpha")), listOf(dto.copy(name = "\ud800")),
        ).forEach { records -> assertThrows(ToolCatalogException::class.java) { CatalogSnapshotCodec.encode(records) } }
    }

    @Test
    fun corruptedOrNonStandardSnapshotIsRejected() {
        val good = CatalogSnapshotCodec.encode(listOf(dto))
        listOf("", "[]", "{}", good.dropLast(1), good + "{}", good.replace("false", "\"false\""),
            good.replace("\"version\":1", "\"version\":1e0"), good.replace("\"icon\":null", "\"icon\":null,\"icon\":null"),
            good.replace("\"mode\":\"WEB\"", "\"mode\":\"LOCAL\"")).forEach {
            assertThrows(ToolCatalogException::class.java) { CatalogSnapshotCodec.decode(it) }
        }
    }

    @Test
    fun exactUtf8ByteLimitIsAcceptedAndOneExtraByteIsRejected() {
        val good = CatalogSnapshotCodec.encode(listOf(dto))
        val exact = good + " ".repeat(CatalogSnapshotCodec.MAX_BYTES - good.toByteArray(Charsets.UTF_8).size)
        assertEquals(listOf(dto), CatalogSnapshotCodec.decode(exact))
        val error = assertThrows(ToolCatalogException::class.java) { CatalogSnapshotCodec.decode(exact + " ") }
        assertEquals(ToolCatalogFailure.RESOURCE_LIMIT, error.reason)
    }

    @Test
    fun writingStopsAtBudgetInsteadOfBuildingCompleteOversizedPayload() {
        val records = List(300) { dto.copy(code = "web_${it.toString().padStart(3, '0')}", keywords = List(64) { "中".repeat(512) }) }
        val error = assertThrows(ToolCatalogException::class.java) { CatalogSnapshotCodec.encode(records) }
        assertEquals(ToolCatalogFailure.RESOURCE_LIMIT, error.reason)
    }

    @Test
    fun cancellationCheckpointsAndKnownDigestWork() {
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(expected, CatalogSnapshotCodec.digest(""))
        assertThrows(ToolCatalogException::class.java) {
            CatalogSnapshotCodec.encode(listOf(dto)) { throw ToolCatalogException(ToolCatalogFailure.TIMEOUT) }
        }
        assertThrows(ToolCatalogException::class.java) {
            CatalogSnapshotCodec.decode("{\"records\":[]}") { throw ToolCatalogException(ToolCatalogFailure.TIMEOUT) }
        }
    }
}
