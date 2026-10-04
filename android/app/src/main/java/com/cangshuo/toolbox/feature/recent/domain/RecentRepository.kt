package com.cangshuo.toolbox.feature.recent.domain

import com.cangshuo.toolbox.core.model.ToolMetadata
import kotlinx.coroutines.flow.Flow

interface RecentRepository {
    fun observeRecent(): Flow<List<RecentTool>>
    suspend fun recordUsage(code: String, at: Long)
    suspend fun remove(code: String)
    suspend fun clear()
    /** Bundled metadata lookup only; does not perform I/O. */
    fun getTool(code: String): ToolMetadata?
}
