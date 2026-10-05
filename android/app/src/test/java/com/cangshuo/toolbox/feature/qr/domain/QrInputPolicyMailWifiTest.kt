package com.cangshuo.toolbox.feature.qr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrInputPolicyMailWifiTest {
    private fun email(cc: String = "", bcc: String = "") = QrInput(
        type = QrContentType.EMAIL,
        form = QrFormInput(
            email = "hello@example.com", emailSubject = "Hi there",
            emailBody = "Line 1\nLine 2", emailCc = cc, emailBcc = bcc,
        ),
    )

    @Test
    fun mailCcAndBccArePercentEncodedAndEmptyOnesAreOmitted() {
        assertNull(QrInputPolicy.validate(email()))
        assertEquals("mailto:hello@example.com?subject=Hi%20there&body=Line%201%0ALine%202",
            QrInputPolicy.content(email()))
        assertNull(QrInputPolicy.validate(email(cc = "a@b.com, c@d.com", bcc = "e@f.org")))
        assertEquals("mailto:hello@example.com?subject=Hi%20there&body=Line%201%0ALine%202" +
            "&cc=a%40b.com,c%40d.com&bcc=e%40f.org",
            QrInputPolicy.content(email(cc = "a@b.com, c@d.com", bcc = "e@f.org")))
    }

    @Test
    fun invalidCcIsRejected() {
        assertEquals(QrFailure.INVALID_EMAIL, QrInputPolicy.validate(email(cc = "not-an-address")))
        assertEquals(QrFailure.INVALID_EMAIL, QrInputPolicy.validate(email(cc = "a@b.com,,")))
    }

    @Test
    fun enterpriseWifiEmitsEapFieldsAndEscapesTheIdentity() {
        val input = QrInput(type = QrContentType.WIFI, ssid = "Corp;Net", password = "secret",
            security = "WPA-EAP", eap = "TTLS", phase2 = "MSCHAPV2",
            identity = "user;1", anonymous = "anon@corp")
        assertNull(QrInputPolicy.validate(input))
        assertEquals("WIFI:T:WPA-EAP;S:Corp\\;Net;P:secret;E:TTLS;I:user\\;1;A:anon@corp;PH2:MSCHAPV2;H:false;;",
            QrInputPolicy.content(input))
    }

    @Test
    fun enterpriseWifiRequiresIdentityAndANonTlsPassword() {
        val missingIdentity = QrInput(type = QrContentType.WIFI, ssid = "Corp", password = "pw",
            security = "WPA3-EAP")
        assertEquals(QrFailure.INVALID_WIFI, QrInputPolicy.validate(missingIdentity))
        val withIdentity = missingIdentity.copy(identity = "user")
        assertNull(QrInputPolicy.validate(withIdentity))
        val missingPassword = withIdentity.copy(password = "")
        assertEquals(QrFailure.PASSWORD_REQUIRED, QrInputPolicy.validate(missingPassword))
        assertNull(QrInputPolicy.validate(missingPassword.copy(eap = "TLS")))
        assertEquals(QrFailure.INVALID_WIFI, QrInputPolicy.validate(withIdentity.copy(eap = "LEAP")))
    }

    @Test
    fun wpa3AndInvalidSecurityAreHandled() {
        val wpa3 = QrInput(type = QrContentType.WIFI, ssid = "Home", password = "pw", security = "WPA3")
        assertNull(QrInputPolicy.validate(wpa3))
        assertTrue(QrInputPolicy.content(wpa3).startsWith("WIFI:T:WPA3;"))
        assertEquals(QrFailure.INVALID_WIFI, QrInputPolicy.validate(wpa3.copy(security = "WPA4")))
        // Open networks keep ignoring any stored password.
        assertTrue(QrInputPolicy.content(wpa3.copy(security = "nopass", password = "kept"))
            .startsWith("WIFI:T:nopass;S:Home;H:false;;"))
    }
}
