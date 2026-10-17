package com.cangshuo.toolbox.feature.habit.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.flow.Flow

enum class HabitWeekday(val bit: Int, val isoDay: Int) {
    MONDAY(1, 1),
    TUESDAY(1 shl 1, 2),
    WEDNESDAY(1 shl 2, 3),
    THURSDAY(1 shl 3, 4),
    FRIDAY(1 shl 4, 5),
    SATURDAY(1 shl 5, 6),
    SUNDAY(1 shl 6, 7),
}

enum class HabitFilter { ALL, TODAY, ARCHIVED }
enum class HabitSort { TODAY_FIRST, STREAK, NEWEST, NAME }
enum class HabitError { NAME, DESCRIPTION, WEEKDAYS, LIMIT, ENTRY_LIMIT, ARCHIVED, DATE, STORAGE }

data class Habit(
    val id: Long,
    val name: String,
    val description: String,
    val weekdaysMask: Int,
    val archived: Boolean,
    val archivedDay: Long?,
    val position: Int,
    val createdDay: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val checkedDates: Set<Long>,
)

data class HabitDay(
    val epochDay: Long,
    val scheduled: Boolean,
    val completed: Boolean,
)

data class HabitWeekProgress(val completed: Int, val scheduled: Int)

data class HabitStats(
    val active: Int,
    val dueToday: Int,
    val completedToday: Int,
    val totalCheckIns: Int,
    val bestCurrentStreak: Int,
)

interface HabitRepository {
    fun observeAll(): Flow<List<Habit>>

    suspend fun insert(habit: Habit): Long

    suspend fun update(habit: Habit)

    suspend fun delete(id: Long)

    suspend fun setArchived(id: Long, archived: Boolean, archivedDay: Long?, updatedAt: Long)

    suspend fun addEntry(habitId: Long, dateEpochDay: Long, checkedAt: Long): Boolean

    suspend fun removeEntry(habitId: Long, dateEpochDay: Long)

    suspend fun clearEntries(habitId: Long): Int

    fun nowMillis(): Long

    fun todayEpochDay(): Long
}

class HabitLimitException : IllegalStateException("Habit storage limit reached")

object HabitPolicy {
    const val MAX_HABITS = 100
    const val MAX_ENTRIES = 10_000
    const val MAX_NAME_CODE_POINTS = 80
    const val MAX_DESCRIPTION_CODE_POINTS = 500
    const val ALL_DAYS_MASK = (1 shl 7) - 1
    const val HISTORY_DAYS = 28

    fun validateName(name: String): HabitError? = when {
        name.isBlank() -> HabitError.NAME
        name.trim().codePointCount(0, name.trim().length) > MAX_NAME_CODE_POINTS -> HabitError.NAME
        else -> null
    }

    fun validateDescription(description: String): HabitError? =
        if (description.codePointCount(0, description.length) > MAX_DESCRIPTION_CODE_POINTS) {
            HabitError.DESCRIPTION
        } else null

    fun validateWeekdays(mask: Int): HabitError? =
        if ((mask and ALL_DAYS_MASK) == 0 || (mask and ALL_DAYS_MASK.inv()) != 0) HabitError.WEEKDAYS else null

    fun validate(name: String, description: String, mask: Int): HabitError? =
        validateName(name) ?: validateDescription(description) ?: validateWeekdays(mask)

    fun normalizeName(name: String): String = name.trim().replace(Regex("\\s+"), " ")

    fun normalizeDescription(description: String): String = description.trim()

    fun isScheduled(mask: Int, epochDay: Long): Boolean {
        val day = LocalDate.ofEpochDay(epochDay).dayOfWeek.value - 1
        return mask and (1 shl day) != 0
    }

    fun isChecked(habit: Habit, epochDay: Long): Boolean = epochDay in habit.checkedDates

    fun isDueToday(habit: Habit, todayEpochDay: Long): Boolean =
        !habit.archived && isScheduled(habit.weekdaysMask, todayEpochDay) && !isChecked(habit, todayEpochDay)

    fun previousScheduled(mask: Int, epochDay: Long): Long {
        var candidate = epochDay - 1
        repeat(7) {
            if (isScheduled(mask, candidate)) return candidate
            candidate--
        }
        return Long.MIN_VALUE
    }

    fun currentStreak(habit: Habit, todayEpochDay: Long): Int {
        val effectiveToday = if (habit.archived) habit.archivedDay ?: todayEpochDay else todayEpochDay
        var day = when {
            !isScheduled(habit.weekdaysMask, effectiveToday) -> previousScheduled(habit.weekdaysMask, effectiveToday)
            isChecked(habit, effectiveToday) -> effectiveToday
            else -> previousScheduled(habit.weekdaysMask, effectiveToday)
        }
        if (day < habit.createdDay) return 0
        var streak = 0
        while (day >= habit.createdDay && isChecked(habit, day)) {
            streak++
            day = previousScheduled(habit.weekdaysMask, day)
        }
        return streak
    }

    fun bestStreak(habit: Habit): Int {
        val scheduledChecks = habit.checkedDates.asSequence()
            .filter { it >= habit.createdDay && isScheduled(habit.weekdaysMask, it) }
            .sorted()
            .toList()
        if (scheduledChecks.isEmpty()) return 0
        val streakAt = HashMap<Long, Int>(scheduledChecks.size)
        var best = 0
        for (day in scheduledChecks) {
            val previous = previousScheduled(habit.weekdaysMask, day)
            val streak = if (previous >= habit.createdDay && streakAt.containsKey(previous)) {
                streakAt.getValue(previous) + 1
            } else 1
            streakAt[day] = streak
            if (streak > best) best = streak
        }
        return best
    }

    fun weekProgress(habit: Habit, todayEpochDay: Long): HabitWeekProgress {
        val effectiveToday = if (habit.archived) habit.archivedDay ?: todayEpochDay else todayEpochDay
        val today = LocalDate.ofEpochDay(effectiveToday)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = (0L..6L).map { monday.plusDays(it).toEpochDay() }
            .filter { it in habit.createdDay..effectiveToday }
        val scheduled = days.filter { isScheduled(habit.weekdaysMask, it) }
        return HabitWeekProgress(
            completed = scheduled.count { isChecked(habit, it) },
            scheduled = scheduled.size,
        )
    }

    fun completionRate(habit: Habit, todayEpochDay: Long, days: Int = 30): Int {
        val effectiveToday = if (habit.archived) habit.archivedDay ?: todayEpochDay else todayEpochDay
        val from = maxOf(habit.createdDay, effectiveToday - days + 1)
        if (from > effectiveToday) return 0
        var scheduled = 0
        var completed = 0
        for (day in from..effectiveToday) {
            if (!isScheduled(habit.weekdaysMask, day)) continue
            scheduled++
            if (isChecked(habit, day)) completed++
        }
        return if (scheduled == 0) 0 else (completed * 100) / scheduled
    }

    fun history(habit: Habit, todayEpochDay: Long, days: Int = HISTORY_DAYS): List<HabitDay> {
        val effectiveToday = if (habit.archived) habit.archivedDay ?: todayEpochDay else todayEpochDay
        val from = effectiveToday - days + 1
        return (from..effectiveToday).map { day ->
            HabitDay(
                epochDay = day,
                scheduled = isScheduled(habit.weekdaysMask, day),
                completed = isChecked(habit, day),
            )
        }
    }

    fun canToggleDate(habit: Habit, epochDay: Long, todayEpochDay: Long): Boolean =
        !habit.archived && epochDay in habit.createdDay..todayEpochDay

    fun filterAndSort(
        habits: List<Habit>,
        filter: HabitFilter,
        sort: HabitSort,
        query: String,
        todayEpochDay: Long,
    ): List<Habit> {
        val needle = query.trim().lowercase(Locale.ROOT)
        val filtered = habits.filter { habit ->
            val matchesFilter = when (filter) {
                HabitFilter.ALL -> true
                HabitFilter.TODAY -> !habit.archived && isScheduled(habit.weekdaysMask, todayEpochDay)
                HabitFilter.ARCHIVED -> habit.archived
            }
            matchesFilter && (needle.isEmpty() || habit.name.lowercase(Locale.ROOT).contains(needle) ||
                habit.description.lowercase(Locale.ROOT).contains(needle))
        }
        val archived = compareBy<Habit> { it.archived }
        val pendingToday = compareBy<Habit> {
            !isDueToday(it, todayEpochDay)
        }
        val streak = compareByDescending<Habit> { currentStreak(it, todayEpochDay) }
        val newest = compareByDescending<Habit> { it.createdAt }.thenByDescending { it.id }
        val name = compareBy<Habit> { it.name.lowercase(Locale.ROOT) }
        return when (sort) {
            HabitSort.TODAY_FIRST -> filtered.sortedWith(archived.then(pendingToday).then(streak).then(name))
            HabitSort.STREAK -> filtered.sortedWith(archived.then(streak).then(name))
            HabitSort.NEWEST -> filtered.sortedWith(archived.then(newest))
            HabitSort.NAME -> filtered.sortedWith(archived.then(name))
        }
    }

    fun stats(habits: List<Habit>, todayEpochDay: Long): HabitStats {
        val active = habits.count { !it.archived }
        return HabitStats(
            active = active,
            dueToday = habits.count { isDueToday(it, todayEpochDay) },
            completedToday = habits.count { !it.archived && isChecked(it, todayEpochDay) },
            totalCheckIns = habits.sumOf { it.checkedDates.size },
            bestCurrentStreak = habits.filterNot { it.archived }.maxOfOrNull { currentStreak(it, todayEpochDay) } ?: 0,
        )
    }
}

class HabitUseCases(private val repository: HabitRepository) {
    fun observe() = repository.observeAll()

    fun validate(name: String, description: String, mask: Int): HabitError? =
        HabitPolicy.validate(name, description, mask)

    suspend fun create(name: String, description: String, weekdaysMask: Int): Habit {
        val now = repository.nowMillis()
        return Habit(
            id = 0,
            name = HabitPolicy.normalizeName(name),
            description = HabitPolicy.normalizeDescription(description),
            weekdaysMask = weekdaysMask,
            archived = false,
            archivedDay = null,
            position = 0,
            createdDay = repository.todayEpochDay(),
            createdAt = now,
            updatedAt = now,
            checkedDates = emptySet(),
        )
    }

    fun update(current: Habit, name: String, description: String, weekdaysMask: Int): Habit =
        current.copy(
            name = HabitPolicy.normalizeName(name),
            description = HabitPolicy.normalizeDescription(description),
            weekdaysMask = weekdaysMask,
            updatedAt = repository.nowMillis(),
        )

    fun now(): Long = repository.nowMillis()
    fun today(): Long = repository.todayEpochDay()

    suspend fun insert(habit: Habit): Long = repository.insert(habit)
    suspend fun update(habit: Habit) = repository.update(habit)
    suspend fun delete(id: Long) = repository.delete(id)
    suspend fun setArchived(id: Long, archived: Boolean) =
        repository.setArchived(id, archived, repository.todayEpochDay().takeIf { archived }, repository.nowMillis())
    suspend fun addEntry(habitId: Long, dateEpochDay: Long): Boolean =
        repository.addEntry(habitId, dateEpochDay, repository.nowMillis())
    suspend fun removeEntry(habitId: Long, dateEpochDay: Long) = repository.removeEntry(habitId, dateEpochDay)
    suspend fun clearEntries(habitId: Long): Int = repository.clearEntries(habitId)
}
