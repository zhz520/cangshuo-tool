package com.cangshuo.toolbox.feature.webtools.data

import com.cangshuo.toolbox.core.database.CachedToolCatalogDao
import com.cangshuo.toolbox.core.database.CachedToolCatalogEntity
import com.cangshuo.toolbox.core.network.CatalogSnapshotCodec
import com.cangshuo.toolbox.core.network.model.ToolCatalogDto
import com.cangshuo.toolbox.feature.webtools.domain.CatalogCacheException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RoomWebToolCatalogCacheTest {
    private val dao = MemoryDao()
    private val dto = ToolCatalogDto("web_qr", "二维码", "生成", "QR", null, listOf("qr"), "WEB", false, "ENABLED", 1, 0, false)
    private fun cache(base: String = "https://example.test/api/v1") = RoomWebToolCatalogCache(dao, base) { 1000L }

    @Test
    fun reconstructingRepositoryRestoresSnapshotAndAllStoredFields() = runBlocking<Unit> {
        cache().write(listOf(dto))
        val row = requireNotNull(dao.row)
        assertEquals(1, row.singleton)
        assertEquals(1, row.formatVersion)
        assertEquals(1000L, row.writtenAt)
        assertTrue(Regex("[a-f0-9]{64}").matches(row.sourceKey))
        assertFalse(row.payload.contains("example.test"))
        assertEquals(listOf(dto), cache().read())
    }

    @Test
    fun missingCacheAndValidEmptyCacheRemainDistinct() = runBlocking<Unit> {
        assertNull(cache().read())
        cache().write(emptyList())
        assertEquals(emptyList<ToolCatalogDto>(), cache().read())
        assertEquals(1, dao.writes)
    }

    @Test
    fun differentSourceVersionTimestampSingletonOrChecksumIsNotRestored() = runBlocking<Unit> {
        cache().write(listOf(dto))
        val good = requireNotNull(dao.row)
        listOf(good.copy(formatVersion = 2), good.copy(singleton = 2), good.copy(writtenAt = -1),
            good.copy(payloadSha256 = "x"), good.copy(payloadSha256 = "0".repeat(64)), good.copy(sourceKey = "0".repeat(64))).forEach { row ->
            dao.row = row
            assertNull(cache().read())
        }
        dao.row = good
        assertNull(cache("https://other.test/api/v1").read())
        assertNull(cache("https://example.test/api/v2").read())
        assertEquals(listOf(dto), cache("https://example.test/api/v1/").read())
    }

    @Test
    fun syntacticallyValidDamagedPayloadIsDetectedByChecksum() = runBlocking<Unit> {
        cache().write(listOf(dto))
        dao.row = requireNotNull(dao.row).let { it.copy(payload = it.payload.replace("二维码", "另一名称")) }
        assertNull(cache().read())
    }

    @Test
    fun malformedOrOversizedPayloadHasOnlyFixedFailureDiagnostics() = runBlocking<Unit> {
        cache().write(listOf(dto))
        val good = requireNotNull(dao.row)
        listOf("sensitive-secret", " ".repeat(CatalogSnapshotCodec.MAX_BYTES + 1)).forEach { payload ->
            dao.row = good.copy(payload = payload)
            val error = failed { cache().read() }
            assertEquals("Catalog cache unavailable", error.message)
            assertNull(error.cause)
        }
    }

    @Test
    fun failedWritePreservesPreviousSnapshotWithoutClearing() = runBlocking<Unit> {
        cache().write(listOf(dto))
        val previous = dao.row
        dao.failWrite = true
        failed { cache().write(emptyList()) }
        assertSame(previous, dao.row)
        assertEquals(listOf(dto), cache().read())
    }

    @Test
    fun storageErrorIsSanitizedAndCancellationPropagates() = runBlocking<Unit> {
        dao.failRead = true
        assertNull(failed { cache().read() }.cause)
        dao.failRead = false
        dao.cancel = true
        try { cache().read(); fail("Expected cancellation") } catch (_: CancellationException) { }
        try { cache().write(listOf(dto)); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertNull(dao.row)
    }

    @Test
    fun invalidSnapshotIsRejectedBeforeAnyDatabaseWrite() = runBlocking<Unit> {
        failed { cache().write(listOf(dto.copy(mode = "LOCAL"))) }
        assertEquals(0, dao.writes)
        assertNull(dao.row)
    }

    private suspend fun failed(block: suspend () -> Any?): CatalogCacheException {
        try { block() } catch (error: CatalogCacheException) { return error }
        throw AssertionError("Expected cache failure")
    }

    private class MemoryDao : CachedToolCatalogDao {
        var row: CachedToolCatalogEntity? = null
        var writes = 0
        var failRead = false
        var failWrite = false
        var cancel = false
        override suspend fun read(sourceKey: String): CachedToolCatalogEntity? {
            if (cancel) throw CancellationException("cancelled")
            if (failRead) throw IOException("sensitive-error")
            return row
        }
        override suspend fun write(snapshot: CachedToolCatalogEntity) {
            if (cancel) throw CancellationException("cancelled")
            if (failWrite) throw IOException("sensitive-error")
            writes++
            row = snapshot
        }
    }
}
