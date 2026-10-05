package com.cangshuo.toolbox.feature.qr.domain

enum class QrMode { GENERATE, DECODE, HISTORY }
enum class QrContentType { TEXT, WIFI, PHONE, EMAIL, SMS, CONTACT, EVENT }

/** Generation formats; [linear] writers produce a single-row bar matrix instead of a QR square. */
enum class QrCodeFormat(val linear: Boolean) {
    QR_CODE(false), CODE_128(true), CODE_39(true), CODE_93(true),
    EAN_13(true), EAN_8(true), UPC_A(true), ITF(true), CODABAR(true),
}

enum class QrErrorCorrection { LOW, MEDIUM, QUARTILE, HIGH }

enum class QrColorStyle(val darkColorArgb: Long, val lightColorArgb: Long) {
    BLACK_WHITE(0xFF000000, 0xFFFFFFFF),
    NAVY_BLUE(0xFF0D47A1, 0xFFFFFFFF),
    EMERALD_GREEN(0xFF1B5E20, 0xFFFFFFFF),
    PURPLE(0xFF4A148C, 0xFFFFFFFF),
}

data class QrInput(
    val type: QrContentType = QrContentType.TEXT,
    val text: String = "",
    val ssid: String = "",
    val password: String = "",
    val security: String = "WPA",
    val hidden: Boolean = false,
    val form: QrFormInput = QrFormInput(),
    val format: QrCodeFormat = QrCodeFormat.QR_CODE,
    val eap: String = "TTLS",
    val phase2: String = "MSCHAPV2",
    val identity: String = "",
    val anonymous: String = "",
)

/** Contact output format; MECARD is compact, vCard 3.0 additionally carries addresses. */
enum class QrContactFormat { MECARD, VCARD }

/** Contact fields; each list keeps its rows, blanks are ignored on output and never rewritten. */
data class QrContactInput(
    val name: String = "",
    val organization: String = "",
    val format: QrContactFormat = QrContactFormat.MECARD,
    val phones: List<String> = listOf(""),
    val emails: List<String> = listOf(""),
    val addresses: List<String> = listOf(""),
)

/** How event timestamps are written into iCalendar; floating values carry no time zone. */
enum class QrEventTimeMode { FLOATING, DEVICE_TO_UTC }

/** Optional DISPLAY alarm relative to the event start; NONE omits the VALARM block. */
enum class QrEventReminder { NONE, AT_START, MINUTES_5, MINUTES_15, MINUTES_30, HOUR_1, DAY_1 }

/** Calendar event fields; [timeMode] decides floating local time or a device-zone UTC conversion. */
data class QrEventInput(
    val title: String = "",
    val location: String = "",
    val description: String = "",
    val startDate: String = "",
    val startTime: String = "",
    val endDate: String = "",
    val endTime: String = "",
    val timeMode: QrEventTimeMode = QrEventTimeMode.FLOATING,
    val reminder: QrEventReminder = QrEventReminder.NONE,
)

/** Fields of the structured content types; TEXT and WIFI keep their own properties. */
data class QrFormInput(
    val phone: String = "",
    val email: String = "",
    val emailSubject: String = "",
    val emailBody: String = "",
    val emailCc: String = "",
    val emailBcc: String = "",
    val smsNumber: String = "",
    val smsMessage: String = "",
    val contact: QrContactInput = QrContactInput(),
    val event: QrEventInput = QrEventInput(),
)

enum class QrFailure {
    INPUT_TOO_LONG, INVALID_TEXT, INVALID_WIFI, SSID_TOO_LONG, PASSWORD_REQUIRED,
    INVALID_PHONE, INVALID_EMAIL, INVALID_SMS, INVALID_CONTACT, INVALID_EVENT, EVENT_RANGE, EVENT_TIME_GAP,
    INVALID_BARCODE,
    CAPACITY_EXCEEDED, GENERATE_FAILED, INVALID_IMAGE, IMAGE_TOO_LARGE,
    MEMORY_LIMIT, IMAGE_READ_FAILED, NOT_FOUND, DAMAGED_CODE, DECODE_FAILED,
}

data class QrMatrix(val width: Int, val height: Int, val data: List<BooleanArray>)

sealed interface QrGenerateResult {
    data class Success(val matrix: QrMatrix, val content: String) : QrGenerateResult
    data class Error(val reason: QrFailure) : QrGenerateResult
    data object Empty : QrGenerateResult
}

sealed interface QrDecodeResult {
    data class Success(val entry: QrDecodeEntry) : QrDecodeResult
    /** Two or more QR codes found in one image, in the order ZXing detected them. */
    data class Multiple(val entries: List<QrDecodeEntry>) : QrDecodeResult
    data class Error(val reason: QrFailure) : QrDecodeResult
}

/** Decoded payload together with the symbology ZXing reported. */
data class QrDecodeEntry(val text: String, val symbology: QrSymbology)

/** Symbologies offered by [QrBarcodeFormats]; [UNKNOWN] covers formats ZXing did not report. */
enum class QrSymbology {
    UNKNOWN, QR_CODE, DATA_MATRIX, AZTEC, PDF_417, CODE_128, CODE_93, CODE_39, CODABAR, ITF,
    EAN_13, EAN_8, UPC_A, UPC_E,
}
