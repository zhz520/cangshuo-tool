package com.cangshuo.toolbox.feature.timestamp.domain

import java.time.Instant
import java.time.ZoneId

class GetCurrentTimeUseCase(private val repository: TimestampRepository) {
    fun getInstant(): Instant = repository.getCurrentInstant()

    fun getConversion(zoneId: ZoneId): TimestampConversion {
        val now = repository.getCurrentInstant()
        return repository.convertTimestamp(now.toEpochMilli(), TimestampUnit.MILLISECONDS, zoneId)
    }
}
