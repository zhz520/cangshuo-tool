package com.cangshuo.toolbox.feature.webtools.domain

import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.model.ToolStatus
import com.cangshuo.toolbox.core.network.ToolCatalogException
import com.cangshuo.toolbox.core.network.ToolCatalogFailure
import com.cangshuo.toolbox.core.network.ToolCatalogSource
import com.cangshuo.toolbox.core.network.validateWebToolSnapshot
import com.cangshuo.toolbox.core.tool.ToolRegistryStore
import com.cangshuo.toolbox.feature.webtools.WebToolDefinition
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

class RefreshWebToolCatalogUseCase(
    private val source: ToolCatalogSource,
    private val registry: ToolRegistryStore,
    private val cache: WebToolCatalogCache? = null,
) {
    suspend operator fun invoke(): Boolean {
        return try {
            val records = source.fetchWebTools()
            val context = coroutineContext
            validateWebToolSnapshot(records) { context.ensureActive() }
            val definitions = records.map { dto ->
                val metadata = dto.toMetadataOrNull()?.takeIf { it.mode == ToolMode.WEB && it.status == ToolStatus.ENABLED }
                    ?: throw ToolCatalogException(ToolCatalogFailure.INVALID_RESPONSE)
                WebToolDefinition(metadata)
            }
            context.ensureActive()
            try { cache?.write(records) } catch (_: CatalogCacheException) { /* Valid network data still updates memory. */ }
            context.ensureActive()
            registry.replaceRemoteWebTools(definitions)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ToolCatalogException) {
            false
        }
    }
}
