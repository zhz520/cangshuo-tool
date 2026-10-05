package com.cangshuo.toolbox.feature.search.domain

import kotlinx.coroutines.flow.Flow

interface ToolSearchRepository {
    /** Reads in-memory metadata only; implementations must not perform blocking I/O. */
    fun getDocuments(): List<ToolSearchDocument>

    fun observeCatalogChanges(): Flow<Unit>
}
