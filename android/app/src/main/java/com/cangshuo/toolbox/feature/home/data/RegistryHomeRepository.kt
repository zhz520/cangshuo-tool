package com.cangshuo.toolbox.feature.home.data

import android.content.Context
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.tool.ToolLookupResult
import com.cangshuo.toolbox.core.tool.ToolRegistryStore
import com.cangshuo.toolbox.feature.home.domain.HomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RegistryHomeRepository(private val registry: ToolRegistryStore) : HomeRepository {
    override fun observeCatalogChanges(): Flow<Unit> = registry.snapshots.map { }

    override fun getTools(): List<ToolMetadata> = registry.current.enabledTools().map {
        it.metadata.copy(keywords = it.metadata.keywords.toList())
    }

    override fun resolveTool(code: String, context: Context): ToolLookupResult =
        registry.current.resolve(code, context)
}
