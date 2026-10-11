package com.cangshuo.toolbox.feature.home.domain

import android.content.Context
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.tool.ToolLookupResult
import kotlinx.coroutines.flow.Flow

interface HomeRepository {
    /** Reads the current in-memory catalog; implementations must not perform blocking I/O. */
    fun getTools(): List<ToolMetadata>

    /** Cached popularity snapshot (most used first); empty until the first refresh succeeds. */
    fun popularCodes(): List<String> = emptyList()

    /** Emits initially and whenever the catalog is replaced. */
    fun observeCatalogChanges(): Flow<Unit>

    fun resolveTool(code: String, context: Context): ToolLookupResult
}
