package com.cangshuo.toolbox.feature.recent.data

import com.cangshuo.toolbox.core.database.RecentToolDao
import com.cangshuo.toolbox.core.database.RecentToolEntity
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.tool.ToolRegistry
import com.cangshuo.toolbox.feature.recent.domain.RecentRepository
import com.cangshuo.toolbox.feature.recent.domain.RecentTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomRecentRepository(
    private val dao: RecentToolDao,
    private val registry: ToolRegistry,
    private val maxEntries: Int = 12,
) : RecentRepository {
    override fun observeRecent(): Flow<List<RecentTool>> = dao.observeRecent().map { entities ->
        entities.map { RecentTool(it.toolCode, it.lastUsedAt, it.useCount) }
    }

    override suspend fun recordUsage(code: String, at: Long) {
        val known = dao.find(code)
        dao.upsert(RecentToolEntity(code, at, (known?.useCount ?: 0) + 1))
        dao.trimTo(maxEntries)
    }

    override suspend fun remove(code: String) { dao.remove(code) }

    override suspend fun clear() { dao.clear() }

    override fun getTool(code: String): ToolMetadata? = registry.find(code)?.metadata
}
