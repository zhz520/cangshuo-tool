package com.cangshuo.toolbox.feature.favorites.ui

import com.cangshuo.toolbox.feature.favorites.domain.FavoriteItem

sealed interface FavoritesListState {
    data object Loading : FavoritesListState
    data object Empty : FavoritesListState
    data class Content(val items: List<FavoriteItem>) : FavoritesListState
    data object Error : FavoritesListState
}

enum class FavoriteMessage { LOAD_FAILED, SAVE_FAILED }

data class FavoritesUiState(
    val list: FavoritesListState = FavoritesListState.Loading,
    val codes: Set<String> = emptySet(),
    val pendingCodes: Set<String> = emptySet(),
    val message: FavoriteMessage? = null,
) {
    val ready: Boolean get() = list is FavoritesListState.Content || list == FavoritesListState.Empty
}
