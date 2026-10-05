package com.cangshuo.toolbox.feature.webtools.domain

import com.cangshuo.toolbox.core.network.model.ToolCatalogDto

interface WebToolCatalogCache {
    suspend fun read(): List<ToolCatalogDto>?
    suspend fun write(records: List<ToolCatalogDto>)
}

/** Fixed diagnostics; no database path, payload, source address or underlying exception. */
class CatalogCacheException : Exception("Catalog cache unavailable", null)
