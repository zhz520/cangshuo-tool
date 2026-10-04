package com.cangshuo.toolbox.feature.favorites.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveFavoritesUseCase(private val repository: FavoriteRepository) {
    operator fun invoke(): Flow<List<FavoriteItem>> = repository.observeFavorites().map { favorites ->
        favorites.map { FavoriteItem(it.code, repository.getTool(it.code), it.addedAt) }
    }
}
