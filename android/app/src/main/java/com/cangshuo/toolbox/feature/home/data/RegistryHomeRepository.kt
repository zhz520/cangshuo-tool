package com.cangshuo.toolbox.feature.home.data

import android.content.Context
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.tool.ToolLookupResult
import com.cangshuo.toolbox.core.tool.ToolRegistryStore
import com.cangshuo.toolbox.feature.home.domain.HomeRepository
import com.cangshuo.toolbox.feature.home.domain.PopularTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

class RegistryHomeRepository(
    private val registry: ToolRegistryStore,
    private val popularSource: suspend () -> List<PopularTool> = { emptyList() },
) : HomeRepository {
    @Volatile private var popular: List<PopularTool> = emptyList()
    private val popularUpdates = MutableStateFlow(0)

    override fun observeCatalogChanges(): Flow<Unit> = merge(registry.snapshots.map { }, popularUpdates.map { })

    override fun getTools(): List<ToolMetadata> = registry.current.enabledTools().map {
        it.metadata.copy(keywords = it.metadata.keywords.toList())
    }

    override fun popularTools(): List<PopularTool> = popular

    /** Best effort refresh; failures keep the previous snapshot instead of blocking the home page. */
    suspend fun refreshPopular() {
        val next = try { popularSource() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return }
        if (next.isNotEmpty() && next != popular) {
            popular = next
            popularUpdates.value = popularUpdates.value + 1
        }
    }

    override fun resolveTool(code: String, context: Context): ToolLookupResult =
        registry.current.resolve(code, context)
}
