package com.cangshuo.toolbox.feature.httpstatus.data

import android.os.SystemClock
import android.security.NetworkSecurityPolicy
import com.cangshuo.toolbox.feature.httpstatus.domain.*
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val USER_AGENT = "CangshuoToolbox/0.1"

class OkHttpStatusRepository : HttpStatusRepository {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val lock = Mutex()
    private var lastStart = Long.MIN_VALUE

    override suspend fun execute(request: HttpProbeRequest): HttpProbeResult {
        if (!lock.tryLock()) throw HttpProbeFailure(HttpError.BUSY)
        try {
            val now = SystemClock.elapsedRealtime()
            if (lastStart != Long.MIN_VALUE && now - lastStart < 1_000L) throw HttpProbeFailure(HttpError.BUSY)
            lastStart = now
            requireCleartext(request.target)
            return withTimeout(30_000) { probe(request) }
        } catch (e: TimeoutCancellationException) {
            throw HttpProbeFailure(HttpError.TIMEOUT)
        } finally {
            lock.unlock()
        }
    }

    private suspend fun probe(request: HttpProbeRequest): HttpProbeResult {
        var current = request.target
        var method = request.method
        val redirects = mutableListOf<HttpRedirect>()
        val visited = mutableSetOf(current.url)
        val started = SystemClock.elapsedRealtime()
        while (true) {
            val url = try { HttpUrl.get(current.url) } catch (_: IllegalArgumentException) { throw HttpProbeFailure(HttpError.INVALID_URL) }
            val call = client.newCall(Request.Builder().url(url).method(method.name, null).header("User-Agent", USER_AGENT).build())
            val response = try { call.await() } catch (e: IOException) { throw mapError(e) }
            try {
                val location = response.header("Location")
                if (request.followRedirects && location != null && HttpRedirects.isRedirect(response.code())) {
                    if (redirects.size >= HttpRedirects.MAX_REDIRECTS) throw HttpProbeFailure(HttpError.TOO_MANY_REDIRECTS)
                    val target = HttpTargets.validate(HttpRedirects.resolve(current.url, location))
                    requireCleartext(target)
                    redirects += HttpRedirect(current.url, response.code(), target.url)
                    if (!visited.add(target.url)) throw HttpProbeFailure(HttpError.REDIRECT_LOOP)
                    method = HttpRedirects.nextMethod(response.code(), method)
                    current = target
                    continue
                }
                return buildResult(method, current, response, redirects, started)
            } finally {
                response.close()
            }
        }
    }

    private fun buildResult(method: HttpMethod, target: HttpTarget, response: Response,
        redirects: List<HttpRedirect>, started: Long): HttpProbeResult {
        val raw = ArrayList<HttpHeader>()
        for (name in response.headers().names()) for (value in response.headers(name)) raw += HttpHeader(name, value)
        return HttpProbeResult(
            finalUrl = target.url,
            status = response.code(),
            statusText = HttpHeaderPolicy.clean(response.message(), 128),
            httpVersion = response.protocol().toString(),
            elapsedMs = SystemClock.elapsedRealtime() - started,
            contentType = HttpHeaderPolicy.clean(response.header("Content-Type").orEmpty(), 256),
            contentLength = HttpHeaderPolicy.parseContentLength(response.header("Content-Length")),
            server = HttpHeaderPolicy.clean(response.header("Server").orEmpty(), 128),
            tlsVersion = response.handshake()?.tlsVersion()?.javaName(),
            tlsCipher = response.handshake()?.cipherSuite()?.javaName(),
            bodyNotRead = method == HttpMethod.GET,
            headers = HttpHeaderPolicy.sanitize(raw),
            redirects = redirects,
        )
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }

    private fun requireCleartext(target: HttpTarget) {
        if (target.secure) return
        val allowed = runCatching { NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(target.host) }.getOrDefault(false)
        if (!allowed) throw HttpProbeFailure(HttpError.CLEARTEXT_BLOCKED)
    }

    private fun mapError(e: IOException): HttpProbeFailure {
        val reason = when (e) {
            is UnknownHostException -> HttpError.DNS
            is SocketTimeoutException -> HttpError.TIMEOUT
            is SSLException -> HttpError.TLS
            is ConnectException -> HttpError.CONNECT
            is NoRouteToHostException -> HttpError.CONNECT
            is InterruptedIOException -> HttpError.TIMEOUT
            is ProtocolException -> HttpError.RESPONSE
            else -> when {
                e.message?.contains("CLEARTEXT", ignoreCase = true) == true -> HttpError.CLEARTEXT_BLOCKED
                e.message?.contains("timeout", ignoreCase = true) == true -> HttpError.TIMEOUT
                e.message?.contains("Unable to resolve host", ignoreCase = true) == true -> HttpError.DNS
                e.message?.contains("no route", ignoreCase = true) == true -> HttpError.CONNECT
                else -> HttpError.CONNECT
            }
        }
        return HttpProbeFailure(reason)
    }
}
