package com.cangshuo.toolbox.feature.worldclock.data

import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class LocalWorldClockRepository(
    private val clock: Clock = Clock.systemUTC(),
    private val systemZone: () -> ZoneId = ZoneId::systemDefault,
    private val zones: () -> Set<String> = ZoneId::getAvailableZoneIds,
) : WorldClockRepository {
    override fun now(): Instant = clock.instant()

    override fun systemZoneId(): String = systemZone().id

    override fun availableZoneIds(): Set<String> = zones()
}
