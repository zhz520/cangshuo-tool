package com.cangshuo.toolbox.feature.anniversary.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.flow.Flow

enum class AnniversaryRepeat(val storage: Int) {
    NONE(0), MONTHLY(1), YEARLY(2);

    companion object {
        fun fromStorage(value: Int): AnniversaryRepeat = entries.firstOrNull { it.storage == value } ?: NONE
    }
}

enum class AnniversaryCategory(val storage: Int) {
    BIRTHDAY(0), RELATIONSHIP(1), FAMILY(2), WORK(3), OTHER(4);

    companion object {
        fun fromStorage(value: Int): AnniversaryCategory = entries.firstOrNull { it.storage == value } ?: OTHER
    }
}

enum class AnniversaryFilter { ALL, UPCOMING, TODAY, PAST }
enum class AnniversarySort { PINNED, NEXT_DATE, NEWEST, NAME }
enum class AnniversaryError { TITLE, NOTES, DATE, LIMIT, STORAGE }

data class AnniversaryEvent(
    val id: Long,
    val title: String,
    val notes: String,
    val dateEpochDay: Long,
    val repeat: AnniversaryRepeat,
    val category: AnniversaryCategory,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class AnniversaryOccurrence(
    val date: LocalDate,
    val daysUntil: Long,
)

data class AnniversaryLunarInfo(
    val monthChinese: String,
    val dayChinese: String,
    val isLeapMonth: Boolean,
    val zodiac: String,
)

data class AnniversaryElapsed(
    val years: Int,
    val daysSinceLatest: Long,
)

data class AnniversaryStats(
    val total: Int,
    val today: Int,
    val nextSevenDays: Int,
    val nextThirtyDays: Int,
)

interface AnniversaryRepository {
    fun observeAll(): Flow<List<AnniversaryEvent>>

    suspend fun insert(event: AnniversaryEvent): Long

    suspend fun update(event: AnniversaryEvent)

    suspend fun delete(id: Long)

    suspend fun setPinned(id: Long, pinned: Boolean, updatedAt: Long)

    fun lunarInfo(date: LocalDate): AnniversaryLunarInfo

    fun nowMillis(): Long

    fun todayEpochDay(): Long
}

class AnniversaryLimitException : IllegalStateException("Anniversary storage limit reached")

object AnniversaryPolicy {
    const val MAX_EVENTS = 500
    const val MAX_TITLE_CODE_POINTS = 80
    const val MAX_NOTES_CODE_POINTS = 500
    val MIN_DATE: LocalDate = LocalDate.of(1900, 1, 1)
    val MAX_DATE: LocalDate = LocalDate.of(2100, 12, 31)

    fun validateTitle(title: String): AnniversaryError? = when {
        title.isBlank() -> AnniversaryError.TITLE
        title.trim().codePointCount(0, title.trim().length) > MAX_TITLE_CODE_POINTS -> AnniversaryError.TITLE
        else -> null
    }

    fun validateNotes(notes: String): AnniversaryError? =
        if (notes.codePointCount(0, notes.length) > MAX_NOTES_CODE_POINTS) AnniversaryError.NOTES else null

    fun validateDate(epochDay: Long): AnniversaryError? {
        val date = LocalDate.ofEpochDay(epochDay)
        return if (date.isBefore(MIN_DATE) || date.isAfter(MAX_DATE)) AnniversaryError.DATE else null
    }

    fun validate(title: String, notes: String, dateEpochDay: Long): AnniversaryError? =
        validateTitle(title) ?: validateNotes(notes) ?: validateDate(dateEpochDay)

    fun normalizeTitle(title: String): String = title.trim().replace(Regex("\\s+"), " ")

    fun normalizeNotes(notes: String): String = notes.trim()

    fun annualDate(original: LocalDate, year: Int): LocalDate =
        if (original.monthValue == 2 && original.dayOfMonth == 29 && !java.time.Year.isLeap(year.toLong())) {
            LocalDate.of(year, 2, 28)
        } else {
            original.withYear(year)
        }

    fun monthlyDate(original: LocalDate, month: YearMonth): LocalDate =
        month.atDay(minOf(original.dayOfMonth, month.lengthOfMonth()))

    fun nextOccurrence(event: AnniversaryEvent, todayEpochDay: Long): LocalDate {
        val today = LocalDate.ofEpochDay(todayEpochDay)
        val original = LocalDate.ofEpochDay(event.dateEpochDay)
        return when (event.repeat) {
            AnniversaryRepeat.NONE -> original
            AnniversaryRepeat.YEARLY -> {
                var year = maxOf(today.year, original.year)
                while (true) {
                    val candidate = annualDate(original, year)
                    if (!candidate.isBefore(today) && !candidate.isBefore(original)) return candidate
                    year++
                }
                error("unreachable")
            }
            AnniversaryRepeat.MONTHLY -> {
                var month = maxOf(YearMonth.from(today), YearMonth.from(original))
                while (true) {
                    val candidate = monthlyDate(original, month)
                    if (!candidate.isBefore(today) && !candidate.isBefore(original)) return candidate
                    month = month.plusMonths(1)
                }
                error("unreachable")
            }
        }
    }

    fun occurrence(event: AnniversaryEvent, todayEpochDay: Long): AnniversaryOccurrence {
        val date = nextOccurrence(event, todayEpochDay)
        return AnniversaryOccurrence(date, ChronoUnit.DAYS.between(LocalDate.ofEpochDay(todayEpochDay), date))
    }

    fun isPast(event: AnniversaryEvent, todayEpochDay: Long): Boolean =
        event.repeat == AnniversaryRepeat.NONE && event.dateEpochDay < todayEpochDay

    fun upcomingDates(event: AnniversaryEvent, todayEpochDay: Long, count: Int = 3): List<LocalDate> {
        if (count <= 0 || isPast(event, todayEpochDay)) return emptyList()
        val first = nextOccurrence(event, todayEpochDay)
        return when (event.repeat) {
            AnniversaryRepeat.NONE -> listOf(first)
            AnniversaryRepeat.YEARLY -> List(count) { annualDate(LocalDate.ofEpochDay(event.dateEpochDay), first.year + it) }
            AnniversaryRepeat.MONTHLY -> {
                val original = LocalDate.ofEpochDay(event.dateEpochDay)
                List(count) { monthlyDate(original, YearMonth.from(first).plusMonths(it.toLong())) }
            }
        }
    }

    fun elapsedYears(event: AnniversaryEvent, todayEpochDay: Long): AnniversaryElapsed? {
        if (event.repeat != AnniversaryRepeat.YEARLY) return null
        val today = LocalDate.ofEpochDay(todayEpochDay)
        val original = LocalDate.ofEpochDay(event.dateEpochDay)
        if (today.isBefore(original)) return null
        var years = today.year - original.year
        var latest = annualDate(original, original.year + years)
        if (latest.isAfter(today)) {
            years--
            latest = annualDate(original, original.year + years)
        }
        return AnniversaryElapsed(years, ChronoUnit.DAYS.between(latest, today))
    }

    fun filterAndSort(
        events: List<AnniversaryEvent>,
        filter: AnniversaryFilter,
        sort: AnniversarySort,
        query: String,
        todayEpochDay: Long,
    ): List<AnniversaryEvent> {
        val needle = query.trim().lowercase(Locale.ROOT)
        val filtered = events.filter { event ->
            val occurrence = occurrence(event, todayEpochDay)
            val matchesFilter = when (filter) {
                AnniversaryFilter.ALL -> true
                AnniversaryFilter.UPCOMING -> !isPast(event, todayEpochDay)
                AnniversaryFilter.TODAY -> !isPast(event, todayEpochDay) && occurrence.daysUntil == 0L
                AnniversaryFilter.PAST -> isPast(event, todayEpochDay)
            }
            matchesFilter && (needle.isEmpty() || event.title.lowercase(Locale.ROOT).contains(needle) ||
                event.notes.lowercase(Locale.ROOT).contains(needle))
        }
        val pinned = compareByDescending<AnniversaryEvent> { it.pinned }
        val past = compareBy<AnniversaryEvent> { isPast(it, todayEpochDay) }
        val next = compareBy<AnniversaryEvent> { occurrence(it, todayEpochDay).date }
        val newest = compareByDescending<AnniversaryEvent> { it.createdAt }.thenByDescending { it.id }
        val name = compareBy<AnniversaryEvent> { it.title.lowercase(Locale.ROOT) }
        return when (sort) {
            AnniversarySort.PINNED -> filtered.sortedWith(pinned.then(past).then(next).then(name))
            AnniversarySort.NEXT_DATE -> filtered.sortedWith(past.then(next).then(pinned).then(name))
            AnniversarySort.NEWEST -> filtered.sortedWith(pinned.then(newest))
            AnniversarySort.NAME -> filtered.sortedWith(pinned.then(name))
        }
    }

    fun stats(events: List<AnniversaryEvent>, todayEpochDay: Long): AnniversaryStats {
        val upcoming = events.filterNot { isPast(it, todayEpochDay) }
        return AnniversaryStats(
            total = events.size,
            today = upcoming.count { occurrence(it, todayEpochDay).daysUntil == 0L },
            nextSevenDays = upcoming.count { occurrence(it, todayEpochDay).daysUntil in 1L..7L },
            nextThirtyDays = upcoming.count { occurrence(it, todayEpochDay).daysUntil in 1L..30L },
        )
    }

    fun epochDay(date: LocalDate): Long = date.toEpochDay()

    fun epochMillis(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun timestampDate(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
}

class AnniversaryUseCases(private val repository: AnniversaryRepository) {
    fun observe() = repository.observeAll()

    fun validate(title: String, notes: String, dateEpochDay: Long): AnniversaryError? =
        AnniversaryPolicy.validate(title, notes, dateEpochDay)

    fun create(
        title: String,
        notes: String,
        dateEpochDay: Long,
        repeat: AnniversaryRepeat,
        category: AnniversaryCategory,
        pinned: Boolean,
    ): AnniversaryEvent {
        val now = repository.nowMillis()
        return AnniversaryEvent(
            id = 0,
            title = AnniversaryPolicy.normalizeTitle(title),
            notes = AnniversaryPolicy.normalizeNotes(notes),
            dateEpochDay = dateEpochDay,
            repeat = repeat,
            category = category,
            pinned = pinned,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun update(
        current: AnniversaryEvent,
        title: String,
        notes: String,
        dateEpochDay: Long,
        repeat: AnniversaryRepeat,
        category: AnniversaryCategory,
        pinned: Boolean,
    ): AnniversaryEvent = current.copy(
        title = AnniversaryPolicy.normalizeTitle(title),
        notes = AnniversaryPolicy.normalizeNotes(notes),
        dateEpochDay = dateEpochDay,
        repeat = repeat,
        category = category,
        pinned = pinned,
        updatedAt = repository.nowMillis(),
    )

    fun lunarInfo(date: LocalDate): AnniversaryLunarInfo = repository.lunarInfo(date)
    fun occurrence(event: AnniversaryEvent, todayEpochDay: Long): AnniversaryOccurrence =
        AnniversaryPolicy.occurrence(event, todayEpochDay)
    fun upcomingDates(event: AnniversaryEvent, todayEpochDay: Long): List<LocalDate> =
        AnniversaryPolicy.upcomingDates(event, todayEpochDay)
    fun elapsedYears(event: AnniversaryEvent, todayEpochDay: Long): AnniversaryElapsed? =
        AnniversaryPolicy.elapsedYears(event, todayEpochDay)
    fun isPast(event: AnniversaryEvent, todayEpochDay: Long): Boolean =
        AnniversaryPolicy.isPast(event, todayEpochDay)

    fun now(): Long = repository.nowMillis()
    fun today(): Long = repository.todayEpochDay()
    suspend fun insert(event: AnniversaryEvent): Long = repository.insert(event)
    suspend fun update(event: AnniversaryEvent) = repository.update(event)
    suspend fun delete(id: Long) = repository.delete(id)
    suspend fun setPinned(id: Long, pinned: Boolean) = repository.setPinned(id, pinned, repository.nowMillis())
}
