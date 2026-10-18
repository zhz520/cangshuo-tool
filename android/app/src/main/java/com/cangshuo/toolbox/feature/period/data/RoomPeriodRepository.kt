package com.cangshuo.toolbox.feature.period.data

import com.cangshuo.toolbox.core.database.PeriodCycleDao
import com.cangshuo.toolbox.core.database.PeriodCycleEntity
import com.cangshuo.toolbox.feature.period.domain.PeriodCycle
import com.cangshuo.toolbox.feature.period.domain.PeriodFlow
import com.cangshuo.toolbox.feature.period.domain.PeriodLimitException
import com.cangshuo.toolbox.feature.period.domain.PeriodMood
import com.cangshuo.toolbox.feature.period.domain.PeriodPolicy
import com.cangshuo.toolbox.feature.period.domain.PeriodRepository
import com.cangshuo.toolbox.feature.period.domain.PeriodSymptom
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPeriodRepository(private val dao: PeriodCycleDao) : PeriodRepository {
    override fun observeAll(): Flow<List<PeriodCycle>> =
        dao.observeAll().map { rows -> rows.map(::toDomain) }

    override suspend fun find(id: Long): PeriodCycle? = dao.find(id)?.let(::toDomain)

    override suspend fun insert(cycle: PeriodCycle): Long {
        val id = dao.insertIfBelowLimit(toEntity(cycle), PeriodPolicy.MAX_CYCLES)
        if (id == -1L) throw PeriodLimitException()
        return id
    }

    override suspend fun update(cycle: PeriodCycle) {
        check(dao.update(toEntity(cycle)) > 0) { "Period record no longer exists" }
    }

    override suspend fun delete(id: Long) {
        check(dao.delete(id) > 0) { "Period record no longer exists" }
    }

    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    private fun toDomain(row: PeriodCycleEntity) = PeriodCycle(
        id = row.id,
        startEpochDay = row.startEpochDay,
        endEpochDay = row.endEpochDay,
        flow = PeriodFlow.fromStorage(row.flow),
        symptoms = PeriodSymptom.fromMask(row.symptomsMask),
        mood = row.mood?.let(PeriodMood::fromStorage),
        notes = row.notes,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
    )

    private fun toEntity(cycle: PeriodCycle) = PeriodCycleEntity(
        id = cycle.id,
        startEpochDay = cycle.startEpochDay,
        endEpochDay = cycle.endEpochDay,
        flow = cycle.flow.storage,
        symptomsMask = PeriodSymptom.maskOf(cycle.symptoms),
        mood = cycle.mood?.storage,
        notes = cycle.notes,
        createdAt = cycle.createdAt,
        updatedAt = cycle.updatedAt,
    )
}
