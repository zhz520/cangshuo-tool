package com.cangshuo.toolbox.feature.webtools.domain

import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.network.ToolCatalogException
import com.cangshuo.toolbox.core.network.ToolCatalogFailure
import com.cangshuo.toolbox.core.network.ToolCatalogSource
import com.cangshuo.toolbox.core.network.model.ToolCatalogDto
import com.cangshuo.toolbox.core.tool.ToolRegistry
import com.cangshuo.toolbox.core.tool.ToolRegistryStore
import com.cangshuo.toolbox.feature.webtools.WebToolDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class RefreshWebToolCatalogUseCaseTest {
    private val bundled = ToolRegistry(listOf(definition("bundled")))
    private val store = ToolRegistryStore(bundled).apply { replaceRemoteWebTools(listOf(definition("previous"))) }

    @Test
    fun everyExpectedFailurePreservesIdenticalPreviousSnapshot() = runBlocking<Unit> {
        val previous = store.current
        ToolCatalogFailure.entries.forEach { failure ->
            assertFalse(RefreshWebToolCatalogUseCase(ToolCatalogSource { throw ToolCatalogException(failure) }, store)())
            assertSame(previous, store.current)
        }
    }

    @Test
    fun successfulRefreshReplacesRemoteEntriesAndKeepsBundledDefinition() = runBlocking<Unit> {
        assertTrue(RefreshWebToolCatalogUseCase(ToolCatalogSource { listOf(dto("bundled"), dto("next")) }, store)())
        assertNull(store.current.find("previous"))
        assertNotNull(store.current.find("next"))
        assertSame(bundled.find("bundled"), store.current.find("bundled"))
    }

    @Test
    fun genuinelyEmptySuccessfulCatalogRestoresBundledSnapshot() = runBlocking<Unit> {
        assertTrue(RefreshWebToolCatalogUseCase(ToolCatalogSource { emptyList() }, store)())
        assertSame(bundled, store.current)
    }

    @Test
    fun invalidRecordAfterValidRecordPublishesNothing() = runBlocking<Unit> {
        val previous = store.current
        val records = listOf(dto("next"), dto("../../invalid"))
        assertFalse(RefreshWebToolCatalogUseCase(ToolCatalogSource { records }, store)())
        assertSame(previous, store.current)
        assertNull(store.current.find("next"))
    }

    @Test
    fun nonWebOrDisabledRecordCannotReplaceCatalog() = runBlocking<Unit> {
        val previous = store.current
        listOf(dto("next").copy(mode = "LOCAL"), dto("next").copy(status = "DISABLED")).forEach { record ->
            assertFalse(RefreshWebToolCatalogUseCase(ToolCatalogSource { listOf(record) }, store)())
            assertSame(previous, store.current)
        }
    }

    @Test
    fun cancellationPropagatesAndNeverPublishes() = runBlocking<Unit> {
        val previous = store.current
        try {
            RefreshWebToolCatalogUseCase(ToolCatalogSource { throw CancellationException("cancelled") }, store)()
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertSame(previous, store.current)

        val job = async {
            RefreshWebToolCatalogUseCase(ToolCatalogSource {
                currentCoroutineContext().cancel()
                listOf(dto("next"))
            }, store)()
        }
        job.join()
        assertTrue(job.isCancelled)
        assertSame(previous, store.current)
    }

    @Test
    fun catalogRemainsReadableUntilEntireFetchCompletes() = runBlocking<Unit> {
        val previous = store.current
        val response = CompletableDeferred<List<ToolCatalogDto>>()
        val job = async { RefreshWebToolCatalogUseCase(ToolCatalogSource { response.await() }, store)() }
        yield()
        assertSame(previous, store.current)
        assertNotNull(store.current.find("previous"))
        response.complete(listOf(dto("next")))
        assertTrue(job.await())
        assertNull(store.current.find("previous"))
        assertNotNull(store.current.find("next"))
    }

    private fun definition(code: String) = WebToolDefinition(ToolMetadata(code, code, "", ToolCategory.QR, ToolMode.WEB))
    private fun dto(code: String) = ToolCatalogDto(code, code, "", "QR", null, emptyList(), "WEB", false, "ENABLED", 1, 0, false)
}
