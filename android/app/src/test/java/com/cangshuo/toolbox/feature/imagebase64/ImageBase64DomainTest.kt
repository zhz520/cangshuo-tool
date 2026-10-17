package com.cangshuo.toolbox.feature.imagebase64

import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Error
import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Exception
import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Limits
import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Options
import com.cangshuo.toolbox.feature.imagebase64.domain.detectImageMime
import com.cangshuo.toolbox.feature.imagebase64.domain.imageBase64PreviewSampleSize
import com.cangshuo.toolbox.feature.imagebase64.domain.parseImageBase64Input
import com.cangshuo.toolbox.feature.imagebase64.domain.renderImageBase64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class ImageBase64DomainTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 1, 2, 3)
    private val png64 = Base64.getEncoder().encodeToString(png)

    @Test fun detectsSupportedMagicBytes() {
        assertEquals("image/png", detectImageMime(png))
        assertEquals("image/jpeg", detectImageMime(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1)))
        assertEquals("image/webp", detectImageMime("RIFFxxxxWEBP".toByteArray(Charsets.US_ASCII)))
        assertEquals("image/gif", detectImageMime("GIF89a".toByteArray(Charsets.US_ASCII)))
        assertEquals(null, detectImageMime("not an image".toByteArray()))
    }

    @Test fun parsesRawAndDataUrlWithWhitespaceUrlsafeAndMissingPadding() {
        val parsed = parseImageBase64Input("  DATA:image/PNG;BASE64,  $png64\n ")
        assertEquals("image/png", parsed.detectedMime)
        assertEquals("image/png", parsed.declaredMime)
        assertEquals(png64, parsed.canonicalBase64)
        assertEquals(png.toList(), parsed.bytes.toList())

        // Make a payload whose standard form contains '+', '/', or padding, then ensure URL-safe
        // input is canonicalized without relying on the encoder's padding choice.
        val urlSafe = png64.replace('+', '-').replace('/', '_').trimEnd('=')
        assertEquals(png64, parseImageBase64Input(urlSafe).canonicalBase64)
    }

    @Test fun rejectsMalformedPaddingCharactersAndMimeMismatch() {
        assertEquals(ImageBase64Error.INVALID_INPUT, reason { parseImageBase64Input("$png64===") })
        assertEquals(ImageBase64Error.INVALID_INPUT, reason { parseImageBase64Input("AAAA=A") })
        assertEquals(ImageBase64Error.INVALID_INPUT, reason { parseImageBase64Input("$png64!") })
        assertEquals(ImageBase64Error.INVALID_INPUT, reason { parseImageBase64Input("data:image/png;base64,$png64#") })
        assertEquals(ImageBase64Error.MIME_MISMATCH, reason { parseImageBase64Input("data:image/jpeg;base64,$png64") })
        assertEquals(ImageBase64Error.UNSUPPORTED_FORMAT, reason { parseImageBase64Input("data:image/svg+xml;base64,$png64") })
        assertEquals(ImageBase64Error.UNSUPPORTED_FORMAT, reason { parseImageBase64Input("data:text/plain;base64,$png64") })
    }

    @Test fun enforcesInputAndBinaryBudgetsBeforeDecode() {
        assertEquals(ImageBase64Error.TOO_LARGE, reason { parseImageBase64Input("A".repeat(ImageBase64Limits.MAX_INPUT_CHARS + 1)) })
        assertEquals(ImageBase64Error.TOO_LARGE, reason {
            parseImageBase64Input("A".repeat((ImageBase64Limits.MAX_BINARY_BYTES / 3 + 1) * 4))
        })
    }

    @Test fun rendersDataUrlAndRawUrlSafeWrappedForms() {
        val raw = Base64.getEncoder().encodeToString(ByteArray(80) { (it * 31).toByte() })
        assertEquals("data:image/png;base64,$raw", renderImageBase64(raw, "image/png", ImageBase64Options()))
        val urlSafe = renderImageBase64(raw, "image/png", ImageBase64Options(dataUrl = false, urlSafe = true))
        assertEquals(false, urlSafe.contains('+'))
        assertEquals(false, urlSafe.contains('/'))
        assertEquals(false, urlSafe.contains('='))
        val wrapped = renderImageBase64(raw, "image/png", ImageBase64Options(dataUrl = false, lineWrap = true))
        assertEquals(raw, wrapped.replace("\n", ""))
        assertEquals(true, wrapped.lines().all { it.length <= ImageBase64Limits.LINE_WIDTH })
    }

    @Test fun previewBudgetAccountsForOrientationOldPreviewAndText() {
        assertEquals(1, imageBase64PreviewSampleSize(100, 100, 256L * 1024 * 1024, false, 1024))
        assertEquals(2, imageBase64PreviewSampleSize(2048, 2048, 256L * 1024 * 1024, true, 16L * 1024 * 1024))
        assertEquals(ImageBase64Error.TOO_LARGE, reason {
            imageBase64PreviewSampleSize(100_001, 1, 256L * 1024 * 1024, false, 0)
        })
        assertEquals(ImageBase64Error.MEMORY_LIMIT, reason {
            imageBase64PreviewSampleSize(10, 10, 4L * 1024, false, 1024)
        })
    }

    private fun reason(block: () -> Unit): ImageBase64Error = assertThrows(ImageBase64Exception::class.java, block).reason
}
