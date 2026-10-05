package com.cangshuo.toolbox.feature.webtools.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class WebToolHostPolicyTest {
    @Test fun sameOriginAndDefaultTlsPortAreAllowed() {
        val policy = WebToolNavigationPolicy("https://tool.zhzgo.cn")
        assertTrue(policy.allows("https://tool.zhzgo.cn/tools/qr_studio/?v=1#preview"))
        assertTrue(policy.allows("HTTPS://TOOL.ZHZGO.CN:443/tools/qr_studio/"))
    }
    @Test fun suffixUserInfoSchemeAndPortCannotBypassOrigin() {
        val policy = WebToolNavigationPolicy("https://tool.zhzgo.cn")
        listOf("https://tool.zhzgo.cn.evil.example/", "https://evil.example/?next=tool.zhzgo.cn", "https://user@tool.zhzgo.cn/",
            "http://tool.zhzgo.cn/", "https://tool.zhzgo.cn:8443/", "file:///tool.zhzgo.cn", "javascript:alert(1)", "data:text/html,hello", "https://[", "//tool.zhzgo.cn").forEach { assertFalse(it, policy.allows(it)) }
    }
    @Test fun debugOriginDoesNotAllowAnotherLoopbackPortOrAlias() {
        val policy = WebToolNavigationPolicy("http://localhost:8088")
        assertTrue(policy.allows("http://localhost:8088/tools/qr_studio/"))
        assertFalse(policy.allows("http://localhost:8081/"))
        assertFalse(policy.allows("http://127.0.0.1:8088/"))
    }
    @Test fun boundedPngAndJpegExportsHaveFixedSafeNames() {
        val png = ByteArray(24)
        byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10).copyInto(png)
        png[18] = 2; png[22] = 2
        val export = DecodeWebToolExportUseCase(url("image/png", png))
        assertEquals("qrcode.png", export.fileName)
        assertArrayEquals(png, export.bytes)
        val jpeg = DecodeWebToolExportUseCase(url("image/jpeg", byteArrayOf(255.toByte(), 216.toByte(), 255.toByte(), 217.toByte())))
        assertEquals("qrcode.jpg", jpeg.fileName)
    }
    @Test fun geometrySvgIsAllowedAndActiveContentIsRejected() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512"><rect width="512" height="512" fill="#FFFFFF"/><path d="M12 12h4v4h-4z" fill="#000000"/></svg>"""
        assertEquals("qrcode.svg", DecodeWebToolExportUseCase(url("image/svg+xml", svg.toByteArray())).fileName)
        listOf(svg.replace("<path", "<script>alert(1)</script><path"), svg.replace("512", "2049"), svg.replace("height=\"512\"", "height=\"513\""), "<!DOCTYPE svg><svg/>").forEach {
            rejected { DecodeWebToolExportUseCase(url("image/svg+xml", it.toByteArray())) }
        }
    }
    @Test fun malformedUnsupportedEmptyAndOversizedExportsFailWithoutCause() {
        listOf("data:text/html;base64,PHNjcmlwdD4=", "data:image/png,abc", "data:image/png;base64,", "data:image/png;base64,%%", "blob:http://localhost/test",
            "data:image/jpeg;base64,AAAA", "data:image/png;base64," + "A".repeat(DecodeWebToolExportUseCase.MAX_URL_CHARS)).forEach {
            rejected { DecodeWebToolExportUseCase(it) }
        }
        rejected { DecodeWebToolExportUseCase(url("image/png", ByteArray(DecodeWebToolExportUseCase.MAX_BYTES + 1))) }
    }
    @Test fun pngZeroAndOversizedDimensionsAreRejectedBeforeSaving() {
        val png = ByteArray(24)
        byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10).copyInto(png)
        rejected { DecodeWebToolExportUseCase(url("image/png", png)) }
        png[18] = 8; png[19] = 1; png[22] = 2
        rejected { DecodeWebToolExportUseCase(url("image/png", png)) }
    }
    private fun url(mime: String, bytes: ByteArray) = "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
    private fun rejected(action: () -> Unit) {
        try { action(); fail("Expected fixed export error") } catch (error: WebToolExportException) {
            assertEquals("Web export unavailable", error.message); assertNull(error.cause)
        }
    }
}
