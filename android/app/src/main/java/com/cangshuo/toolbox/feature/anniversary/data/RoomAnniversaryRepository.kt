package com.cangshuo.toolbox.feature.anniversary.data

import com.cangshuo.toolbox.core.database.AnniversaryEventDao
import com.cangshuo.toolbox.core.database.AnniversaryEventEntity
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryCategory
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryEvent
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryLimitException
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryLunarInfo
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryPolicy
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryRepeat
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryRepository
import com.nlf.calendar.Solar
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAnniversaryRepository(private val dao: AnniversaryEventDao) : AnniversaryRepository {
    override fun observeAll(): Flow<List<AnniversaryEvent>> = dao.observeAll().map { rows -> rows.map(::toDomain) }

    override suspend fun insert(event: AnniversaryEvent): Long {
        val id = dao.insertIfBelowLimit(
            title = event.title,
            notes = event.notes,
            dateEpochDay = event.dateEpochDay,
            repeatRule = event.repeat.storage,
            category = event.category.storage,
            pinned = event.pinned,
            createdAt = event.createdAt,
            updatedAt = event.updatedAt,
            limit = AnniversaryPolicy.MAX_EVENTS,
        )
        if (id == -1L) throw AnniversaryLimitException()
        return id
    }

    override suspend fun update(event: AnniversaryEvent) = dao.update(toEntity(event))
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun setPinned(id: Long, pinned: Boolean, updatedAt: Long) = dao.setPinned(id, pinned, updatedAt)

    override fun lunarInfo(date: LocalDate): AnniversaryLunarInfo {
        val lunar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth).lunar
        return AnniversaryLunarInfo(
            monthChinese = lunar.monthInChinese,
            dayChinese = lunar.dayInChinese,
            isLeapMonth = lunar.month < 0,
            zodiac = lunar.yearShengXiao,
        )
    }

    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    private fun toDomain(event: AnniversaryEventEntity) = AnniversaryEvent(
        id = event.id,
        title = event.title,
        notes = event.notes,
        dateEpochDay = event.dateEpochDay,
        repeat = AnniversaryRepeat.fromStorage(event.repeatRule),
        category = AnniversaryCategory.fromStorage(event.category),
        pinned = event.pinned,
        createdAt = event.createdAt,
        updatedAt = event.updatedAt,
    )

    private fun toEntity(event: AnniversaryEvent) = AnniversaryEventEntity(
        id = event.id,
        title = event.title,
        notes = event.notes,
        dateEpochDay = event.dateEpochDay,
        repeatRule = event.repeat.storage,
        category = event.category.storage,
        pinned = event.pinned,
        createdAt = event.createdAt,
        updatedAt = event.updatedAt,
    )
}
