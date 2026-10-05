package com.cangshuo.toolbox.feature.favorites.data

import com.cangshuo.toolbox.core.database.FavoriteToolDao
import com.cangshuo.toolbox.core.database.FavoriteToolEntity
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.tool.ToolRegistryStore
import com.cangshuo.toolbox.feature.favorites.domain.FavoriteRepository
import com.cangshuo.toolbox.feature.favorites.domain.FavoriteTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.cangshuo.toolbox.feature.sync.domain.CloudSyncRepository

class RoomFavoriteRepository(private val dao: FavoriteToolDao, private val registry: ToolRegistryStore,
    private val sync: CloudSyncRepository? = null) : FavoriteRepository {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeFavorites(): Flow<List<FavoriteTool>> {
        val local=dao.observeFavorites().map { list -> list.map { FavoriteTool(it.toolCode,it.addedAt) } }
        val selected=sync?.context?.flatMapLatest { context ->
            context.localScope?.let { user -> sync.records(user,"FAVORITE").map { list ->
                list.filterNot { it.deleted }.map { FavoriteTool(it.entityKey,it.updatedAt) }
            } } ?: local
        } ?: local
        return combine(selected,registry.snapshots) { favorites,_ -> favorites }
    }

    override suspend fun setFavorite(code: String, selected: Boolean) {
        if (sync?.setFavorite(code,selected)==true) return
        if (selected) dao.add(FavoriteToolEntity(code, System.currentTimeMillis())) else dao.remove(code)
    }

    override fun getTool(code: String): ToolMetadata? = registry.current.find(code)?.metadata
}
