package com.cangshuo.toolbox.feature.home.domain

import android.content.Context
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.tool.ToolLookupResult

interface HomeRepository {
    /** Reads the bundled catalog synchronously; implementations must not perform blocking I/O. */
    fun getTools(): List<ToolMetadata>

    fun resolveTool(code: String, context: Context): ToolLookupResult
}
