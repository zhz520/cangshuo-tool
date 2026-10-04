package com.cangshuo.toolbox.feature.search.domain

import com.cangshuo.toolbox.core.model.ToolMetadata

data class ToolSearchDocument(val tool: ToolMetadata, val categoryNames: List<String>)
