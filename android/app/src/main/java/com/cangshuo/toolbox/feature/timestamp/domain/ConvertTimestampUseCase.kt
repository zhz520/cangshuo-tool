package com.cangshuo.toolbox.feature.timestamp.domain

import java.time.DateTimeException
import java.time.ZoneId

class ConvertTimestampUseCase(private val repository: TimestampRepository) {
    operator fun invoke(input: String, unit: TimestampUnit, zoneId: ZoneId): TimestampResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return TimestampResult.Empty
        val epoch = trimmed.toLongOrNull() ?: return TimestampResult.InvalidFormat
        // Sanity limit: year roughly between 1900 and 3000
        val seconds = if (unit == TimestampUnit.SECONDS) epoch else epoch / 1000
        if (seconds < -2208988800L || seconds > 32503680000L) {
            return TimestampResult.OutOfRange
        }
        return try {
            val conversion = repository.convertTimestamp(epoch, unit, zoneId)
            TimestampResult.Success(conversion)
        } catch (_: DateTimeException) {
            TimestampResult.OutOfRange
        } catch (_: ArithmeticException) {
            TimestampResult.OutOfRange
        } catch (_: IllegalArgumentException) {
            TimestampResult.OutOfRange
        }
    }
}
