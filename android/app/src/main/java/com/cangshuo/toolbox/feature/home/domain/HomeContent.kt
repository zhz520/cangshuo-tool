package com.cangshuo.toolbox.feature.home.domain

import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata

data class HomeCategory(val category: ToolCategory, val toolCount: Int)

data class HomeContent(
    val tools: List<ToolMetadata>,
    val commonTools: List<ToolMetadata>,
    val featuredTools: List<ToolMetadata>,
    val categories: List<HomeCategory>,
    val totalToolCount: Int,
)
