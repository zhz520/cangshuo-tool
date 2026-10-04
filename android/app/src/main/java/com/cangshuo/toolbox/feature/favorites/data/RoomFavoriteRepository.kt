package com.cangshuo.toolbox.feature.favorites.data

import com.cangshuo.toolbox.core.database.FavoriteToolDao
import com.cangshuo.toolbox.core.database.FavoriteToolEntity
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.tool.ToolRegistry
import com.cangshuo.toolbox.feature.favorites.domain.FavoriteRepository
import com.cangshuo.toolbox.feature.favorites.domain.FavoriteTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomFavoriteRepository(private val dao: FavoriteToolDao, private val registry: ToolRegistry) : FavoriteRepository {
    override fun observeFavorites(): Flow<List<FavoriteTool>> = dao.observeFavorites().map { entities ->
        entities.map { FavoriteTool(it.toolCode, it.addedAt) }
    }

    override suspend fun setFavorite(code: String, selected: Boolean) {
        if (selected) dao.add(FavoriteToolEntity(code, System.currentTimeMillis())) else dao.remove(code)
    }

    override fun getTool(code: String): ToolMetadata? = registry.find(code)?.metadata
}
