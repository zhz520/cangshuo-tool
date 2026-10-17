package com.cangshuo.toolbox.feature.worldclock

import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockDstState
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockHourFormat
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockPolicy
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockUseCases
import com.cangshuo.toolbox.feature.worldclock.domain.WorldClockRepository
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldClockPolicyTest {
    private val zones = setOf(
        "UTC",
        "Asia/Shanghai",
        "Asia/Tokyo",
        "Asia/Kathmandu",
        "Europe/London",
        "America/New_York",
        "Pacific/Kiritimati",
        "Pacific/Honolulu",
        "Pacific/Pago_Pago",
    )
    private val useCases = WorldClockUseCases(object : WorldClockRepository {
        override fun now(): Instant = Instant.parse("2026-07-01T00:00:00Z")
        override fun systemZoneId(): String = "UTC"
        override fun availableZoneIds(): Set<String> = zones
    })

    @Test
    fun readingsConvertTimeOffsetDayDeltaAndDst() {
        val readings = useCases.readings(
            zoneIds = listOf("Asia/Shanghai", "Europe/London", "America/New_York"),
            instant = Instant.parse("2026-07-01T00:00:00Z"),
            referenceZoneId = "UTC",
            locale = Locale.US,
            hourFormat = WorldClockHourFormat.HOUR_24,
        )
        assertEquals(3, readings.size)

        val shanghai = readings[0]
        assertEquals("08:00:00", shanghai.time)
        assertEquals("UTC+08:00", shanghai.utcOffset)
        assertEquals(0L, shanghai.dayDelta)
        assertEquals(WorldClockDstState.FIXED_OFFSET, shanghai.dstState)

        val london = readings[1]
        assertEquals("01:00:00", london.time)
        assertEquals("UTC+01:00", london.utcOffset)
        assertEquals(WorldClockDstState.DAYLIGHT, london.dstState)

        val newYork = readings[2]
        assertEquals("20:00:00", newYork.time)
        assertEquals("UTC-04:00", newYork.utcOffset)
        assertEquals(-1L, newYork.dayDelta)
        assertEquals(WorldClockDstState.DAYLIGHT, newYork.dstState)
        assertNotNull(newYork.nextTransition)
        assertTrue(newYork.nextTransition!!.newOffset == "UTC-05:00")
    }

    @Test
    fun winterReadingReportsStandardTimeAndNextDaylightTransition() {
        val reading = WorldClockPolicy.reading(
            zoneId = "America/New_York",
            instant = Instant.parse("2026-01-15T12:00:00Z"),
            referenceDate = java.time.LocalDate.of(2026, 1, 15),
            locale = Locale.US,
            hourFormat = WorldClockHourFormat.HOUR_12,
        )
        assertEquals(WorldClockDstState.STANDARD, reading.dstState)
        assertTrue(reading.time.endsWith("AM"))
        assertEquals("UTC-04:00", reading.nextTransition?.newOffset)
    }

    @Test
    fun twelveHourFormatHandlesMidnightAndNoon() {
        val midnight = WorldClockPolicy.reading(
            zoneId = "UTC",
            instant = Instant.parse("2026-01-15T00:00:00Z"),
            referenceDate = java.time.LocalDate.of(2026, 1, 15),
            locale = Locale.US,
            hourFormat = WorldClockHourFormat.HOUR_12,
        )
        val noon = WorldClockPolicy.reading(
            zoneId = "UTC",
            instant = Instant.parse("2026-01-15T12:00:00Z"),
            referenceDate = java.time.LocalDate.of(2026, 1, 15),
            locale = Locale.US,
            hourFormat = WorldClockHourFormat.HOUR_12,
        )
        assertEquals("12:00:00 AM", midnight.time)
        assertEquals("12:00:00 PM", noon.time)
    }

    @Test
    fun dateDeltaUsesReferenceZoneCalendarDate() {
        val readings = useCases.readings(
            zoneIds = listOf("Pacific/Kiritimati", "Pacific/Honolulu", "Pacific/Pago_Pago"),
            instant = Instant.parse("2026-07-01T10:30:00Z"),
            referenceZoneId = "Pacific/Kiritimati",
            locale = Locale.US,
            hourFormat = WorldClockHourFormat.HOUR_24,
        )
        assertEquals(0L, readings[0].dayDelta)
        assertEquals(-1L, readings[1].dayDelta)
        assertEquals(-2L, readings[2].dayDelta)
    }

    @Test
    fun daylightSavingBoundaryUsesRuleOffsetAtTheInstant() {
        val beforeStart = WorldClockPolicy.reading(
            zoneId = "America/New_York",
            instant = Instant.parse("2026-03-08T06:59:59Z"),
            referenceDate = java.time.LocalDate.of(2026, 3, 8),
            locale = Locale.US,
            hourFormat = WorldClockHourFormat.HOUR_24,
        )
        val atStart = WorldClockPolicy.reading(
            zoneId = "America/New_York",
            instant = Instant.parse("2026-03-08T07:00:00Z"),
            referenceDate = java.time.LocalDate.of(2026, 3, 8),
            locale = Locale.US,
            hourFormat = WorldClockHourFormat.HOUR_24,
        )
        val beforeEnd = WorldClockPolicy.reading(
            zoneId = "America/New_York",
            instant = Instant.parse("2026-11-01T05:59:59Z"),
            referenceDate = java.time.LocalDate.of(2026, 11, 1),
            locale = Locale.US,
            hourFormat = WorldClockHourFormat.HOUR_24,
        )
        val atEnd = WorldClockPolicy.reading(
            zoneId = "America/New_York",
            instant = Instant.parse("2026-11-01T06:00:00Z"),
            referenceDate = java.time.LocalDate.of(2026, 11, 1),
            locale = Locale.US,
            hourFormat = WorldClockHourFormat.HOUR_24,
        )
        assertEquals("UTC-05:00", beforeStart.utcOffset)
        assertEquals(WorldClockDstState.STANDARD, beforeStart.dstState)
        assertEquals("UTC-04:00", atStart.utcOffset)
        assertEquals(WorldClockDstState.DAYLIGHT, atStart.dstState)
        assertEquals("UTC-04:00", beforeEnd.utcOffset)
        assertEquals(WorldClockDstState.DAYLIGHT, beforeEnd.dstState)
        assertEquals("UTC-05:00", atEnd.utcOffset)
        assertEquals(WorldClockDstState.STANDARD, atEnd.dstState)
    }

    @Test
    fun offsetsIncludeQuarterHourAndNegativeZones() {
        assertEquals("UTC+05:45", WorldClockPolicy.formatOffset(5 * 3_600 + 45 * 60))
        assertEquals("UTC-03:30", WorldClockPolicy.formatOffset(-(3 * 3_600 + 30 * 60)))
        assertEquals("UTC+00:00", WorldClockPolicy.formatOffset(0))
    }

    @Test
    fun zoneSearchSupportsChineseAliasesSpacesAndUnderscores() {
        val options = useCases.zoneOptions(Locale.US)
        assertEquals("Asia/Shanghai", options.first().id)
        assertTrue(useCases.filterZones(options, "纽约").any { it.id == "America/New_York" })
        assertTrue(useCases.filterZones(options, "new_york").any { it.id == "America/New_York" })
        assertTrue(useCases.filterZones(options, "NEW YORK").any { it.id == "America/New_York" })
        assertTrue(useCases.filterZones(options, "东京").any { it.id == "Asia/Tokyo" })
        assertTrue(useCases.filterZones(options, "东京 日本").any { it.id == "Asia/Tokyo" })
        assertTrue(useCases.filterZones(options, "东").any { it.id == "Asia/Tokyo" })
        assertTrue(useCases.filterZones(options, "not-a-zone").isEmpty())
        assertEquals(options, useCases.filterZones(options, "  "))
    }

    @Test
    fun savedZonesAreValidatedDeduplicatedAndBounded() {
        val ids = listOf(
            "Asia/Shanghai",
            "Asia/Tokyo",
            "Asia/Tokyo",
            "bad/zone",
            "UTC",
        ) + zones.filterNot { it == "Asia/Shanghai" || it == "UTC" }
        val result = WorldClockPolicy.sanitize(ids, "Asia/Shanghai")
        assertEquals(result.distinct(), result)
        assertFalse("Asia/Shanghai" in result)
        assertTrue(result.size <= WorldClockPolicy.MAX_TOTAL_ZONES - 1)
        assertEquals(result, WorldClockPolicy.decode(WorldClockPolicy.encode(result)))
    }

    @Test
    fun invalidZoneIdsAreRejectedWithoutThrowing() {
        assertEquals(null, WorldClockPolicy.validatedId(""))
        assertEquals(null, WorldClockPolicy.validatedId(" "))
        assertEquals(null, WorldClockPolicy.validatedId("Mars/Olympus"))
        assertEquals("Asia/Tokyo", WorldClockPolicy.validatedId(" Asia/Tokyo "))
        assertNotNull(ZoneId.of("Asia/Tokyo"))
    }
}
