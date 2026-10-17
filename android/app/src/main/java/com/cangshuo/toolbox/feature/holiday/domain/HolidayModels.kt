package com.cangshuo.toolbox.feature.holiday.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class HolidayKind { SOLAR, LUNAR, SOLAR_TERM }

enum class HolidayGroup { TRADITIONAL, GREGORIAN }

enum class HolidayFilter {
    ALL,
    TRADITIONAL,
    GREGORIAN;

    fun includes(id: HolidayId): Boolean = when (this) {
        ALL -> true
        TRADITIONAL -> id.group == HolidayGroup.TRADITIONAL
        GREGORIAN -> id.group == HolidayGroup.GREGORIAN
    }
}

enum class HolidayId(
    val kind: HolidayKind,
    val month: Int,
    val day: Int,
    val solarTerm: String? = null,
    val group: HolidayGroup,
) {
    SPRING_FESTIVAL(HolidayKind.LUNAR, 1, 1, group = HolidayGroup.TRADITIONAL),
    LANTERN_FESTIVAL(HolidayKind.LUNAR, 1, 15, group = HolidayGroup.TRADITIONAL),
    DRAGON_HEAD(HolidayKind.LUNAR, 2, 2, group = HolidayGroup.TRADITIONAL),
    QINGMING(HolidayKind.SOLAR_TERM, 4, 1, solarTerm = "清明", group = HolidayGroup.TRADITIONAL),
    DRAGON_BOAT_FESTIVAL(HolidayKind.LUNAR, 5, 5, group = HolidayGroup.TRADITIONAL),
    QIXI(HolidayKind.LUNAR, 7, 7, group = HolidayGroup.TRADITIONAL),
    GHOST_FESTIVAL(HolidayKind.LUNAR, 7, 15, group = HolidayGroup.TRADITIONAL),
    MID_AUTUMN_FESTIVAL(HolidayKind.LUNAR, 8, 15, group = HolidayGroup.TRADITIONAL),
    DOUBLE_NINTH(HolidayKind.LUNAR, 9, 9, group = HolidayGroup.TRADITIONAL),
    LABA_FESTIVAL(HolidayKind.LUNAR, 12, 8, group = HolidayGroup.TRADITIONAL),
    LITTLE_NEW_YEAR(HolidayKind.LUNAR, 12, 23, group = HolidayGroup.TRADITIONAL),

    NEW_YEAR(HolidayKind.SOLAR, 1, 1, group = HolidayGroup.GREGORIAN),
    VALENTINES_DAY(HolidayKind.SOLAR, 2, 14, group = HolidayGroup.GREGORIAN),
    WOMENS_DAY(HolidayKind.SOLAR, 3, 8, group = HolidayGroup.GREGORIAN),
    LABOR_DAY(HolidayKind.SOLAR, 5, 1, group = HolidayGroup.GREGORIAN),
    CHILDRENS_DAY(HolidayKind.SOLAR, 6, 1, group = HolidayGroup.GREGORIAN),
    TEACHERS_DAY(HolidayKind.SOLAR, 9, 10, group = HolidayGroup.GREGORIAN),
    NATIONAL_DAY(HolidayKind.SOLAR, 10, 1, group = HolidayGroup.GREGORIAN),
    HALLOWEEN(HolidayKind.SOLAR, 10, 31, group = HolidayGroup.GREGORIAN),
    SINGLES_DAY(HolidayKind.SOLAR, 11, 11, group = HolidayGroup.GREGORIAN),
    CHRISTMAS_EVE(HolidayKind.SOLAR, 12, 24, group = HolidayGroup.GREGORIAN),
    CHRISTMAS(HolidayKind.SOLAR, 12, 25, group = HolidayGroup.GREGORIAN),
}

data class LunarDateInfo(
    val month: Int,
    val day: Int,
    val isLeap: Boolean,
)

data class HolidayOccurrence(
    val id: HolidayId,
    val date: LocalDate,
    val lunarDate: LunarDateInfo,
)

data class HolidayTiming(
    val daysUntil: Long,
    val secondsUntil: Long,
    val isToday: Boolean,
)

data class HolidayCountdownItem(
    val occurrence: HolidayOccurrence,
    val timing: HolidayTiming,
)

data class HolidayDataset(
    val today: LocalDate,
    val zoneId: ZoneId,
    val occurrences: Map<HolidayId, List<HolidayOccurrence>>,
)

sealed class HolidayLoadResult {
    data class Success(val dataset: HolidayDataset) : HolidayLoadResult()
    data object Failure : HolidayLoadResult()
}

interface HolidayRepository {
    fun nextOccurrences(holiday: HolidayId, fromDate: LocalDate, count: Int): List<LocalDate>
    fun lunarDate(date: LocalDate): LunarDateInfo
}

class BuildHolidayCountdownUseCase(
    private val repository: HolidayRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneProvider: () -> ZoneId = { ZoneId.of("Asia/Shanghai") },
) {
    fun now(): Instant = clock.instant()

    fun zone(): ZoneId = zoneProvider()

    fun load(): HolidayLoadResult {
        return try {
            val zone = zoneProvider()
            val today = clock.instant().atZone(zone).toLocalDate()
            val values = HolidayId.entries.associateWith { holiday ->
                repository.nextOccurrences(holiday, today, FUTURE_OCCURRENCES)
                    .map { date ->
                        HolidayOccurrence(holiday, date, repository.lunarDate(date))
                    }
            }.filterValues(List<HolidayOccurrence>::isNotEmpty)
            if (values.isEmpty()) HolidayLoadResult.Failure
            else HolidayLoadResult.Success(HolidayDataset(today, zone, values))
        } catch (_: Exception) {
            HolidayLoadResult.Failure
        }
    }

    fun timing(occurrence: HolidayOccurrence, now: Instant, dataset: HolidayDataset): HolidayTiming {
        val isToday = occurrence.date == dataset.today
        val targetDate = if (isToday) dataset.today.plusDays(1) else occurrence.date
        val seconds = Duration.between(now, targetDate.atStartOfDay(dataset.zoneId).toInstant())
            .seconds
            .coerceAtLeast(0)
        return HolidayTiming(
            daysUntil = ChronoUnit.DAYS.between(dataset.today, occurrence.date),
            secondsUntil = seconds,
            isToday = isToday,
        )
    }

    companion object {
        const val FUTURE_OCCURRENCES = 3
        const val TICK_INTERVAL_MS = 1_000L
    }
}
