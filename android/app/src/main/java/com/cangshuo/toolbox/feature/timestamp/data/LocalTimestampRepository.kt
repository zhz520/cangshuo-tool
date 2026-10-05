package com.cangshuo.toolbox.feature.timestamp.data

import com.cangshuo.toolbox.feature.timestamp.domain.InvalidDateTimeException
import com.cangshuo.toolbox.feature.timestamp.domain.TimestampConversion
import com.cangshuo.toolbox.feature.timestamp.domain.TimestampRepository
import com.cangshuo.toolbox.feature.timestamp.domain.TimestampUnit
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

class LocalTimestampRepository : TimestampRepository {

    override fun getCurrentInstant(): Instant = Instant.now()

    override fun convertTimestamp(
        epochValue: Long,
        unit: TimestampUnit,
        zoneId: ZoneId,
    ): TimestampConversion {
        val instant = when (unit) {
            TimestampUnit.SECONDS -> Instant.ofEpochSecond(epochValue)
            TimestampUnit.MILLISECONDS -> Instant.ofEpochMilli(epochValue)
        }
        val zdt = instant.atZone(zoneId)
        return buildConversion(instant, zdt)
    }

    override fun convertDateTime(
        dateTimeStr: String,
        zoneId: ZoneId,
    ): TimestampConversion {
        val normalized = dateTimeStr.trim()
        val zdt = parseDateTime(normalized, zoneId)
        val instant = zdt.toInstant()
        return buildConversion(instant, zdt)
    }

    /**
     * Parses only values that exist. Strict mode rejects 2026-02-30 instead of SMART
     * resolution rewriting it to 2026-02-28; the lenient retry only classifies the
     * failure as "recognized shape, invalid value" so the UI can explain it.
     */
    private fun parseDateTime(text: String, zoneId: ZoneId): ZonedDateTime {
        var recognizedButInvalid = false

        for (formatters in DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(text, formatters.strict)
                    .atExistingZone(zoneId)
                    .requireSupportedYear()
            } catch (_: DateTimeParseException) {
                if (canParse { LocalDateTime.parse(text, formatters.lenient) }) recognizedButInvalid = true
            }
        }

        for (formatters in DATE_ONLY_FORMATTERS) {
            try {
                return LocalDate.parse(text, formatters.strict)
                    .atStartOfDay(zoneId)
                    .requireSupportedYear()
            } catch (_: DateTimeParseException) {
                if (canParse { LocalDate.parse(text, formatters.lenient) }) recognizedButInvalid = true
            }
        }

        try {
            return ZonedDateTime.parse(text, ISO_DATE_TIME_STRICT).requireSupportedYear()
        } catch (_: DateTimeParseException) {
            if (canParse { ZonedDateTime.parse(text, ISO_DATE_TIME_LENIENT) }) recognizedButInvalid = true
        }

        if (recognizedButInvalid) throw InvalidDateTimeException()
        throw DateTimeParseException("Unrecognized date-time format", text, 0)
    }

    /**
     * Rejects local times skipped by a daylight-saving jump. Overlapping local times
     * keep the earlier offset, matching ZonedDateTime.atZone.
     */
    private fun LocalDateTime.atExistingZone(zoneId: ZoneId): ZonedDateTime {
        if (zoneId.rules.getValidOffsets(this).isEmpty()) throw InvalidDateTimeException()
        return atZone(zoneId)
    }

    private fun ZonedDateTime.requireSupportedYear(): ZonedDateTime = apply {
        if (year !in MIN_SUPPORTED_YEAR..MAX_SUPPORTED_YEAR) {
            throw DateTimeException("Year outside supported range")
        }
    }

    private inline fun canParse(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (_: DateTimeException) {
        false
    }

    private fun buildConversion(instant: Instant, zdt: ZonedDateTime): TimestampConversion {
        val formatted = zdt.format(STANDARD_FORMATTER)
        val iso = zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        return TimestampConversion(
            epochSeconds = instant.epochSecond,
            epochMillis = instant.toEpochMilli(),
            formattedDateTime = formatted,
            iso8601 = iso,
            zoneName = zdt.zone.id,
        )
    }

    private data class FormatterPair(
        val strict: DateTimeFormatter,
        val lenient: DateTimeFormatter,
    )

    private companion object {
        const val MIN_SUPPORTED_YEAR = 1900
        const val MAX_SUPPORTED_YEAR = 3000

        val STANDARD_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)

        fun pair(pattern: String): FormatterPair = FormatterPair(
            strict = DateTimeFormatter.ofPattern(pattern, Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT),
            lenient = DateTimeFormatter.ofPattern(pattern, Locale.ROOT)
                .withResolverStyle(ResolverStyle.LENIENT),
        )

        val DATE_TIME_FORMATTERS = listOf(
            "uuuu-MM-dd HH:mm:ss",
            "uuuu/MM/dd HH:mm:ss",
            "uuuu-MM-dd'T'HH:mm:ss",
            "uuuu-MM-dd HH:mm",
            "uuuu/MM/dd HH:mm",
        ).map(::pair)

        val DATE_ONLY_FORMATTERS = listOf(
            "uuuu-MM-dd",
            "uuuu/MM/dd",
        ).map(::pair)

        val ISO_DATE_TIME_STRICT: DateTimeFormatter = DateTimeFormatter.ISO_ZONED_DATE_TIME
        val ISO_DATE_TIME_LENIENT: DateTimeFormatter =
            DateTimeFormatter.ISO_ZONED_DATE_TIME.withResolverStyle(ResolverStyle.LENIENT)
    }
}
