package com.cangshuo.toolbox.feature.home.domain

import com.cangshuo.toolbox.core.model.ToolCategory
import kotlinx.coroutines.flow.Flow

/** Quick access fills two full rows of four on a phone. */
private const val COMMON_TOOL_LIMIT = 8

class GetHomeUseCase(private val repository: HomeRepository) {
    fun observeCatalogChanges(): Flow<Unit> = repository.observeCatalogChanges()

    operator fun invoke(category: ToolCategory? = null): HomeContent {
        val allTools = repository.getTools()
        val counts = allTools.groupingBy { it.category }.eachCount()
        val visibleTools = allTools.filter { category == null || it.category == category }
        // Quick access shows the most used tools first; unknown codes fall back to registry order.
        val byCode = visibleTools.associateBy { it.code }
        val popular = repository.popularTools()
        val ranked = popular.mapNotNull { byCode[it.code] }
        return HomeContent(
            tools = visibleTools,
            commonTools = (ranked + visibleTools).distinctBy { it.code }.take(COMMON_TOOL_LIMIT),
            featuredTools = visibleTools.filter { it.isFeatured },
            categories = ToolCategory.entries.map { HomeCategory(it, counts[it] ?: 0) },
            totalToolCount = allTools.size,
            popularTools = popular,
        )
    }
}
