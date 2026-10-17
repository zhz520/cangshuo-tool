package com.cangshuo.toolbox.feature.worldclock.ui

import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockHourFormat
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockReading
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockZone

enum class WorldClockError {
    DUPLICATE_ZONE,
    ZONE_LIMIT,
    INVALID_ZONE,
    QUERY_TOO_LONG,
    READ_FAILED,
}

data class WorldClockUiState(
    val systemZoneId: String = "",
    val systemReading: WorldClockReading? = null,
    val readings: List<WorldClockReading> = emptyList(),
    val addedZoneIds: List<String> = emptyList(),
    val hourFormat: WorldClockHourFormat = WorldClockHourFormat.HOUR_24,
    val query: String = "",
    val searchResults: List<WorldClockZone> = emptyList(),
    val allZoneCount: Int = 0,
    val error: WorldClockError? = null,
)
