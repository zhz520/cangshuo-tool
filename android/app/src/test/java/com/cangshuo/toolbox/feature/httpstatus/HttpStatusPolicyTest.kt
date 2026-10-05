package com.cangshuo.toolbox.feature.httpstatus

import com.cangshuo.toolbox.feature.httpstatus.domain.*
import org.junit.Assert.*
import org.junit.Test

class HttpStatusPolicyTest {
    @Test fun normalizesIdnIpv6PortsAndStripsFragment() {
        assertEquals("https://xn--fiqs8s.cn/", HttpTargets.validate("HTTPS://中国.cn").url)
        assertTrue(HttpTargets.validate("HTTPS://中国.cn").secure)
        assertEquals("https://example.com:8443/a%20b?q=1", HttpTargets.validate("https://example.com:8443/a%20b?q=1#part").url)
        assertEquals("https://[::1]:8443/", HttpTargets.validate("https://[::1]:8443").url)
        assertEquals("http://192.168.1.1/", HttpTargets.validate("http://192.168.1.1").url)
        assertEquals("https://localhost/?x=1", HttpTargets.validate("https://localhost?x=1").url)
        assertFalse(HttpTargets.validate("http://192.168.1.1/").secure)
    }
    @Test fun rejectsCredentialsSchemesControlsAndBadHosts() {
        val bad = listOf("javascript:alert(1)", "file:///etc/passwd", "ftp://example.com", "https://user:pass@example.com",
            "https://example.com:0", "https://example.com:99999", "https://exa mple.com", "https://a\nb.example.com",
            "https://:443", "https://-bad-.example.com", "https://example..com", "https://127.01.0.1",
            "https://example.com/a\"b", "https://example.com/" + "a".repeat(3000))
        bad.forEach { input ->
            try { HttpTargets.validate(input); fail("Accepted invalid input") }
            catch (e: HttpProbeFailure) { assertTrue(e.reason == HttpError.INVALID_URL || e.reason == HttpError.UNSUPPORTED_SCHEME) }
        }
    }
    @Test fun resolvesRedirectsAndClassifiesMethods() {
        val base = HttpTargets.validate("https://example.com/a/b?x=1").url
        assertEquals("https://example.com/c", HttpRedirects.resolve(base, "/c"))
        assertEquals("https://other.example/d", HttpRedirects.resolve(base, "https://other.example/d"))
        assertEquals("https://example.com/a/c", HttpRedirects.resolve(base, "c"))
        assertEquals(HttpMethod.GET, HttpRedirects.nextMethod(303, HttpMethod.HEAD))
        assertEquals(HttpMethod.HEAD, HttpRedirects.nextMethod(301, HttpMethod.HEAD))
        assertEquals(HttpMethod.GET, HttpRedirects.nextMethod(308, HttpMethod.GET))
        assertTrue(HttpRedirects.isRedirect(301)); assertTrue(HttpRedirects.isRedirect(308))
        assertFalse(HttpRedirects.isRedirect(304)); assertFalse(HttpRedirects.isRedirect(200))
        assertEquals(HttpOutcome.SUCCESS, outcomeOf(204)); assertEquals(HttpOutcome.REDIRECT, outcomeOf(301))
        assertEquals(HttpOutcome.CLIENT_ERROR, outcomeOf(404)); assertEquals(HttpOutcome.SERVER_ERROR, outcomeOf(503))
        assertEquals(HttpOutcome.OTHER, outcomeOf(99))
    }
    @Test fun sanitizesHeadersAndContentLength() {
        val headers = HttpHeaderPolicy.sanitize(listOf(
            HttpHeader("Content-Type", "text/html; \u0000charset=utf-8"),
            HttpHeader("X-Long", "y".repeat(600)), HttpHeader("", ""),
        ))
        assertEquals(2, headers.size)
        assertEquals("text/html;  charset=utf-8", headers[0].value)
        assertTrue(headers[1].value.endsWith("…")); assertEquals(513, headers[1].value.length)
        assertEquals("v", HttpHeaderPolicy.first(listOf(HttpHeader("Server", "v")), "server"))
        assertNull(HttpHeaderPolicy.first(headers, "server"))
        assertEquals(1024L, HttpHeaderPolicy.parseContentLength(" 1024 "))
        assertEquals(0L, HttpHeaderPolicy.parseContentLength("0"))
        assertNull(HttpHeaderPolicy.parseContentLength("-1")); assertNull(HttpHeaderPolicy.parseContentLength("1.5"))
        assertNull(HttpHeaderPolicy.parseContentLength("999999999999999999999"))
        assertNull(HttpHeaderPolicy.clean("   ", 10)); assertEquals("a…", HttpHeaderPolicy.clean("ab", 1))
    }
}
