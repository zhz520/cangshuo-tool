package com.cangshuo.toolbox.feature.qr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrInputPolicyContactTest {
    private fun contact(
        format: QrContactFormat = QrContactFormat.MECARD,
        phones: List<String> = listOf("13800138000"),
        emails: List<String> = listOf("ada@example.com"),
        addresses: List<String> = listOf(""),
    ) = QrInput(
        type = QrContentType.CONTACT,
        form = QrFormInput(contact = QrContactInput(
            name = "Ada, Lovelace; \\", organization = "Analytical Engine",
            format = format, phones = phones, emails = emails, addresses = addresses,
        )),
    )

    @Test
    fun mecardKeepsItsFieldOrderAndCarriesEveryPhoneAndEmail() {
        val input = contact(phones = listOf("111", "222"), emails = listOf("a@b.com", "c@d.com"))
        assertNull(QrInputPolicy.validate(input))
        assertEquals("MECARD:N:Ada, Lovelace\\; \\\\;ORG:Analytical Engine;" +
            "TEL:111;TEL:222;EMAIL:a@b.com;EMAIL:c@d.com;;",
            QrInputPolicy.content(input))
    }

    @Test
    fun vcardCarriesMultipleValuesAndEscapesText() {
        val input = contact(
            format = QrContactFormat.VCARD,
            phones = listOf("111", "", "222"),
            emails = listOf("a@b.com", "c@d.com"),
            addresses = listOf("1 Main St, Apt 2", ""),
        )
        assertNull(QrInputPolicy.validate(input))
        assertEquals(listOf(
            "BEGIN:VCARD", "VERSION:3.0", "FN:Ada\\, Lovelace\\; \\\\",
            "ORG:Analytical Engine", "TEL;TYPE=CELL:111", "TEL;TYPE=CELL:222",
            "EMAIL;TYPE=INTERNET:a@b.com", "EMAIL;TYPE=INTERNET:c@d.com",
            "ADR;TYPE=HOME:;;1 Main St\\, Apt 2;;;;", "END:VCARD",
        ).joinToString("\r\n"), QrInputPolicy.content(input))
    }

    @Test
    fun blanksAreIgnoredAndInvalidRowsAreRejected() {
        val blank = contact(phones = listOf("", ""), emails = listOf(""))
        assertNull(QrInputPolicy.validate(blank))
        assertTrue(QrInputPolicy.content(blank).startsWith("MECARD:N:Ada"))
        val badPhone = contact(phones = listOf("13800138000", "12345abc"))
        assertEquals(QrFailure.INVALID_CONTACT, QrInputPolicy.validate(badPhone))
        val badEmail = contact(emails = listOf("a@b.com", "nope"))
        assertEquals(QrFailure.INVALID_CONTACT, QrInputPolicy.validate(badEmail))
    }

    @Test
    fun tooManyRowsAndBlankNamesAreRejected() {
        val tooMany = contact(phones = listOf("1", "2", "3", "4"))
        assertEquals(QrFailure.INVALID_CONTACT, QrInputPolicy.validate(tooMany))
        val noName = QrInput(type = QrContentType.CONTACT,
            form = QrFormInput(contact = QrContactInput(name = "  ")))
        assertEquals(QrFailure.INVALID_CONTACT, QrInputPolicy.validate(noName))
    }
}
