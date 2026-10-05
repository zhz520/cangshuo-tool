package com.cangshuo.toolbox.core.network

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ToolCatalogClientTest {
    @Test
    fun countsAllModesBeforeFilteringAndFetchesEveryPage() = runBlocking<Unit> {
        val first = Response(catalogPage(listOf(catalogRecord("alpha", "LOCAL")), pageSize = 1, total = 2))
        val second = Response(catalogPage(listOf(catalogRecord("beta")), page = 2, pageSize = 1, total = 2))
        val urls = CopyOnWriteArrayList<URL>()
        val client = client(listOf(first, second), urls = urls)
        assertEquals(listOf("beta"), client.fetchWebTools(2, 1).map { it.code })
        assertEquals(listOf("page=1&pageSize=1", "page=2&pageSize=1"), urls.map { it.query })
        listOf(first, second).forEach {
            assertTrue(it.closed.await(1, TimeUnit.SECONDS))
            assertTrue(it.disconnected)
            assertFalse(it.instanceFollowRedirects)
            assertFalse(it.useCaches)
            assertEquals("identity", it.getRequestProperty("Accept-Encoding"))
        }
    }

    @Test
    fun acceptsCompleteEmptyCatalog() = runBlocking<Unit> {
        assertTrue(client(listOf(Response(catalogPage(emptyList())))).fetchWebTools().isEmpty())
    }

    @Test
    fun rejectsWrongPaginationChangingTotalsMissingRowsAndDuplicateCodes() = runBlocking<Unit> {
        val first = catalogPage(listOf(catalogRecord("alpha")), pageSize = 1, total = 2)
        val second = catalogPage(listOf(catalogRecord("beta")), page = 2, pageSize = 1, total = 2)
        val pairs = listOf(
            first.replace("\"page\":1", "\"page\":2") to second,
            first.replace("\"pageSize\":1", "\"pageSize\":2") to second,
            first to second.replace("\"total\":2", "\"total\":1"),
            first to catalogPage(emptyList(), page = 2, pageSize = 1, total = 2),
            first to second.replace("beta", "alpha"),
            first to second.replace("beta", "aardvark"),
        )
        pairs.forEach { (a, b) -> fails(client(listOf(Response(a), Response(b))), ToolCatalogFailure.INCOMPLETE, 2, 1) }
    }

    @Test
    fun rejectsDescendingSortOrderWithinAndAcrossPages() = runBlocking<Unit> {
        val a = catalogRecord("alpha").replace("\"sortOrder\":0", "\"sortOrder\":1")
        val b = catalogRecord("beta")
        fails(client(listOf(Response(catalogPage(listOf(a, b))))), ToolCatalogFailure.INCOMPLETE)
        fails(client(listOf(
            Response(catalogPage(listOf(a), pageSize = 1, total = 2)),
            Response(catalogPage(listOf(b), page = 2, pageSize = 1, total = 2)),
        )), ToolCatalogFailure.INCOMPLETE, 2, 1)
    }

    @Test
    fun refusesCatalogLargerThanPageCapIncludingLongTotals() = runBlocking<Unit> {
        listOf(301L, Long.MAX_VALUE).forEach { total ->
            val urls = CopyOnWriteArrayList<URL>()
            fails(client(listOf(Response(catalogPage(total = total))), urls = urls), ToolCatalogFailure.RESOURCE_LIMIT)
            assertEquals(1, urls.size)
        }
    }

    @Test
    fun rejectsInvalidSecondPageInsteadOfReturningFirstPage() = runBlocking<Unit> {
        fails(client(listOf(
            Response(catalogPage(listOf(catalogRecord("alpha")), pageSize = 1, total = 2)),
            Response("{\"data\":null}"),
        )), ToolCatalogFailure.INVALID_RESPONSE, 2, 1)
    }

    @Test
    fun advertisedOversizeIsRejectedBeforeOpeningBody() = runBlocking<Unit> {
        val response = Response(catalogPage(), length = 1_000_001)
        fails(client(listOf(response)), ToolCatalogFailure.RESOURCE_LIMIT)
        assertFalse(response.opened)
        assertTrue(response.disconnected)
    }

    @Test
    fun unknownLengthReadsOnlyBudgetPlusOneByteAndClosesStream() = runBlocking<Unit> {
        val response = Response("x".repeat(50_000), length = -1)
        fails(client(listOf(response), ToolCatalogRequestPolicy(maxPageBytes = 1000)), ToolCatalogFailure.RESOURCE_LIMIT)
        assertEquals(1001, response.bytesRead.get())
        assertTrue(response.closed.await(1, TimeUnit.SECONDS))
        assertTrue(response.disconnected)
    }

    @Test
    fun byteBudgetIncludesMultibyteUtf8AndAcceptsExactLimit() = runBlocking<Unit> {
        val body = catalogPage()
        val size = body.toByteArray(Charsets.UTF_8).size
        assertTrue(size > body.length)
        assertEquals(1, client(listOf(Response(body, length = -1)), ToolCatalogRequestPolicy(maxPageBytes = size)).fetchWebTools().size)
        fails(client(listOf(Response(body, length = -1)), ToolCatalogRequestPolicy(maxPageBytes = size - 1)), ToolCatalogFailure.RESOURCE_LIMIT)
    }

    @Test
    fun aggregateBudgetIsSharedBetweenPages() = runBlocking<Unit> {
        val a = catalogPage(listOf(catalogRecord("alpha")), pageSize = 1, total = 2)
        val b = catalogPage(listOf(catalogRecord("beta")), page = 2, pageSize = 1, total = 2)
        val second = Response(b)
        val limit = a.toByteArray().size + b.toByteArray().size - 1
        fails(client(listOf(Response(a), second), ToolCatalogRequestPolicy(maxTotalBytes = limit)), ToolCatalogFailure.RESOURCE_LIMIT, 2, 1)
        assertFalse(second.opened)
        assertTrue(second.disconnected)
    }

    @Test
    fun rejectsTruncatedOrMisreportedBodiesAndMalformedUtf8() = runBlocking<Unit> {
        val body = catalogPage()
        fails(client(listOf(Response(body, length = body.toByteArray().size.toLong() + 1))), ToolCatalogFailure.INCOMPLETE)
        fails(client(listOf(Response(body, length = body.toByteArray().size.toLong() - 1))), ToolCatalogFailure.INCOMPLETE)
        val malformed = Response(body, length = -1, stream = ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
        fails(client(listOf(malformed)), ToolCatalogFailure.INVALID_RESPONSE)
    }

    @Test
    fun rejectsHttpErrorsWithoutReadingErrorBodiesOrFollowingRedirects() = runBlocking<Unit> {
        listOf(204, 301, 302, 401, 429, 500).forEach { code ->
            val response = Response("sensitive-error-body", status = code)
            val error = fails(client(listOf(response)), ToolCatalogFailure.HTTP)
            assertFalse(error.message.orEmpty().contains("sensitive"))
            assertFalse(response.opened)
            assertFalse(response.instanceFollowRedirects)
            assertTrue(response.disconnected)
        }
    }

    @Test
    fun validatesContentTypeEncodingAndTrace() = runBlocking<Unit> {
        listOf(null, "text/html", "application/json; charset=GBK", "application/json; charset").forEach { type ->
            fails(client(listOf(Response(catalogPage(), type = type))), ToolCatalogFailure.INVALID_RESPONSE)
        }
        fails(client(listOf(Response(catalogPage(), encoding = "gzip"))), ToolCatalogFailure.INVALID_RESPONSE)
        fails(client(listOf(Response(catalogPage(), trace = null))), ToolCatalogFailure.INVALID_RESPONSE)
        fails(client(listOf(Response(catalogPage(), trace = "f".repeat(32)))), ToolCatalogFailure.INVALID_RESPONSE)
        assertEquals(1, client(listOf(Response(catalogPage(), type = "Application/JSON; charset=\"UTF-8\""))).fetchWebTools().size)
    }

    @Test
    fun unexpectedNetworkErrorsExposeOnlyFixedDiagnostics() = runBlocking<Unit> {
        val client = ToolCatalogClient("http://localhost/api/v1", ToolCatalogRequestPolicy()) {
            throw IOException("secret-token-and-response")
        }
        val error = fails(client, ToolCatalogFailure.NETWORK)
        assertEquals("Catalog request failed: NETWORK", error.message)
        assertNull(error.cause)
    }

    @Test
    fun rejectsInvalidEndpointAndRequestBoundsBeforeNetwork() = runBlocking<Unit> {
        listOf("file:///local", "http://user:secret@localhost/api", "http://localhost/api?q=secret", "http://localhost/api#fragment").forEach {
            assertThrows(IllegalArgumentException::class.java) { ToolCatalogClient(it) }
        }
        val client = client(emptyList())
        listOf(0 to 100, 4 to 100, 1 to 0, 1 to 101).forEach { (pages, size) ->
            try { client.fetchWebTools(pages, size); fail("Expected bounds rejection") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test
    fun totalDeadlineStopsSlowDripEvenWhenEachReadKeepsProgressing() = runBlocking<Unit> {
        val bytes = catalogPage().toByteArray()
        val drip = object : InputStream() {
            var position = 0
            override fun read(): Int { Thread.sleep(10); return if (position < bytes.size) bytes[position++].toInt() and 255 else -1 }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val byte = read()
                if (byte < 0) return -1
                buffer[offset] = byte.toByte()
                return 1
            }
        }
        val response = Response("", length = -1, stream = drip)
        withTimeout(3000) {
            fails(client(listOf(response), ToolCatalogRequestPolicy(timeoutMillis = 200)), ToolCatalogFailure.TIMEOUT)
        }
        assertTrue(response.closed.await(2, TimeUnit.SECONDS))
        assertTrue(response.bytesRead.get() < bytes.size)
    }

    @Test
    fun cancellationReturnsWhileBlockedReadIsDisconnected() = runBlocking<Unit> {
        val started = CountDownLatch(1)
        val released = CountDownLatch(1)
        val blocked = object : InputStream() {
            override fun read(): Int { started.countDown(); released.await(3, TimeUnit.SECONDS); return -1 }
            override fun close() { released.countDown() }
        }
        val response = Response("", length = -1, stream = blocked)
        val job = async { client(listOf(response)).fetchWebTools() }
        try {
            // Start the child before waiting on the real IO worker.
            kotlinx.coroutines.yield()
            assertTrue(started.await(2, TimeUnit.SECONDS))
            withTimeout(1000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
            assertTrue(response.closed.await(2, TimeUnit.SECONDS))
        } finally { released.countDown() }
    }

    @Test
    fun realLoopbackHttpReadsFixedAndChunkedUtf8Bodies() = runBlocking<Unit> {
        listOf(false, true).forEach { chunked ->
            withServer({ exchange -> send(exchange, catalogPage(), chunked) }) { base ->
                assertEquals("二维码工作台", ToolCatalogClient(base).fetchWebTools().single().name)
            }
        }
    }

    @Test
    fun realLoopbackHttpReadTimeoutFallsBackToTypedFailure() = runBlocking<Unit> {
        val release = CountDownLatch(1)
        try {
            withServer({ exchange ->
                headers(exchange)
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use { output ->
                    output.write('{'.code)
                    output.flush()
                    release.await(2, TimeUnit.SECONDS)
                }
            }) { base ->
                val client = ToolCatalogClient(base, ToolCatalogRequestPolicy(readTimeoutMillis = 100, timeoutMillis = 1500)) {
                    it.openConnection() as HttpURLConnection
                }
                try { withTimeout(3000) { fails(client, ToolCatalogFailure.TIMEOUT) } } finally { release.countDown() }
            }
        } finally { release.countDown() }
    }

    private fun client(
        responses: List<Response>,
        policy: ToolCatalogRequestPolicy = ToolCatalogRequestPolicy(),
        urls: MutableList<URL> = CopyOnWriteArrayList(),
    ): ToolCatalogClient {
        val next = AtomicInteger()
        return ToolCatalogClient("http://localhost/api/v1", policy) { url ->
            urls += url
            responses[next.getAndIncrement()]
        }
    }

    private suspend fun fails(client: ToolCatalogClient, reason: ToolCatalogFailure, pages: Int = 3, size: Int = 100): ToolCatalogException {
        try { client.fetchWebTools(pages, size) } catch (error: ToolCatalogException) {
            assertEquals(reason, error.reason)
            assertNull(error.cause)
            return error
        }
        throw AssertionError("Expected catalog failure: $reason")
    }

    private suspend fun withServer(handler: (HttpExchange) -> Unit, block: suspend (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/tools") { exchange -> try { handler(exchange) } finally { exchange.close() } }
        server.start()
        try { block("http://127.0.0.1:${server.address.port}/api/v1") } finally { server.stop(0) }
    }

    private fun headers(exchange: HttpExchange) {
        exchange.responseHeaders.set("Content-Type", "application/json; charset=UTF-8")
        exchange.responseHeaders.set("X-Trace-Id", CATALOG_TRACE)
    }

    private fun send(exchange: HttpExchange, body: String, chunked: Boolean) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        headers(exchange)
        exchange.sendResponseHeaders(200, if (chunked) 0 else bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private class Response(
        body: String,
        private val length: Long = body.toByteArray(Charsets.UTF_8).size.toLong(),
        private val status: Int = 200,
        private val type: String? = "application/json; charset=UTF-8",
        private val encoding: String? = null,
        private val trace: String? = CATALOG_TRACE,
        private val stream: InputStream = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)),
    ) : HttpURLConnection(URL("http://localhost/api/v1/tools")) {
        @Volatile var opened = false
        @Volatile var disconnected = false
        val bytesRead = AtomicInteger()
        val closed = CountDownLatch(1)
        override fun connect() { }
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true; stream.close() }
        override fun getResponseCode() = status
        override fun getContentType() = type
        override fun getContentEncoding() = encoding
        override fun getContentLengthLong() = length
        override fun getHeaderField(name: String?) = if (name.equals("X-Trace-Id", ignoreCase = true)) trace else null
        override fun getInputStream(): InputStream {
            opened = true
            return object : InputStream() {
                override fun read(): Int = stream.read().also { if (it >= 0) bytesRead.incrementAndGet() }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    stream.read(buffer, offset, length).also { if (it > 0) bytesRead.addAndGet(it) }
                override fun close() { try { stream.close() } finally { closed.countDown() } }
            }
        }
    }
}
