package com.cangshuo.toolbox.feature.home.domain

import com.cangshuo.toolbox.core.model.ToolCategory
import kotlinx.coroutines.flow.Flow

class GetHomeUseCase(private val repository: HomeRepository) {
    fun observeCatalogChanges(): Flow<Unit> = repository.observeCatalogChanges()

    operator fun invoke(category: ToolCategory? = null): HomeContent {
        val allTools = repository.getTools()
        val counts = allTools.groupingBy { it.category }.eachCount()
        val visibleTools = allTools.filter { category == null || it.category == category }
        return HomeContent(
            tools = visibleTools,
            commonTools = visibleTools.take(6),
            featuredTools = visibleTools.filter { it.isFeatured },
            categories = ToolCategory.entries.map { HomeCategory(it, counts[it] ?: 0) },
            totalToolCount = allTools.size,
        )
    }
}
