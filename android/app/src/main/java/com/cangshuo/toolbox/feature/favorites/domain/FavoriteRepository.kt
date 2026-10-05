package com.cangshuo.toolbox.feature.favorites.domain

import com.cangshuo.toolbox.core.model.ToolMetadata
import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    fun observeFavorites(): Flow<List<FavoriteTool>>
    suspend fun setFavorite(code: String, selected: Boolean)
    /** Current in-memory metadata lookup only; does not perform I/O. */
    fun getTool(code: String): ToolMetadata?
}
