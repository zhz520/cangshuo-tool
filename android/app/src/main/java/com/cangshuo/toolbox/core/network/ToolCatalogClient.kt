package com.cangshuo.toolbox.core.network

import com.cangshuo.toolbox.core.network.model.ToolCatalogDto
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Entire bounded catalog or an error. No page or filtered partial result is published. */
class ToolCatalogClient internal constructor(
    baseUrl: String,
    private val policy: ToolCatalogRequestPolicy,
    private val openConnection: (URL) -> HttpURLConnection,
) : ToolCatalogSource {
    constructor(baseUrl: String) : this(baseUrl, ToolCatalogRequestPolicy(), { it.openConnection() as HttpURLConnection })

    private val endpoint: String
    private val requests = Mutex()

    init {
        val base = try { URI(baseUrl) } catch (_: Exception) { throw IllegalArgumentException("Invalid catalog endpoint") }
        require(base.scheme in listOf("https", "http") && !base.host.isNullOrBlank() &&
            base.userInfo == null && base.query == null && base.fragment == null) { "Invalid catalog endpoint" }
        endpoint = base.toASCIIString().trimEnd('/') + "/tools"
    }

    override suspend fun fetchWebTools(): List<ToolCatalogDto> = fetchWebTools(3, 100)

    suspend fun fetchWebTools(maxPages: Int, pageSize: Int): List<ToolCatalogDto> {
        require(maxPages in 1..3 && pageSize in 1..100) { "Invalid catalog request bounds" }
        return requests.withLock {
            suspendCancellableCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                val deadline = AtomicReference<ScheduledFuture<*>?>()
                val guard = RequestGuard { continuation.context.ensureActive() }
                val worker = CoroutineScope(Dispatchers.IO).launch(start = CoroutineStart.LAZY) {
                    try {
                        val result = fetch(maxPages, pageSize, guard)
                        guard.checkpoint()
                        deadline.get()?.cancel(false)
                        if (finished.compareAndSet(false, true)) continuation.resume(result)
                    } catch (exception: Exception) {
                        val failure = when {
                            !continuation.isActive -> CancellationException("Catalog request cancelled")
                            guard.expired.get() || exception is SocketTimeoutException -> ToolCatalogException(ToolCatalogFailure.TIMEOUT)
                            exception is ToolCatalogException -> exception
                            exception is CancellationException -> exception
                            else -> ToolCatalogException(ToolCatalogFailure.NETWORK)
                        }
                        if (finished.compareAndSet(false, true)) continuation.resumeWithException(failure)
                    } finally {
                        deadline.get()?.cancel(false)
                        guard.connection.getAndSet(null)?.disconnect()
                    }
                }
                deadline.set(DEADLINES.schedule({
                    guard.expired.set(true)
                    if (finished.compareAndSet(false, true)) {
                        continuation.resumeWithException(ToolCatalogException(ToolCatalogFailure.TIMEOUT))
                    }
                    worker.cancel()
                    guard.disconnectAsync()
                }, policy.timeoutMillis, TimeUnit.MILLISECONDS))
                continuation.invokeOnCancellation {
                    finished.set(true)
                    deadline.get()?.cancel(false)
                    worker.cancel()
                    guard.disconnectAsync()
                }
                worker.start()
            }
        }
    }

    private fun fetch(maxPages: Int, pageSize: Int, guard: RequestGuard): List<ToolCatalogDto> {
        val found = ArrayList<ToolCatalogDto>()
        val codes = HashSet<String>()
        var expectedTotal: Long? = null
        var previous: ToolCatalogDto? = null
        for (page in 1..maxPages) {
            guard.checkpoint()
            val (body, traceId) = get(URL("$endpoint?page=$page&pageSize=$pageSize"), guard)
            val data = CatalogJsonReader(body, guard::checkpoint).page(traceId)
            if (data.page != page || data.pageSize != pageSize ||
                (expectedTotal != null && expectedTotal != data.total)) fail(ToolCatalogFailure.INCOMPLETE)
            if (data.total > maxPages.toLong() * pageSize) fail(ToolCatalogFailure.RESOURCE_LIMIT)
            expectedTotal = data.total
            val offset = (page - 1L) * pageSize
            val expectedSize = minOf(pageSize.toLong(), (data.total - offset).coerceAtLeast(0)).toInt()
            if (data.records.size != expectedSize) fail(ToolCatalogFailure.INCOMPLETE)
            for (dto in data.records) {
                if (!codes.add(dto.code)) fail(ToolCatalogFailure.INCOMPLETE)
                previous?.let { last ->
                    if (last.sortOrder > dto.sortOrder ||
                        (last.sortOrder == dto.sortOrder && last.code > dto.code)) fail(ToolCatalogFailure.INCOMPLETE)
                }
                previous = dto
                if (dto.mode == "WEB") found += dto
            }
            if (offset + data.records.size == data.total) return found
        }
        fail(ToolCatalogFailure.INCOMPLETE)
    }

    private fun get(url: URL, guard: RequestGuard): Pair<String, String?> {
        val connection = openConnection(url)
        guard.connection.set(connection)
        try {
            guard.checkpoint()
            connection.apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                useCaches = false
                connectTimeout = policy.connectTimeoutMillis
                readTimeout = policy.readTimeoutMillis
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Encoding", "identity")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) fail(ToolCatalogFailure.HTTP)
            guard.checkpoint()
            val type = connection.contentType?.split(';') ?: fail(ToolCatalogFailure.INVALID_RESPONSE)
            if (!type.first().trim().equals("application/json", ignoreCase = true)) fail(ToolCatalogFailure.INVALID_RESPONSE)
            for (parameter in type.drop(1)) {
                val parts = parameter.trim().split('=', limit = 2)
                if (parts.first().equals("charset", ignoreCase = true) &&
                    (parts.size != 2 || parts[1].trim().trim('"').lowercase() !in listOf("utf-8", "utf8"))) {
                    fail(ToolCatalogFailure.INVALID_RESPONSE)
                }
            }
            if (connection.contentEncoding?.let { !it.equals("identity", ignoreCase = true) } == true) {
                fail(ToolCatalogFailure.INVALID_RESPONSE)
            }
            val length = connection.contentLengthLong
            val remaining = minOf(policy.maxPageBytes, policy.maxTotalBytes - guard.bytes)
            if (length > remaining || length < -1) fail(ToolCatalogFailure.RESOURCE_LIMIT)
            val output = ByteArrayOutputStream(minOf(4096, remaining.coerceAtLeast(0)))
            val buffer = ByteArray(8192)
            connection.inputStream.use { input ->
                while (true) {
                    guard.checkpoint()
                    // Read at most one byte beyond the allowance, and never append that byte.
                    val count = input.read(buffer, 0, minOf(buffer.size, remaining - output.size() + 1))
                    guard.checkpoint()
                    if (count < 0) break
                    if (count == 0) continue
                    if (count > remaining - output.size()) fail(ToolCatalogFailure.RESOURCE_LIMIT)
                    output.write(buffer, 0, count)
                }
            }
            if (length >= 0 && output.size().toLong() != length) fail(ToolCatalogFailure.INCOMPLETE)
            guard.bytes += output.size()
            val body = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString()
            } catch (_: CharacterCodingException) { fail(ToolCatalogFailure.INVALID_RESPONSE) }
            return body to connection.getHeaderField("X-Trace-Id")
        } finally {
            guard.connection.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    private inner class RequestGuard(private val checkCancellation: () -> Unit) {
        val expired = AtomicBoolean(false)
        val connection = AtomicReference<HttpURLConnection?>()
        private val started = System.nanoTime()
        var bytes = 0
        // Disconnect may block in a platform provider. Never run it on the caller or deadline thread.
        fun disconnectAsync() {
            connection.get()?.let { active ->
                CoroutineScope(Dispatchers.IO).launch {
                    try { active.disconnect() } catch (_: Exception) { /* Best-effort cleanup. */ }
                }
            }
        }
        fun checkpoint() {
            checkCancellation()
            if (expired.get() || System.nanoTime() - started >= TimeUnit.MILLISECONDS.toNanos(policy.timeoutMillis)) {
                fail(ToolCatalogFailure.TIMEOUT)
            }
        }
    }

    private fun fail(reason: ToolCatalogFailure): Nothing = throw ToolCatalogException(reason)

    private companion object {
        val DEADLINES = ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, "tool-catalog-deadline").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
    }
}
