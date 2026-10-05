package com.cangshuo.toolbox.feature.httpstatus.domain

import java.net.IDN
import java.net.URI
import java.util.Locale

enum class HttpMethod { HEAD, GET }

enum class HttpError { INVALID_URL, UNSUPPORTED_SCHEME, CLEARTEXT_BLOCKED, BUSY, TIMEOUT, DNS, TLS, CONNECT,
    TOO_MANY_REDIRECTS, REDIRECT_LOOP, RESPONSE, UNSUPPORTED, FAILED }

class HttpProbeFailure(val reason: HttpError) : IllegalStateException(reason.name)

data class HttpTarget(val url: String, val secure: Boolean, val host: String) {
    override fun toString() = "HttpTarget[redacted]"
}
data class HttpProbeRequest(val target: HttpTarget, val method: HttpMethod, val followRedirects: Boolean) {
    override fun toString() = "HttpProbeRequest[redacted]"
}
data class HttpRedirect(val from: String, val status: Int, val to: String) {
    override fun toString() = "HttpRedirect[redacted]"
}
data class HttpHeader(val name: String, val value: String)

data class HttpProbeResult(val finalUrl: String, val status: Int, val statusText: String?, val httpVersion: String?,
    val elapsedMs: Long, val contentType: String?, val contentLength: Long?, val server: String?,
    val tlsVersion: String?, val tlsCipher: String?, val bodyNotRead: Boolean, val headers: List<HttpHeader>,
    val redirects: List<HttpRedirect>) {
    override fun toString() = "HttpProbeResult[redacted]"
}

enum class HttpOutcome { SUCCESS, REDIRECT, CLIENT_ERROR, SERVER_ERROR, OTHER }
fun outcomeOf(status: Int): HttpOutcome = when (status) {
    in 200..299 -> HttpOutcome.SUCCESS
    in 300..399 -> HttpOutcome.REDIRECT
    in 400..499 -> HttpOutcome.CLIENT_ERROR
    in 500..599 -> HttpOutcome.SERVER_ERROR
    else -> HttpOutcome.OTHER
}

object HttpTargets {
    const val MAX_URL_LENGTH = 2048
    private const val FORBIDDEN_PATH_CHARS = "\"<>\\^`{|}[]"
    private val schemePrefix = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://")
    private val ipv6Chars = Regex("[0-9a-fA-F:.]+")
    private val labelRegex = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")

    fun validate(input: String): HttpTarget {
        fun invalid(reason: HttpError = HttpError.INVALID_URL): Nothing = throw HttpProbeFailure(reason)
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_URL_LENGTH) invalid()
        if (trimmed.any { it <= ' ' || it.code == 0x7F }) invalid()
        val schemeMatch = schemePrefix.find(trimmed) ?: invalid(HttpError.UNSUPPORTED_SCHEME)
        val scheme = schemeMatch.groupValues[1].lowercase(Locale.ROOT)
        if (scheme != "https" && scheme != "http") invalid(HttpError.UNSUPPORTED_SCHEME)
        val rest = trimmed.substring(schemeMatch.value.length)
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        val pathAndQuery = rest.substring(authorityEnd)
        if (authority.isEmpty() || authority.contains('@')) invalid()
        var host: String
        var port: Int? = null
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close <= 1) invalid()
            val literal = authority.substring(1, close)
            if (!ipv6Chars.matches(literal) || literal.count { it == ':' } < 2) invalid()
            host = literal.lowercase(Locale.ROOT)
            val remainder = authority.substring(close + 1)
            if (remainder.isNotEmpty()) {
                if (!remainder.startsWith(':')) invalid()
                port = parsePort(remainder.substring(1)) ?: invalid()
            }
        } else {
            val colon = authority.lastIndexOf(':')
            if (colon >= 0) {
                if (authority.indexOf(':') != colon) invalid()
                port = parsePort(authority.substring(colon + 1)) ?: invalid()
                host = authority.substring(0, colon)
            } else host = authority
            if (host.isEmpty()) invalid()
            val ascii = runCatching { IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES) }.getOrElse { invalid() }.lowercase(Locale.ROOT)
            if (ascii.length > 253) invalid()
            val labels = ascii.split('.')
            if (labels.any { it.length !in 1..63 || !labelRegex.matches(it) }) invalid()
            if (ascii.all { it in "0123456789." }) {
                val parts = ascii.split('.')
                if (parts.size != 4 || parts.any { it.length !in 1..3 || (it.length > 1 && it.startsWith('0')) || it.toIntOrNull() !in 0..255 }) invalid()
            }
            host = ascii
        }
        if (pathAndQuery.any { it in FORBIDDEN_PATH_CHARS }) invalid()
        val withoutFragment = pathAndQuery.substringBefore('#')
        val path = withoutFragment.substringBefore('?').ifEmpty { "/" }
        val query = withoutFragment.substringAfter('?', "").let { if (it.isEmpty()) "" else "?$it" }
        val portPart = port?.let { ":$it" }.orEmpty()
        val hostPart = if (host.contains(':')) "[$host]" else host
        return HttpTarget("$scheme://$hostPart$portPart$path$query", scheme == "https", host)
    }

    private fun parsePort(raw: String): Int? {
        if (raw.isEmpty() || raw.length > 5 || raw.any { it !in '0'..'9' }) return null
        return raw.toIntOrNull()?.takeIf { it in 1..65535 }
    }
}

object HttpRedirects {
    const val MAX_REDIRECTS = 5
    fun isRedirect(status: Int) = status == 301 || status == 302 || status == 303 || status == 307 || status == 308
    fun nextMethod(status: Int, method: HttpMethod) = if (status == 303) HttpMethod.GET else method
    fun resolve(current: String, location: String): String {
        val base = runCatching { URI(current) }.getOrElse { throw HttpProbeFailure(HttpError.RESPONSE) }
        return runCatching { base.resolve(location).toString() }.getOrElse { throw HttpProbeFailure(HttpError.RESPONSE) }
    }
}

object HttpHeaderPolicy {
    const val MAX_HEADERS = 60
    const val MAX_VALUE_CHARS = 512
    const val MAX_TOTAL_CHARS = 8192

    fun clean(value: String, limit: Int): String? {
        val cleaned = value.map { if (Character.isISOControl(it)) ' ' else it }.joinToString("").trim()
        if (cleaned.isEmpty()) return null
        return if (cleaned.length > limit) cleaned.take(limit) + "…" else cleaned
    }

    fun sanitize(headers: List<HttpHeader>): List<HttpHeader> {
        val result = mutableListOf<HttpHeader>()
        var total = 0
        for (header in headers) {
            if (result.size >= MAX_HEADERS || total >= MAX_TOTAL_CHARS) break
            val name = clean(header.name, 128) ?: continue
            val value = clean(header.value, MAX_VALUE_CHARS) ?: continue
            total += name.length + value.length
            result += HttpHeader(name, value)
        }
        return result
    }

    fun first(headers: List<HttpHeader>, name: String): String? =
        headers.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value

    fun parseContentLength(value: String?): Long? {
        val digits = value?.trim() ?: return null
        if (digits.isEmpty() || digits.length > 18 || digits.any { it !in '0'..'9' }) return null
        return digits.toLongOrNull()
    }
}

interface HttpStatusRepository { suspend fun execute(request: HttpProbeRequest): HttpProbeResult }
class ProbeHttpStatusUseCase(private val repository: HttpStatusRepository) {
    suspend operator fun invoke(url: String, method: HttpMethod, followRedirects: Boolean): HttpProbeResult {
        val target = HttpTargets.validate(url)
        return repository.execute(HttpProbeRequest(target, method, followRedirects))
    }
}
