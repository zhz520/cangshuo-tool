package com.cangshuo.toolbox.feature.timestamp.domain

import java.time.Instant
import java.time.ZoneId

data class TimestampConversion(
    val epochSeconds: Long,
    val epochMillis: Long,
    val formattedDateTime: String,
    val iso8601: String,
    val zoneName: String,
)

sealed interface TimestampResult {
    data class Success(val conversion: TimestampConversion) : TimestampResult
    data object Empty : TimestampResult
    data object InvalidFormat : TimestampResult
    /** The text matches a supported format shape but names a date/time that does not exist. */
    data object InvalidDate : TimestampResult
    data object OutOfRange : TimestampResult
}

/** Thrown for a recognized format whose calendar or local-time value does not exist. */
class InvalidDateTimeException : Exception("Date or time does not exist")

interface TimestampRepository {
    fun getCurrentInstant(): Instant
    fun convertTimestamp(epochValue: Long, unit: TimestampUnit, zoneId: ZoneId): TimestampConversion
    fun convertDateTime(dateTimeStr: String, zoneId: ZoneId): TimestampConversion
}
