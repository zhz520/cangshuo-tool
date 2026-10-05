package com.cangshuo.toolbox.feature.qr.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

object QrInputPolicy {
    const val MAX_TEXT_LENGTH = 2000
    const val MAX_FIELD_LENGTH = 256
    const val MAX_MESSAGE_LENGTH = 500
    const val MAX_EVENT_DATE_LENGTH = 10
    const val MAX_EVENT_TIME_LENGTH = 5
    const val MAX_CONTENT_BYTES = 6000
    const val MAX_BARCODE_LENGTH = 80
    const val MAX_CONTACT_ENTRIES = 3

    private const val MIN_PHONE_DIGITS = 2
    private const val MIN_EVENT_YEAR = 1900
    private const val MAX_EVENT_YEAR = 3000
    private const val VEVENT = "BEGIN:VEVENT\r\n"
    // Shared by CODE 39 and CODE 93 (ZXing's ALPHABET); lowercase input is rejected, never rewritten.
    private const val CODE39_93_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ -.$/+%"
    private const val CODABAR_BODY_CHARS = "-\$:/.+"

    /** Wi-Fi security markers and enterprise EAP selections; protocol names keep their spelling. */
    val WIFI_SECURITIES = setOf("WPA", "WPA3", "WPA-EAP", "WPA3-EAP", "WEP", "nopass")
    val EAP_METHODS = setOf("TTLS", "PEAP", "TLS")
    val PHASE2_METHODS = setOf("MSCHAPV2", "GTC")

    private val PHONE_ALLOWED: Set<Char> = buildSet {
        addAll('0'..'9')
        add('+')
        add(' ')
        add('-')
        add('(')
        add(')')
        add('.')
    }

    // Strict patterns: the same reasoning as the timestamp tool, so 2026-02-30 is rejected.
    private val EVENT_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    private val EVENT_TIME: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)
    private val EVENT_DATE_OUT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuuMMdd")
    private val EVENT_TIME_OUT: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss")
    private val EVENT_UTC_OUT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    /** True when the current type has enough input to attempt a payload. */
    fun hasContent(input: QrInput): Boolean = if (input.format.linear) {
        input.text.isNotEmpty()
    } else when (input.type) {
        QrContentType.TEXT -> input.text.isNotEmpty()
        QrContentType.WIFI -> input.ssid.isNotEmpty()
        QrContentType.PHONE -> input.form.phone.isNotBlank()
        QrContentType.EMAIL -> input.form.email.isNotBlank()
        QrContentType.SMS -> input.form.smsNumber.isNotBlank()
        QrContentType.CONTACT -> input.form.contact.name.isNotBlank()
        QrContentType.EVENT -> input.form.event.title.isNotBlank()
    }

    fun validate(input: QrInput, zone: ZoneId = ZoneId.systemDefault()): QrFailure? {
        if (input.format.linear) return validateBarcode(input)
        return when (input.type) {
            QrContentType.TEXT -> validateText(input)
            QrContentType.WIFI -> validateWifi(input)
            QrContentType.PHONE -> validatePhone(input.form.phone)
            QrContentType.EMAIL -> validateEmail(input.form)
            QrContentType.SMS -> validateSms(input.form)
            QrContentType.CONTACT -> validateContact(input.form.contact)
            QrContentType.EVENT -> validateEvent(input.form.event, zone)
        }
    }

    fun content(input: QrInput, zone: ZoneId = ZoneId.systemDefault()): String {
        if (input.format.linear) return input.text
        return when (input.type) {
            QrContentType.TEXT -> input.text
            QrContentType.WIFI -> wifiContent(input)
            QrContentType.PHONE -> "tel:" + input.form.phone.trim()
            QrContentType.EMAIL -> emailContent(input.form)
            QrContentType.SMS -> "SMSTO:" + input.form.smsNumber.trim() + ":" + input.form.smsMessage
        QrContentType.CONTACT -> when (input.form.contact.format) {
            QrContactFormat.MECARD -> mecard(input.form.contact)
            QrContactFormat.VCARD -> vcard(input.form.contact)
        }
            QrContentType.EVENT -> vevent(input.form.event, zone)
        }
    }

    /** Per-format character and length rules; the payload is never rewritten to fit. */
    private fun validateBarcode(input: QrInput): QrFailure? {
        val text = input.text
        if (text.length > MAX_TEXT_LENGTH) return QrFailure.INPUT_TOO_LONG
        if (text.isEmpty() || !isUtf8(text)) return QrFailure.INVALID_BARCODE
        if (text.toByteArray(Charsets.UTF_8).size > MAX_CONTENT_BYTES) return QrFailure.INPUT_TOO_LONG
        if (text.length > MAX_BARCODE_LENGTH) return QrFailure.INVALID_BARCODE
        return when (input.format) {
            QrCodeFormat.QR_CODE -> null
            QrCodeFormat.CODE_128 -> if (text.all { it.code in 32..126 }) null else QrFailure.INVALID_BARCODE
            QrCodeFormat.CODE_39, QrCodeFormat.CODE_93 ->
                if (text.all { it in CODE39_93_CHARS }) null else QrFailure.INVALID_BARCODE
            QrCodeFormat.EAN_13 -> fixedDigits(text, 12)
            QrCodeFormat.EAN_8 -> fixedDigits(text, 7)
            QrCodeFormat.UPC_A -> fixedDigits(text, 11)
            QrCodeFormat.ITF -> if (text.length in 2..MAX_BARCODE_LENGTH && text.length % 2 == 0 &&
                text.all { it.isDigit() }) null else QrFailure.INVALID_BARCODE
            QrCodeFormat.CODABAR -> if (codabarOk(text)) null else QrFailure.INVALID_BARCODE
        }
    }

    /** EAN-13/EAN-8/UPC-A accept the body (writer appends the check digit) or the full value. */
    private fun fixedDigits(text: String, bodyLength: Int): QrFailure? {
        if (text.length != bodyLength && text.length != bodyLength + 1) return QrFailure.INVALID_BARCODE
        if (!text.all { it.isDigit() }) return QrFailure.INVALID_BARCODE
        if (text.length == bodyLength + 1 &&
            text.last().digitToInt() != checkDigit(text.dropLast(1))) return QrFailure.INVALID_BARCODE
        return null
    }

    /** Standard GS1 check digit: weights alternate 3/1 from the rightmost body digit. */
    private fun checkDigit(body: String): Int {
        var sum = 0
        var weight = 3
        for (index in body.indices.reversed()) {
            sum += (body[index] - '0') * weight
            weight = if (weight == 3) 1 else 3
        }
        return (10 - sum % 10) % 10
    }

    /** Codabar requires A-D start/stop guards; the body keeps digits and -$:/.+ unchanged. */
    private fun codabarOk(text: String): Boolean {
        if (text.length < 3) return false
        if (text.first().uppercaseChar() !in 'A'..'D' || text.last().uppercaseChar() !in 'A'..'D') return false
        val body = text.substring(1, text.length - 1)
        return body.isNotEmpty() && body.all { it.isDigit() || it in CODABAR_BODY_CHARS }
    }

    private fun validateText(input: QrInput): QrFailure? {
        if (input.text.length > MAX_TEXT_LENGTH) return QrFailure.INPUT_TOO_LONG
        return if (isUtf8(input.text)) null else QrFailure.INVALID_TEXT
    }

    private fun validateWifi(input: QrInput): QrFailure? {
        val password = if (input.security == "nopass") "" else input.password
        val extra = listOf(input.identity, input.anonymous)
        if (input.ssid.length > MAX_FIELD_LENGTH || password.length > MAX_FIELD_LENGTH ||
            extra.any { it.length > MAX_FIELD_LENGTH }) {
            return QrFailure.INPUT_TOO_LONG
        }
        if (!isUtf8(input.ssid) || !isUtf8(password) || extra.any { !isUtf8(it) }) return QrFailure.INVALID_TEXT
        if (input.security !in WIFI_SECURITIES ||
            (input.ssid + password + input.identity + input.anonymous).any(::isControl)) {
            return QrFailure.INVALID_WIFI
        }
        if (input.ssid.toByteArray(Charsets.UTF_8).size > 32) return QrFailure.SSID_TOO_LONG
        if (input.security in setOf("WPA-EAP", "WPA3-EAP")) {
            if (input.eap !in EAP_METHODS || input.phase2 !in PHASE2_METHODS) return QrFailure.INVALID_WIFI
            if (input.identity.isBlank()) return QrFailure.INVALID_WIFI
            // TLS authenticates with certificates, so an empty private-key password is valid there.
            if (input.eap != "TLS" && input.password.isEmpty()) return QrFailure.PASSWORD_REQUIRED
            return null
        }
        if (input.ssid.isNotEmpty() && input.security != "nopass" && input.password.isEmpty()) {
            return QrFailure.PASSWORD_REQUIRED
        }
        return null
    }

    private fun validatePhone(value: String): QrFailure? =
        if (phoneFailure(value) == null) null else QrFailure.INVALID_PHONE

    private fun validateSms(form: QrFormInput): QrFailure? {
        if (form.smsNumber.length > MAX_FIELD_LENGTH || form.smsMessage.length > MAX_MESSAGE_LENGTH) {
            return QrFailure.INPUT_TOO_LONG
        }
        if (!isUtf8(form.smsNumber) || !isUtf8(form.smsMessage)) return QrFailure.INVALID_TEXT
        if (phoneFailure(form.smsNumber) != null) return QrFailure.INVALID_SMS
        return null
    }

    private fun validateContact(contact: QrContactInput): QrFailure? {
        if (contact.phones.size > MAX_CONTACT_ENTRIES || contact.emails.size > MAX_CONTACT_ENTRIES ||
            contact.addresses.size > MAX_CONTACT_ENTRIES) return QrFailure.INVALID_CONTACT
        val fields = listOf(contact.name, contact.organization) + contact.phones + contact.emails + contact.addresses
        if (fields.any { it.length > MAX_FIELD_LENGTH }) return QrFailure.INPUT_TOO_LONG
        if (fields.any { !isUtf8(it) }) return QrFailure.INVALID_TEXT
        if (contact.name.isBlank() || contact.name.any(::isControl) || contact.organization.any(::isControl)) {
            return QrFailure.INVALID_CONTACT
        }
        if (fields.any { value -> value.any(::isControl) }) return QrFailure.INVALID_CONTACT
        if (contact.phones.any { it.isNotBlank() && phoneFailure(it) != null }) return QrFailure.INVALID_CONTACT
        if (contact.emails.any { it.isNotBlank() && emailFailure(it) != null }) return QrFailure.INVALID_CONTACT
        return null
    }

    private fun validateEmail(form: QrFormInput): QrFailure? {
        if (form.emailSubject.length > MAX_FIELD_LENGTH || form.emailBody.length > MAX_MESSAGE_LENGTH ||
            form.emailCc.length > MAX_MESSAGE_LENGTH || form.emailBcc.length > MAX_MESSAGE_LENGTH) {
            return QrFailure.INPUT_TOO_LONG
        }
        if (!isUtf8(form.emailSubject) || !isUtf8(form.emailBody) ||
            !isUtf8(form.emailCc) || !isUtf8(form.emailBcc)) return QrFailure.INVALID_TEXT
        if (form.emailCc.any(::isControl) || form.emailBcc.any(::isControl)) return QrFailure.INVALID_EMAIL
        if (emailFailure(form.email) != null) return QrFailure.INVALID_EMAIL
        if (emailListFailure(form.emailCc) != null || emailListFailure(form.emailBcc) != null) {
            return QrFailure.INVALID_EMAIL
        }
        return null
    }

    /** Comma-separated recipients; every address is validated and encoded on its own. */
    private fun emailListFailure(value: String): QrFailure? {
        if (value.isBlank()) return null
        val parts = value.split(',').map { it.trim() }
        if (parts.any { it.isEmpty() }) return QrFailure.INVALID_EMAIL
        return parts.firstNotNullOfOrNull { emailFailure(it) }
    }

    private fun validateEvent(event: QrEventInput, zone: ZoneId): QrFailure? {
        val fields = listOf(
            event.title, event.location, event.startDate, event.startTime, event.endDate, event.endTime,
        )
        if (fields.any { it.length > MAX_FIELD_LENGTH } || event.description.length > MAX_MESSAGE_LENGTH) {
            return QrFailure.INPUT_TOO_LONG
        }
        if (fields.any { !isUtf8(it) } || !isUtf8(event.description)) return QrFailure.INVALID_TEXT
        if (fields.any { value -> value.any(::isControl) }) return QrFailure.INVALID_EVENT
        // The description may span lines; those are escaped into iCalendar text instead of rejected.
        if (event.description.any { it != '\n' && it != '\t' && isControl(it) }) return QrFailure.INVALID_EVENT
        if (event.title.isBlank()) return QrFailure.INVALID_EVENT
        val start = parseEventMoment(event.startDate, event.startTime) ?: return QrFailure.INVALID_EVENT
        val hasEndDate = event.endDate.isNotBlank()
        val hasEndTime = event.endTime.isNotBlank()
        val end = if (hasEndDate || hasEndTime) {
            if (hasEndDate != hasEndTime) return QrFailure.INVALID_EVENT
            parseEventMoment(event.endDate, event.endTime) ?: return QrFailure.INVALID_EVENT
        } else null
        if (end != null && !end.isAfter(start)) return QrFailure.EVENT_RANGE
        // Device-zone conversion must not silently shift a time that the DST gap removed.
        if (event.timeMode == QrEventTimeMode.DEVICE_TO_UTC) {
            if (hasZoneGap(start, zone) || (end != null && hasZoneGap(end, zone))) {
                return QrFailure.EVENT_TIME_GAP
            }
        }
        return null
    }

    /** True when the wall-clock time does not exist in this zone (daylight-saving spring gap). */
    private fun hasZoneGap(moment: LocalDateTime, zone: ZoneId): Boolean =
        zone.rules.getValidOffsets(moment).isEmpty()

    /** Telephone numbers keep their separators; only the allowed characters and digit count are checked. */
    private fun phoneFailure(value: String): QrFailure? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.any { it !in PHONE_ALLOWED }) return QrFailure.INVALID_PHONE
        if (trimmed.count { it.isDigit() } < MIN_PHONE_DIGITS) return QrFailure.INVALID_PHONE
        return null
    }

    private fun emailFailure(value: String): QrFailure? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() || isControl(it) }) return QrFailure.INVALID_EMAIL
        val at = trimmed.indexOf('@')
        if (at <= 0 || at != trimmed.lastIndexOf('@')) return QrFailure.INVALID_EMAIL
        val domain = trimmed.substring(at + 1)
        if (domain.isEmpty() || !domain.contains('.') || domain.startsWith('.') ||
            domain.endsWith('.') || domain.contains("..")) return QrFailure.INVALID_EMAIL
        return if (isUtf8(trimmed)) null else QrFailure.INVALID_EMAIL
    }

    private fun parseEventMoment(date: String, time: String): LocalDateTime? {
        val day = try {
            LocalDate.parse(date.trim(), EVENT_DATE)
        } catch (_: DateTimeParseException) {
            return null
        }
        if (day.year !in MIN_EVENT_YEAR..MAX_EVENT_YEAR) return null
        val clock = try {
            LocalTime.parse(time.trim(), EVENT_TIME)
        } catch (_: DateTimeParseException) {
            return null
        }
        return LocalDateTime.of(day, clock)
    }

    private fun wifiContent(input: QrInput): String {
        if (input.ssid.isEmpty()) return ""
        return buildString {
            append("WIFI:T:").append(input.security).append(";S:").append(escapeWifi(input.ssid)).append(';')
            if (input.security != "nopass") append("P:").append(escapeWifi(input.password)).append(';')
            if (input.security in setOf("WPA-EAP", "WPA3-EAP")) {
                append("E:").append(input.eap).append(';')
                if (input.identity.isNotEmpty()) append("I:").append(escapeWifi(input.identity)).append(';')
                if (input.anonymous.isNotEmpty()) append("A:").append(escapeWifi(input.anonymous)).append(';')
                if (input.phase2.isNotEmpty()) append("PH2:").append(input.phase2).append(';')
            }
            append("H:").append(input.hidden).append(";;")
        }
    }

    /** RFC 6068 mailto with optional hfields; values are percent-encoded as the standard requires. */
    private fun emailContent(form: QrFormInput): String {
        val base = "mailto:" + form.email.trim()
        val fields = listOf(
            "subject" to rfc6068Value(form.emailSubject.trim()),
            "body" to rfc6068Value(form.emailBody),
            "cc" to emailRecipients(form.emailCc),
            "bcc" to emailRecipients(form.emailBcc),
        ).filter { (_, value) -> value.isNotEmpty() }
        return if (fields.isEmpty()) base else
            base + "?" + fields.joinToString("&") { (name, value) -> "$name=$value" }
    }

    /** Keeps the comma separators literal and percent-encodes each recipient. */
    private fun emailRecipients(value: String): String =
        if (value.isBlank()) "" else value.split(',').joinToString(",") { rfc6068Value(it.trim()) }

    private fun rfc6068Value(value: String): String = buildString(value.length) {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            val readable = (code in 0x30..0x39) || (code in 0x41..0x5A) || (code in 0x61..0x7A) ||
                code == 0x2D || code == 0x2E || code == 0x5F || code == 0x7E
            if (readable) append(code.toChar()) else {
                append('%')
                append(HEX[code ushr 4])
                append(HEX[code and 0x0F])
            }
        }
    }

    /**
     * MECARD as produced by ZXing's MECARDContactEncoder: backslash, colon and semicolon are escaped
     * with a backslash, line breaks are dropped and the record ends with an extra semicolon.
     */
    private fun mecard(contact: QrContactInput): String = buildString {
        append("MECARD:N:").append(mecardValue(contact.name.trim())).append(';')
        if (contact.organization.isNotBlank()) {
            append("ORG:").append(mecardValue(contact.organization.trim())).append(';')
        }
        contact.phones.filter { it.isNotBlank() }.forEach {
            append("TEL:").append(mecardValue(it.trim())).append(';')
        }
        contact.emails.filter { it.isNotBlank() }.forEach {
            append("EMAIL:").append(mecardValue(it.trim())).append(';')
        }
        append(';')
    }

    /**
     * vCard 3.0: one row per non-empty phone, email or address. Addresses use the structured ADR
     * field with empty components, matching the shipped web payload; MECARD cannot carry them.
     */
    private fun vcard(contact: QrContactInput): String = buildString {
        append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
        append("FN:").append(icalValue(contact.name.trim())).append("\r\n")
        if (contact.organization.isNotBlank()) {
            append("ORG:").append(icalValue(contact.organization.trim())).append("\r\n")
        }
        contact.phones.filter { it.isNotBlank() }.forEach {
            append("TEL;TYPE=CELL:").append(icalValue(it.trim())).append("\r\n")
        }
        contact.emails.filter { it.isNotBlank() }.forEach {
            append("EMAIL;TYPE=INTERNET:").append(icalValue(it.trim())).append("\r\n")
        }
        contact.addresses.filter { it.isNotBlank() }.forEach {
            append("ADR;TYPE=HOME:;;").append(icalValue(it.trim())).append(";;;;\r\n")
        }
        append("END:VCARD")
    }

    private fun mecardValue(value: String): String = buildString(value.length) {
        for (char in value) {
            when {
                char == '\\' || char == ':' || char == ';' -> {
                    append('\\')
                    append(char)
                }
                char == '\n' || char == '\r' -> Unit
                else -> append(char)
            }
        }
    }

    private fun vevent(event: QrEventInput, zone: ZoneId): String = buildString {
        append(VEVENT)
        append("SUMMARY:").append(icalValue(event.title.trim())).append("\r\n")
        if (event.location.isNotBlank()) {
            append("LOCATION:").append(icalValue(event.location.trim())).append("\r\n")
        }
        if (event.description.isNotBlank()) {
            append("DESCRIPTION:").append(icalValue(event.description.trim())).append("\r\n")
        }
        append("DTSTART:").append(icalStamp(event.startDate, event.startTime, event.timeMode, zone)).append("\r\n")
        if (event.endDate.isNotBlank()) {
            append("DTEND:").append(icalStamp(event.endDate, event.endTime, event.timeMode, zone)).append("\r\n")
        }
        append(reminderBlock(event))
        append("END:VEVENT")
    }

    private fun icalStamp(date: String, time: String, mode: QrEventTimeMode, zone: ZoneId): String {
        val day = LocalDate.parse(date.trim(), EVENT_DATE)
        val clock = LocalTime.parse(time.trim(), EVENT_TIME)
        val floating = day.format(EVENT_DATE_OUT) + "T" + clock.format(EVENT_TIME_OUT)
        if (mode == QrEventTimeMode.FLOATING) return floating
        // The earlier offset is used for ambiguous fall-back times; gaps are rejected by validate().
        val local = LocalDateTime.of(day, clock)
        val offset = zone.rules.getValidOffsets(local).firstOrNull() ?: return floating
        return EVENT_UTC_OUT.format(local.toInstant(offset))
    }

    /** DISPLAY alarm relative to DTSTART; absent when the user keeps the default reminder. */
    private fun reminderBlock(event: QrEventInput): String {
        val trigger = when (event.reminder) {
            QrEventReminder.NONE -> return ""
            QrEventReminder.AT_START -> "PT0S"
            QrEventReminder.MINUTES_5 -> "-PT5M"
            QrEventReminder.MINUTES_15 -> "-PT15M"
            QrEventReminder.MINUTES_30 -> "-PT30M"
            QrEventReminder.HOUR_1 -> "-PT1H"
            QrEventReminder.DAY_1 -> "-P1D"
        }
        return buildString {
            append("BEGIN:VALARM\r\n")
            append("ACTION:DISPLAY\r\n")
            append("DESCRIPTION:").append(icalValue(event.title.trim())).append("\r\n")
            append("TRIGGER:").append(trigger).append("\r\n")
            append("END:VALARM\r\n")
        }
    }

    /** iCalendar TEXT escaping: backslash, semicolon, comma and line breaks. */
    private fun icalValue(value: String): String = buildString(value.length) {
        for (char in value) {
            when (char) {
                '\\' -> append("\\\\")
                ';' -> append("\\;")
                ',' -> append("\\,")
                '\n' -> append("\\n")
                '\r' -> Unit
                else -> append(char)
            }
        }
    }

    // Escape each Wi-Fi source character once, including the escape character itself.
    private fun escapeWifi(value: String): String = buildString(value.length) {
        for (char in value) {
            if (char in "\\;,:\"") append('\\')
            append(char)
        }
    }

    private fun isControl(char: Char): Boolean = char.code < 32 || char.code == 127
    fun isUtf8(value: String): Boolean = Charsets.UTF_8.newEncoder().canEncode(value)

    private const val HEX = "0123456789ABCDEF"
}
