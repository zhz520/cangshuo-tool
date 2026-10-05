package com.cangshuo.toolbox.feature.webtools.domain

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class WebPdfExportTest {
    private fun url(text: String) = "data:application/pdf;base64," + Base64.getEncoder().encodeToString(text.toByteArray(Charsets.US_ASCII))
    @Test fun pdfExportUsesDocumentMimeAndExactBytes() {
        val pdf = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF\n"
        val result = DecodeWebToolExportUseCase(url(pdf))
        assertEquals("application/pdf", result.mimeType)
        assertEquals("toolbox.pdf", result.fileName)
        assertArrayEquals(pdf.toByteArray(), result.bytes)
    }
    @Test fun missingHeaderOrEndMarkerIsRejected() {
        for (pdf in listOf("garbage %%EOF", "%PDF-1.7\n1 0 obj\n", "%PDF-1.7\n%%EOF malicious")) {
            try { DecodeWebToolExportUseCase(url(pdf)); fail("Expected invalid PDF") } catch (_: WebToolExportException) { }
        }
    }
    @Test fun overBudgetPdfIsRejectedBeforeBase64Allocation() {
        try { DecodeWebToolExportUseCase("data:application/pdf;base64," + "A".repeat(DecodeWebToolExportUseCase.MAX_PDF_URL_CHARS)); fail("Expected size rejection") }
        catch (_: WebToolExportException) { }
    }
}
