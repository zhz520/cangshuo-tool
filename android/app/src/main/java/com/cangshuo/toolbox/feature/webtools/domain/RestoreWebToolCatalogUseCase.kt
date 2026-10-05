package com.cangshuo.toolbox.feature.webtools.domain

import com.cangshuo.toolbox.core.network.ToolCatalogException
import com.cangshuo.toolbox.core.network.validateWebToolSnapshot
import com.cangshuo.toolbox.core.tool.ToolRegistryStore
import com.cangshuo.toolbox.feature.webtools.WebToolDefinition
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

class RestoreWebToolCatalogUseCase(private val cache: WebToolCatalogCache, private val registry: ToolRegistryStore) {
    suspend operator fun invoke(): Boolean = try {
        val records = cache.read()
        if (records == null) false else {
            val context = coroutineContext
            validateWebToolSnapshot(records) { context.ensureActive() }
            val definitions = records.map { WebToolDefinition(requireNotNull(it.toMetadataOrNull())) }
            coroutineContext.ensureActive()
            registry.replaceRemoteWebTools(definitions)
            true
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: CatalogCacheException) {
        false
    } catch (_: ToolCatalogException) {
        false
    }
}
