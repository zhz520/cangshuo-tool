package com.cangshuo.toolbox.core.network

import com.cangshuo.toolbox.core.network.model.ToolCatalogDto

internal fun validateWebToolSnapshot(records: List<ToolCatalogDto>, checkpoint: () -> Unit = {}) {
    if (records.size > 300) throw ToolCatalogException(ToolCatalogFailure.RESOURCE_LIMIT)
    val codes = HashSet<String>()
    var previous: ToolCatalogDto? = null
    for (record in records) {
        checkpoint()
        if (record.mode != "WEB" || record.status != "ENABLED" || record.toMetadataOrNull() == null ||
            record.keywords.size > 64 || record.keywords.any { it.length > 512 }) {
            throw ToolCatalogException(ToolCatalogFailure.INVALID_RESPONSE)
        }
        previous?.let { last ->
            if (last.sortOrder > record.sortOrder || (last.sortOrder == record.sortOrder && last.code > record.code)) {
                throw ToolCatalogException(ToolCatalogFailure.INCOMPLETE)
            }
        }
        if (!codes.add(record.code)) throw ToolCatalogException(ToolCatalogFailure.INCOMPLETE)
        previous = record
    }
}
