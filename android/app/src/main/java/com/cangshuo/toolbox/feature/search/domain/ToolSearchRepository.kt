package com.cangshuo.toolbox.feature.search.domain

interface ToolSearchRepository {
    /** Reads bundled metadata only; implementations must not perform blocking I/O. */
    fun getDocuments(): List<ToolSearchDocument>
}
