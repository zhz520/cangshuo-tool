package com.cangshuo.toolbox.feature.favorites.domain

import com.cangshuo.toolbox.core.model.ToolStatus

private val favoriteCodePattern = Regex("[a-z][a-z0-9_]{0,63}")

class SetFavoriteUseCase(private val repository: FavoriteRepository) {
    suspend operator fun invoke(code: String, selected: Boolean) {
        require(favoriteCodePattern.matches(code)) { "Invalid favorite code" }
        if (selected) {
            require(repository.getTool(code)?.status == ToolStatus.ENABLED) { "Tool is not enabled" }
        }
        repository.setFavorite(code, selected)
    }
}
