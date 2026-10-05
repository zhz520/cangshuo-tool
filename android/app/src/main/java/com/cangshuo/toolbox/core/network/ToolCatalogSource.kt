package com.cangshuo.toolbox.core.network

import com.cangshuo.toolbox.core.network.model.ToolCatalogDto
import java.io.IOException

fun interface ToolCatalogSource {
    suspend fun fetchWebTools(): List<ToolCatalogDto>
}

enum class ToolCatalogFailure { NETWORK, TIMEOUT, HTTP, INVALID_RESPONSE, RESOURCE_LIMIT, INCOMPLETE }

/** Fixed diagnostics only: never carry response text, URLs or an underlying exception message. */
class ToolCatalogException(val reason: ToolCatalogFailure) : IOException("Catalog request failed: ${reason.name}")

internal data class ToolCatalogRequestPolicy(
    val maxPageBytes: Int = 1_000_000,
    val maxTotalBytes: Int = 3_000_000,
    val timeoutMillis: Long = 20_000,
    val connectTimeoutMillis: Int = 5_000,
    val readTimeoutMillis: Int = 8_000,
) {
    init {
        require(maxPageBytes in 1..1_000_000 && maxTotalBytes in 1..3_000_000)
        require(timeoutMillis in 1..20_000 && connectTimeoutMillis in 1..5_000 && readTimeoutMillis in 1..8_000)
    }
}
