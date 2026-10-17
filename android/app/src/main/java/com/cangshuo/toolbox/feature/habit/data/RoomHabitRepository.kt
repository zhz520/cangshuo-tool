package com.cangshuo.toolbox.feature.habit.data

import com.cangshuo.toolbox.core.database.HabitDao
import com.cangshuo.toolbox.core.database.HabitEntity
import com.cangshuo.toolbox.core.database.HabitEntryEntity
import com.cangshuo.toolbox.feature.habit.domain.Habit
import com.cangshuo.toolbox.feature.habit.domain.HabitLimitException
import com.cangshuo.toolbox.feature.habit.domain.HabitPolicy
import com.cangshuo.toolbox.feature.habit.domain.HabitRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomHabitRepository(private val dao: HabitDao) : HabitRepository {
    override fun observeAll(): Flow<List<Habit>> = combine(dao.observeAll(), dao.observeEntries()) { habits, entries ->
        val byHabit = entries.groupBy { it.habitId }
        habits.map { habit -> toDomain(habit, byHabit[habit.id].orEmpty()) }
    }

    override suspend fun insert(habit: Habit): Long {
        val id = dao.insertIfBelowLimit(
            name = habit.name,
            description = habit.description,
            weekdaysMask = habit.weekdaysMask,
            archived = habit.archived,
            archivedDay = habit.archivedDay,
            createdDay = habit.createdDay,
            createdAt = habit.createdAt,
            updatedAt = habit.updatedAt,
            limit = HabitPolicy.MAX_HABITS,
        )
        if (id == -1L) throw HabitLimitException()
        return id
    }

    override suspend fun update(habit: Habit) = dao.update(toEntity(habit))

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun setArchived(id: Long, archived: Boolean, archivedDay: Long?, updatedAt: Long) =
        dao.setArchived(id, archived, archivedDay, updatedAt)

    override suspend fun addEntry(habitId: Long, dateEpochDay: Long, checkedAt: Long): Boolean =
        dao.insertEntryIfAllowed(habitId, dateEpochDay, checkedAt, HabitPolicy.MAX_ENTRIES) != -1L

    override suspend fun removeEntry(habitId: Long, dateEpochDay: Long) = dao.deleteEntry(habitId, dateEpochDay)

    override suspend fun clearEntries(habitId: Long): Int = dao.deleteEntries(habitId)

    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    private fun toDomain(habit: HabitEntity, entries: List<HabitEntryEntity>) = Habit(
        id = habit.id,
        name = habit.name,
        description = habit.description,
        weekdaysMask = habit.weekdaysMask,
        archived = habit.archived,
        archivedDay = habit.archivedDay,
        position = habit.position,
        createdDay = habit.createdDay,
        createdAt = habit.createdAt,
        updatedAt = habit.updatedAt,
        checkedDates = entries.mapTo(HashSet()) { it.dateEpochDay },
    )

    private fun toEntity(habit: Habit) = HabitEntity(
        id = habit.id,
        name = habit.name,
        description = habit.description,
        weekdaysMask = habit.weekdaysMask,
        archived = habit.archived,
        archivedDay = habit.archivedDay,
        position = habit.position,
        createdDay = habit.createdDay,
        createdAt = habit.createdAt,
        updatedAt = habit.updatedAt,
    )
}
