package com.cangshuo.toolbox.feature.webtools.domain

import com.cangshuo.toolbox.core.network.ToolCatalogException
import com.cangshuo.toolbox.core.network.ToolCatalogFailure
import com.cangshuo.toolbox.core.network.ToolCatalogSource
import com.cangshuo.toolbox.core.network.model.ToolCatalogDto
import com.cangshuo.toolbox.core.tool.ToolRegistry
import com.cangshuo.toolbox.core.tool.ToolRegistryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CachedWebToolCatalogUseCasesTest {
    private val bundled = ToolRegistry(emptyList())
    private val store = ToolRegistryStore(bundled)
    private val cache = MemoryCache()
    private val dto = ToolCatalogDto("web_qr", "二维码", "", "QR", null, emptyList(), "WEB", false, "ENABLED", 1, 0, false)

    @Test
    fun offlineRestartRestoresLastSuccessAndNetworkFailureKeepsIt() = runBlocking<Unit> {
        assertTrue(RefreshWebToolCatalogUseCase(ToolCatalogSource { listOf(dto) }, store, cache)())
        val restarted = ToolRegistryStore(bundled)
        assertTrue(RestoreWebToolCatalogUseCase(cache, restarted)())
        val restored = restarted.current
        assertNotNull(restored.find("web_qr"))
        assertFalse(RefreshWebToolCatalogUseCase(ToolCatalogSource { throw ToolCatalogException(ToolCatalogFailure.NETWORK) }, restarted, cache)())
        assertSame(restored, restarted.current)
        assertEquals(1, cache.writes)
    }

    @Test
    fun laterNetworkSuccessReplacesCachedMetadataAndSavedSnapshot() = runBlocking<Unit> {
        cache.records = listOf(dto)
        assertTrue(RestoreWebToolCatalogUseCase(cache, store)())
        val latest = dto.copy(name = "新版")
        assertTrue(RefreshWebToolCatalogUseCase(ToolCatalogSource { listOf(latest) }, store, cache)())
        assertEquals("新版", store.current.find("web_qr")?.metadata?.name)
        assertEquals(listOf(latest), cache.records)
    }

    @Test
    fun successfulEmptyResultIsPersistedAndDoesNotResurrectRemovedTools() = runBlocking<Unit> {
        cache.records = listOf(dto)
        RestoreWebToolCatalogUseCase(cache, store)()
        assertTrue(RefreshWebToolCatalogUseCase(ToolCatalogSource { emptyList() }, store, cache)())
        assertSame(bundled, store.current)
        assertEquals(emptyList<ToolCatalogDto>(), cache.records)
        val restarted = ToolRegistryStore(bundled)
        assertTrue(RestoreWebToolCatalogUseCase(cache, restarted)())
        assertSame(bundled, restarted.current)
    }

    @Test
    fun cacheWriteFailureDoesNotDiscardValidNetworkResult() = runBlocking<Unit> {
        cache.failWrite = true
        assertTrue(RefreshWebToolCatalogUseCase(ToolCatalogSource { listOf(dto) }, store, cache)())
        assertNotNull(store.current.find("web_qr"))
        assertNull(cache.records)
    }

    @Test
    fun missingDamagedOrInvalidCacheDoesNotReplaceCurrentSnapshot() = runBlocking<Unit> {
        val previous = store.current
        assertFalse(RestoreWebToolCatalogUseCase(cache, store)())
        cache.failRead = true
        assertFalse(RestoreWebToolCatalogUseCase(cache, store)())
        cache.failRead = false
        listOf(listOf(dto.copy(mode = "LOCAL")), listOf(dto, dto), listOf(dto.copy(code = "../invalid"))).forEach {
            cache.records = it
            assertFalse(RestoreWebToolCatalogUseCase(cache, store)())
            assertSame(previous, store.current)
        }
    }

    @Test
    fun cancellationDuringCacheReadOrWritePropagatesWithoutPublishing() = runBlocking<Unit> {
        cache.cancel = true
        try { RestoreWebToolCatalogUseCase(cache, store)(); fail("Expected cancellation") } catch (_: CancellationException) { }
        try { RefreshWebToolCatalogUseCase(ToolCatalogSource { listOf(dto) }, store, cache)(); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertSame(bundled, store.current)
        assertNull(cache.records)
    }

    @Test
    fun invalidNetworkResultNeverReachesCache() = runBlocking<Unit> {
        assertFalse(RefreshWebToolCatalogUseCase(ToolCatalogSource { listOf(dto, dto) }, store, cache)())
        assertEquals(0, cache.writes)
        assertSame(bundled, store.current)
    }

    private class MemoryCache : WebToolCatalogCache {
        var records: List<ToolCatalogDto>? = null
        var writes = 0
        var failWrite = false
        var failRead = false
        var cancel = false
        override suspend fun read(): List<ToolCatalogDto>? {
            if (cancel) throw CancellationException("cancelled")
            if (failRead) throw CatalogCacheException()
            return records
        }
        override suspend fun write(records: List<ToolCatalogDto>) {
            if (cancel) throw CancellationException("cancelled")
            if (failWrite) throw CatalogCacheException()
            writes++
            this.records = records
        }
    }
}
