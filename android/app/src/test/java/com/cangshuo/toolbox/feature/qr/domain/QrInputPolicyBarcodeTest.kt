package com.cangshuo.toolbox.feature.qr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrInputPolicyBarcodeTest {
    private fun input(format: QrCodeFormat, text: String) = QrInput(text = text, format = format)

    @Test
    fun linearFormatsEncodePlainTextAndIgnoreTheStructuredType() {
        val input = QrInput(type = QrContentType.WIFI, text = "HELLO", ssid = "net",
            format = QrCodeFormat.CODE_128)
        assertNull(QrInputPolicy.validate(input))
        assertEquals("HELLO", QrInputPolicy.content(input))
        assertTrue(QrInputPolicy.hasContent(input))
    }

    @Test
    fun code39AndCode93RejectLowercaseAndUnsupportedCharacters() {
        assertNull(QrInputPolicy.validate(input(QrCodeFormat.CODE_39, "CANG-SHUO 39")))
        assertEquals(QrFailure.INVALID_BARCODE,
            QrInputPolicy.validate(input(QrCodeFormat.CODE_39, "Cang shuo")))
        assertEquals(QrFailure.INVALID_BARCODE,
            QrInputPolicy.validate(input(QrCodeFormat.CODE_93, "ABC#123")))
        assertNull(QrInputPolicy.validate(input(QrCodeFormat.CODE_93, "ABC-123.\$/+%")))
    }

    @Test
    fun code128AcceptsPrintableAsciiOnly() {
        assertNull(QrInputPolicy.validate(input(QrCodeFormat.CODE_128, "Cangshuo-2026 /#")))
        assertEquals(QrFailure.INVALID_BARCODE,
            QrInputPolicy.validate(input(QrCodeFormat.CODE_128, "中文")))
        assertEquals(QrFailure.INVALID_BARCODE,
            QrInputPolicy.validate(input(QrCodeFormat.CODE_128, "line\nbreak")))
    }

    @Test
    fun eanAndUpcAcceptTheBodyOrAValidFullValue() {
        assertNull(QrInputPolicy.validate(input(QrCodeFormat.EAN_13, "690123456789")))
        assertNull(QrInputPolicy.validate(input(QrCodeFormat.EAN_13, "6901234567892")))
        assertEquals(QrFailure.INVALID_BARCODE,
            QrInputPolicy.validate(input(QrCodeFormat.EAN_13, "6901234567891")))
        assertNull(QrInputPolicy.validate(input(QrCodeFormat.EAN_8, "1234567")))
        assertNull(QrInputPolicy.validate(input(QrCodeFormat.EAN_8, "12345670")))
        assertEquals(QrFailure.INVALID_BARCODE,
            QrInputPolicy.validate(input(QrCodeFormat.EAN_8, "123456")))
        assertNull(QrInputPolicy.validate(input(QrCodeFormat.UPC_A, "03600029145")))
        assertNull(QrInputPolicy.validate(input(QrCodeFormat.UPC_A, "036000291452")))
        assertEquals(QrFailure.INVALID_BARCODE,
            QrInputPolicy.validate(input(QrCodeFormat.UPC_A, "036000291453")))
    }

    @Test
    fun itfNeedsEvenDigitsAndCodabarNeedsGuards() {
        assertNull(QrInputPolicy.validate(input(QrCodeFormat.ITF, "1234567890")))
        assertEquals(QrFailure.INVALID_BARCODE,
            QrInputPolicy.validate(input(QrCodeFormat.ITF, "123456789")))
        assertEquals(QrFailure.INVALID_BARCODE,
            QrInputPolicy.validate(input(QrCodeFormat.ITF, "12A4")))
        assertNull(QrInputPolicy.validate(input(QrCodeFormat.CODABAR, "A12345B")))
        assertEquals(QrFailure.INVALID_BARCODE,
            QrInputPolicy.validate(input(QrCodeFormat.CODABAR, "12345")))
        assertEquals(QrFailure.INVALID_BARCODE,
            QrInputPolicy.validate(input(QrCodeFormat.CODABAR, "A12*45B")))
    }

    @Test
    fun emptyAndOversizedPayloadsAreRejected() {
        assertEquals(QrFailure.INVALID_BARCODE,
            QrInputPolicy.validate(input(QrCodeFormat.CODE_128, "")))
        assertEquals(QrFailure.INVALID_BARCODE, QrInputPolicy.validate(
            input(QrCodeFormat.CODE_128, "A".repeat(QrInputPolicy.MAX_BARCODE_LENGTH + 1))))
        assertFalse(QrInputPolicy.hasContent(input(QrCodeFormat.CODE_128, "")))
    }

    @Test
    fun qrFormatKeepsTheStructuredBehaviour() {
        val wifi = QrInput(type = QrContentType.WIFI, ssid = "net", password = "pw", security = "WPA")
        assertNull(QrInputPolicy.validate(wifi))
        assertTrue(QrInputPolicy.content(wifi).startsWith("WIFI:T:WPA;"))
        assertEquals("https://example.invalid",
            QrInputPolicy.content(QrInput(text = "https://example.invalid")))
    }
}
