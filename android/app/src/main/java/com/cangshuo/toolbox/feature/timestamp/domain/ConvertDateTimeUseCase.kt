package com.cangshuo.toolbox.feature.timestamp.domain

import java.time.DateTimeException
import java.time.ZoneId
import java.time.format.DateTimeParseException

class ConvertDateTimeUseCase(private val repository: TimestampRepository) {
    operator fun invoke(input: String, zoneId: ZoneId): TimestampResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return TimestampResult.Empty
        return try {
            val conversion = repository.convertDateTime(trimmed, zoneId)
            TimestampResult.Success(conversion)
        } catch (_: InvalidDateTimeException) {
            TimestampResult.InvalidDate
        } catch (_: DateTimeParseException) {
            TimestampResult.InvalidFormat
        } catch (_: DateTimeException) {
            TimestampResult.OutOfRange
        } catch (_: IllegalArgumentException) {
            TimestampResult.InvalidFormat
        }
    }
}
