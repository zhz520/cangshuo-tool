package com.cangshuo.toolbox.feature.timescreen

import com.cangshuo.toolbox.feature.timescreen.domain.TimeScreenPolicy
import com.cangshuo.toolbox.feature.timescreen.domain.TimeScreenSettings
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeScreenPolicyTest {
    private val time = ZonedDateTime.of(2026, 10, 17, 21, 5, 7, 250_000_000, ZoneId.of("Asia/Shanghai"))

    @Test fun twentyFourAndTwelveHourFormatsHonorSeconds() {
        val settings = TimeScreenSettings()
        assertEquals("21:05:07", TimeScreenPolicy.snapshot(time, settings, Locale.US).clock.main)
        assertEquals("21:05", TimeScreenPolicy.snapshot(time, settings.copy(showSeconds = false), Locale.US).clock.main)
        val twelve = TimeScreenPolicy.snapshot(time, settings.copy(use24Hour = false), Locale.US)
        assertEquals("9:05:07", twelve.clock.main)
        assertEquals("PM", twelve.clock.period)
        assertEquals("Asia/Shanghai", time.zone.id)
    }

    @Test fun snapshotProvidesLocalizedDateZoneAndTime() {
        val snapshot = TimeScreenPolicy.snapshot(time, TimeScreenSettings(), Locale.US)
        assertTrue(snapshot.date.contains("2026"))
        assertTrue(snapshot.zone.isNotBlank())
        assertEquals(time, snapshot.now)
    }

    @Test fun driftIsBoundedAndStepsEveryTenSeconds() {
        val first = TimeScreenPolicy.driftOffset(1_000L, 8)
        val sameStep = TimeScreenPolicy.driftOffset(1_009L, 8)
        assertEquals(first, sameStep)
        for (second in 0L..720L step 10) {
            val point = TimeScreenPolicy.driftOffset(second, 8)
            assertTrue(kotlin.math.hypot(point.xDp, point.yDp) <= 8.001f)
        }
        assertEquals(TimeScreenPolicy.driftOffset(0L, 2), TimeScreenPolicy.driftOffset(240L, 2))
    }

    @Test fun driftRadiusAndSettingsAreBounded() {
        val point = TimeScreenPolicy.driftOffset(0L, 99)
        assertEquals(TimeScreenPolicy.MAX_DRIFT_RADIUS.toFloat(), point.xDp, 0.001f)
        val normalized = TimeScreenPolicy.normalize(TimeScreenSettings(driftRadiusDp = -5))
        assertEquals(TimeScreenPolicy.MIN_DRIFT_RADIUS, normalized.driftRadiusDp)
    }

    @Test fun tickerAimsAtNextSecondOrMinuteAndNeverBusyLoops() {
        assertEquals(750L, TimeScreenPolicy.nextDelayMillis(time, showSeconds = true))
        assertEquals(52_750L, TimeScreenPolicy.nextDelayMillis(time, showSeconds = false))
        val almostNext = ZonedDateTime.of(2026, 10, 17, 21, 5, 59, 999_000_000, ZoneId.of("Asia/Shanghai"))
        assertTrue(TimeScreenPolicy.nextDelayMillis(almostNext, showSeconds = true) >= 100L)
        assertTrue(TimeScreenPolicy.nextDelayMillis(almostNext, showSeconds = false) >= 100L)
    }
}
