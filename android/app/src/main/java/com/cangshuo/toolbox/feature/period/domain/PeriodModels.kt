package com.cangshuo.toolbox.feature.period.domain

import com.cangshuo.toolbox.core.model.UnicodeText
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class PeriodFlow(val storage: Int) {
    LIGHT(0), MEDIUM(1), HEAVY(2);

    companion object {
        fun fromStorage(value: Int): PeriodFlow = entries.firstOrNull { it.storage == value } ?: MEDIUM
    }
}

enum class PeriodSymptom(val bit: Int) {
    CRAMPS(1 shl 0),
    HEADACHE(1 shl 1),
    BACK_PAIN(1 shl 2),
    FATIGUE(1 shl 3),
    BLOATING(1 shl 4),
    TENDER_BREASTS(1 shl 5),
    ACNE(1 shl 6),
    NAUSEA(1 shl 7);

    companion object {
        fun fromMask(mask: Int): Set<PeriodSymptom> = entries.filterTo(linkedSetOf()) { mask and it.bit != 0 }
        fun maskOf(values: Set<PeriodSymptom>): Int = values.fold(0) { mask, value -> mask or value.bit }
    }
}

enum class PeriodMood(val storage: Int) {
    CALM(0), HAPPY(1), SENSITIVE(2), IRRITABLE(3), LOW(4), ANXIOUS(5);

    companion object {
        fun fromStorage(value: Int): PeriodMood? = entries.firstOrNull { it.storage == value }
    }
}

data class PeriodSettings(
    val cycleDays: Int = DEFAULT_CYCLE_DAYS,
    val periodDays: Int = DEFAULT_PERIOD_DAYS,
) {
    companion object {
        const val DEFAULT_CYCLE_DAYS = 28
        const val DEFAULT_PERIOD_DAYS = 5
    }
}

data class PeriodCycle(
    val id: Long,
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val flow: PeriodFlow,
    val symptoms: Set<PeriodSymptom>,
    val mood: PeriodMood?,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun durationDays(todayEpochDay: Long): Int {
        val effectiveEnd = endEpochDay ?: todayEpochDay.coerceAtLeast(startEpochDay)
        return (effectiveEnd - startEpochDay + 1L).coerceAtLeast(1L).toInt()
    }
}

data class PeriodCycleInput(
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val flow: PeriodFlow,
    val symptoms: Set<PeriodSymptom>,
    val mood: PeriodMood?,
    val notes: String,
)

data class PeriodInsights(
    val averageCycleDays: Int,
    val averagePeriodDays: Int,
    val cycleSamples: Int,
    val periodSamples: Int,
    val lastStartEpochDay: Long?,
    val lastEndEpochDay: Long?,
    val nextStartEpochDay: Long?,
    val predictedEndEpochDay: Long?,
    val currentCycleDay: Int?,
    val lateDays: Int,
    val ovulationEpochDay: Long?,
    val fertileStartEpochDay: Long?,
    val fertileEndEpochDay: Long?,
)

data class PeriodCalendarDay(
    val epochDay: Long,
    val inVisibleMonth: Boolean,
    val cycleId: Long?,
    val periodStart: Boolean,
    val periodEnd: Boolean,
    val ongoing: Boolean,
    val predictedPeriod: Boolean,
    val fertileWindow: Boolean,
    val symptomCount: Int,
    val hasNotes: Boolean,
    val selected: Boolean,
    val today: Boolean,
)

sealed interface PeriodValidation {
    data class Valid(val cycle: PeriodCycle) : PeriodValidation
    data class Invalid(val error: PeriodError) : PeriodValidation
}

enum class PeriodError {
    START_DATE, END_DATE, DATE_ORDER, OVERLAP, ONGOING, NOTES, LIMIT, SETTINGS, STORAGE,
}

interface PeriodRepository {
    fun observeAll(): Flow<List<PeriodCycle>>
    suspend fun find(id: Long): PeriodCycle?
    suspend fun insert(cycle: PeriodCycle): Long
    suspend fun update(cycle: PeriodCycle)
    suspend fun delete(id: Long)
    fun nowMillis(): Long
    fun todayEpochDay(): Long
}

interface PeriodSettingsRepository {
    val settings: StateFlow<PeriodSettings>
    fun update(settings: PeriodSettings)
}

class PeriodLimitException : IllegalStateException("Period record limit reached")

class PeriodUseCases(
    private val repository: PeriodRepository,
    private val settingsRepository: PeriodSettingsRepository,
) {
    fun observeAll() = repository.observeAll()
    fun observeSettings() = settingsRepository.settings

    fun validate(existing: PeriodCycle?, input: PeriodCycleInput, all: List<PeriodCycle>): PeriodValidation =
        PeriodPolicy.validate(existing, input, all, repository.nowMillis(), repository.todayEpochDay())

    suspend fun save(existing: PeriodCycle?, input: PeriodCycleInput, all: List<PeriodCycle>): PeriodError? {
        val validation = validate(existing, input, all)
        if (validation is PeriodValidation.Invalid) return validation.error
        val cycle = (validation as PeriodValidation.Valid).cycle
        return try {
            if (existing == null) repository.insert(cycle) else repository.update(cycle)
            null
        } catch (_: PeriodLimitException) {
            PeriodError.LIMIT
        } catch (_: Exception) {
            PeriodError.STORAGE
        }
    }

    suspend fun delete(id: Long): PeriodError? = try {
        repository.delete(id)
        null
    } catch (_: Exception) {
        PeriodError.STORAGE
    }

    fun updateSettings(settings: PeriodSettings): PeriodError? {
        if (!PeriodPolicy.validSettings(settings)) return PeriodError.SETTINGS
        settingsRepository.update(settings)
        return null
    }

    fun insights(cycles: List<PeriodCycle>, settings: PeriodSettings): PeriodInsights =
        PeriodPolicy.insights(cycles, settings, repository.todayEpochDay())

    fun today() = repository.todayEpochDay()
}

object PeriodPolicy {
    const val MAX_CYCLES = 1_200
    const val MAX_NOTES_CODE_POINTS = 500
    const val MIN_CYCLE_DAYS = 15
    const val MAX_CYCLE_DAYS = 60
    const val MIN_PERIOD_DAYS = 1
    const val MAX_PERIOD_DAYS = 14
    const val MAX_RECORDED_PERIOD_DAYS = 31
    const val LUTEAL_DAYS = 14
    val MIN_DATE: LocalDate = LocalDate.of(1900, 1, 1)
    val MAX_DATE: LocalDate = LocalDate.of(2100, 12, 31)
    val MIN_MONTH: YearMonth = YearMonth.from(MIN_DATE)
    val MAX_MONTH: YearMonth = YearMonth.from(MAX_DATE)

    fun validate(
        existing: PeriodCycle?,
        input: PeriodCycleInput,
        all: List<PeriodCycle>,
        nowMillis: Long,
        todayEpochDay: Long,
    ): PeriodValidation {
        val start = input.startEpochDay
        val end = input.endEpochDay
        if (!validDate(start) || start > todayEpochDay) return PeriodValidation.Invalid(PeriodError.START_DATE)
        if (end != null && (!validDate(end) || end > todayEpochDay || end < start)) {
            return PeriodValidation.Invalid(PeriodError.END_DATE)
        }
        if (end != null && end - start + 1L > MAX_RECORDED_PERIOD_DAYS) {
            return PeriodValidation.Invalid(PeriodError.END_DATE)
        }
        val notes = input.notes.trim()
        if (notes.codePointCount(0, notes.length) > MAX_NOTES_CODE_POINTS) {
            return PeriodValidation.Invalid(PeriodError.NOTES)
        }
        val effectiveEnd = end ?: todayEpochDay.coerceAtLeast(start)
        val overlaps = all.any { other ->
            other.id != existing?.id && start <= (other.endEpochDay ?: todayEpochDay.coerceAtLeast(other.startEpochDay)) &&
                other.startEpochDay <= effectiveEnd
        }
        if (overlaps) return PeriodValidation.Invalid(PeriodError.OVERLAP)
        if (existing == null && all.size >= MAX_CYCLES) return PeriodValidation.Invalid(PeriodError.LIMIT)
        val cycle = PeriodCycle(
            id = existing?.id ?: 0L,
            startEpochDay = start,
            endEpochDay = end,
            flow = input.flow,
            symptoms = PeriodSymptom.entries.filterTo(linkedSetOf()) { it in input.symptoms },
            mood = input.mood,
            notes = notes,
            createdAt = existing?.createdAt ?: nowMillis,
            updatedAt = nowMillis,
        )
        return PeriodValidation.Valid(cycle)
    }

    fun validSettings(settings: PeriodSettings): Boolean =
        settings.cycleDays in MIN_CYCLE_DAYS..MAX_CYCLE_DAYS && settings.periodDays in MIN_PERIOD_DAYS..MAX_PERIOD_DAYS

    fun insights(cycles: List<PeriodCycle>, settings: PeriodSettings, todayEpochDay: Long): PeriodInsights {
        val sorted = cycles.sortedBy { it.startEpochDay }
        val starts = sorted.map { it.startEpochDay }.distinct()
        val cycleLengths = starts.zipWithNext { first, second -> (second - first).toInt() }
            .filter { it in MIN_CYCLE_DAYS..MAX_CYCLE_DAYS }
            .takeLast(12)
        val periodLengths = sorted.mapNotNull { cycle ->
            cycle.endEpochDay?.let { end -> (end - cycle.startEpochDay + 1L).toInt() }
        }.filter { it in MIN_PERIOD_DAYS..MAX_PERIOD_DAYS }.takeLast(12)
        val averageCycle = average(cycleLengths) ?: settings.cycleDays
        val averagePeriod = average(periodLengths) ?: settings.periodDays
        val last = sorted.lastOrNull()
        val nextStart = last?.let { it.startEpochDay + averageCycle }
        val ovulation = nextStart?.minus(LUTEAL_DAYS.toLong())
        return PeriodInsights(
            averageCycleDays = averageCycle,
            averagePeriodDays = averagePeriod,
            cycleSamples = cycleLengths.size,
            periodSamples = periodLengths.size,
            lastStartEpochDay = last?.startEpochDay,
            lastEndEpochDay = last?.endEpochDay,
            nextStartEpochDay = nextStart,
            predictedEndEpochDay = nextStart?.plus(averagePeriod.toLong() - 1L),
            currentCycleDay = last?.takeIf { it.startEpochDay <= todayEpochDay }
                ?.let { (todayEpochDay - it.startEpochDay + 1L).toInt() },
            lateDays = nextStart?.let { (todayEpochDay - it).coerceAtLeast(0L).toInt() } ?: 0,
            ovulationEpochDay = ovulation,
            fertileStartEpochDay = ovulation?.minus(5L),
            fertileEndEpochDay = ovulation?.plus(1L),
        )
    }

    fun calendarMonth(
        month: YearMonth,
        cycles: List<PeriodCycle>,
        insights: PeriodInsights,
        selectedEpochDay: Long?,
        todayEpochDay: Long,
    ): List<PeriodCalendarDay> {
        val cycleByDay = buildMap {
            cycles.forEach { cycle ->
                val end = cycle.endEpochDay ?: todayEpochDay.coerceAtLeast(cycle.startEpochDay)
                var day = cycle.startEpochDay
                while (day <= end) {
                    put(day, cycle)
                    day++
                }
            }
        }
        val first = month.atDay(1)
        val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
        val predictedStart = insights.nextStartEpochDay
        val predictedEnd = insights.predictedEndEpochDay
        return (0 until 42).map { offset ->
            val date = gridStart.plusDays(offset.toLong())
            val epochDay = date.toEpochDay()
            val cycle = cycleByDay[epochDay]
            PeriodCalendarDay(
                epochDay = epochDay,
                inVisibleMonth = YearMonth.from(date) == month,
                cycleId = cycle?.id,
                periodStart = cycle?.startEpochDay == epochDay,
                periodEnd = cycle?.endEpochDay == epochDay,
                ongoing = cycle != null && cycle.endEpochDay == null,
                predictedPeriod = predictedStart != null && predictedEnd != null &&
                    epochDay in predictedStart..predictedEnd && cycle == null,
                fertileWindow = insights.fertileStartEpochDay != null && insights.fertileEndEpochDay != null &&
                    epochDay in insights.fertileStartEpochDay..insights.fertileEndEpochDay && cycle == null,
                symptomCount = cycle?.symptoms?.size ?: 0,
                hasNotes = cycle?.notes?.isNotBlank() == true,
                selected = epochDay == selectedEpochDay,
                today = epochDay == todayEpochDay,
            )
        }
    }

    fun clampMonth(value: YearMonth): YearMonth = when {
        value.isBefore(MIN_MONTH) -> MIN_MONTH
        value.isAfter(MAX_MONTH) -> MAX_MONTH
        else -> value
    }

    fun normalizeNotes(value: String): String =
        UnicodeText.takeCodePointsWithoutSplittingCluster(value, MAX_NOTES_CODE_POINTS)

    private fun average(values: List<Int>): Int? =
        if (values.isEmpty()) null else ((values.sum() + values.size / 2) / values.size).coerceAtLeast(1)

    private fun validDate(epochDay: Long): Boolean = try {
        val date = LocalDate.ofEpochDay(epochDay)
        !date.isBefore(MIN_DATE) && !date.isAfter(MAX_DATE)
    } catch (_: java.time.DateTimeException) {
        false
    }
}
