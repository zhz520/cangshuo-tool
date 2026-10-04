package com.cangshuo.toolbox.feature.recent.domain
import com.cangshuo.toolbox.core.model.ToolMetadata
data class RecentTool(val code: String, val lastUsedAt: Long, val useCount: Int)
data class RecentItem(val code: String, val metadata: ToolMetadata?, val lastUsedAt: Long, val useCount: Int)
