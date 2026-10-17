package com.cangshuo.toolbox.feature.timescreen.domain

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

data class TimeScreenSettings(
    val use24Hour: Boolean = true,
    val showSeconds: Boolean = true,
    val showDate: Boolean = true,
    val showZone: Boolean = true,
    val antiBurnIn: Boolean = true,
    val driftRadiusDp: Int = 8,
    val keepScreenOn: Boolean = true,
)

data class ClockFaceText(val main: String, val period: String?)

data class TimeScreenSnapshot(
    val now: ZonedDateTime,
    val clock: ClockFaceText,
    val date: String,
    val zone: String,
)

data class DriftOffset(val xDp: Float, val yDp: Float)

interface TimeScreenRepository {
    fun now(): ZonedDateTime

    fun locale(): Locale
}

class ReadTimeScreenUseCase(private val repository: TimeScreenRepository) {
    fun read(settings: TimeScreenSettings): TimeScreenSnapshot =
        TimeScreenPolicy.snapshot(repository.now(), settings, repository.locale())

    fun nextDelayMillis(showSeconds: Boolean, now: ZonedDateTime = repository.now()): Long =
        TimeScreenPolicy.nextDelayMillis(now, showSeconds)
}

object TimeScreenPolicy {
    const val MIN_DRIFT_RADIUS = 2
    const val MAX_DRIFT_RADIUS = 16
    const val DRIFT_CYCLE_SECONDS = 240
    const val DRIFT_STEP_SECONDS = 10

    fun normalize(settings: TimeScreenSettings): TimeScreenSettings = settings.copy(
        driftRadiusDp = settings.driftRadiusDp.coerceIn(MIN_DRIFT_RADIUS, MAX_DRIFT_RADIUS),
    )

    fun snapshot(now: ZonedDateTime, settings: TimeScreenSettings, locale: Locale): TimeScreenSnapshot {
        val pattern = when {
            settings.use24Hour && settings.showSeconds -> "HH:mm:ss"
            settings.use24Hour -> "HH:mm"
            settings.showSeconds -> "h:mm:ss"
            else -> "h:mm"
        }
        val main = DateTimeFormatter.ofPattern(pattern, locale).format(now)
        val period = if (settings.use24Hour) null else DateTimeFormatter.ofPattern("a", locale).format(now)
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(now)
        val zone = now.zone.getDisplayName(TextStyle.SHORT, locale)
            .ifBlank { now.zone.id }
        return TimeScreenSnapshot(now, ClockFaceText(main, period), date, zone)
    }

    /** Ten-second stepped circular drift; deterministic and bounded for tests and burn-in UIs. */
    fun driftOffset(epochSecond: Long, radiusDp: Int): DriftOffset {
        val radius = radiusDp.coerceIn(MIN_DRIFT_RADIUS, MAX_DRIFT_RADIUS).toFloat()
        val stepped = (epochSecond / DRIFT_STEP_SECONDS) * DRIFT_STEP_SECONDS
        val radians = (stepped % DRIFT_CYCLE_SECONDS).toDouble() / DRIFT_CYCLE_SECONDS * Math.PI * 2.0
        return DriftOffset(
            xDp = (cos(radians) * radius).toFloat(),
            yDp = (sin(radians) * radius).toFloat(),
        )
    }

    fun nextDelayMillis(now: ZonedDateTime, showSeconds: Boolean): Long {
        val millisIntoMinute = now.second * 1_000L + now.nano / 1_000_000L
        val target = if (showSeconds) {
            1_000L - now.nano / 1_000_000L
        } else {
            60_000L - millisIntoMinute
        }
        return target.coerceIn(100L, 60_000L)
    }
}
