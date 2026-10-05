package com.cangshuo.toolbox.feature.recent.data

import com.cangshuo.toolbox.core.database.RecentToolDao
import com.cangshuo.toolbox.core.database.RecentToolEntity
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.tool.ToolRegistryStore
import com.cangshuo.toolbox.feature.recent.domain.RecentRepository
import com.cangshuo.toolbox.feature.recent.domain.RecentTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.cangshuo.toolbox.feature.sync.domain.CloudSyncRepository
import com.cangshuo.toolbox.feature.sync.domain.SyncRecord

class RoomRecentRepository(
    private val dao: RecentToolDao,
    private val registry: ToolRegistryStore,
    private val maxEntries: Int = 12,
    private val sync: CloudSyncRepository? = null,
) : RecentRepository {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeRecent(): Flow<List<RecentTool>> {
        val local=dao.observeRecent().map { entities -> entities.map { RecentTool(it.toolCode,it.lastUsedAt,it.useCount) } }
        val selected=sync?.context?.flatMapLatest { context ->
            context.recentScope?.let { user -> sync.records(user,"RECENT").map { list ->
                list.filterNot { it.deleted }.sortedWith(compareByDescending<SyncRecord> { it.lastUsedAt }.thenBy { it.entityKey }).take(maxEntries)
                    .map { RecentTool(it.entityKey,requireNotNull(it.lastUsedAt),requireNotNull(it.useCount).toInt()) }
            } } ?: local
        } ?: local
        return combine(selected,registry.snapshots) { recent,_ -> recent }
    }

    override suspend fun recordUsage(code: String, at: Long) {
        if (sync?.recordUsage(code,at)==true) return
        val known = dao.find(code)
        dao.upsert(RecentToolEntity(code, at, (known?.useCount ?: 0) + 1))
        dao.trimTo(maxEntries)
    }

    override suspend fun remove(code: String) { if(sync?.removeRecent(code)!=true) dao.remove(code) }

    override suspend fun clear() { if(sync?.clearRecent()!=true) dao.clear() }

    override fun getTool(code: String): ToolMetadata? = registry.current.find(code)?.metadata
}
