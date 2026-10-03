package com.cangshuo.toolbox.feature.home.data

import android.content.Context
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.tool.ToolLookupResult
import com.cangshuo.toolbox.core.tool.ToolRegistry
import com.cangshuo.toolbox.feature.home.domain.HomeRepository

class RegistryHomeRepository(private val registry: ToolRegistry) : HomeRepository {
    override fun getTools(): List<ToolMetadata> = registry.enabledTools().map {
        it.metadata.copy(keywords = it.metadata.keywords.toList())
    }

    override fun resolveTool(code: String, context: Context): ToolLookupResult =
        registry.resolve(code, context)
}
