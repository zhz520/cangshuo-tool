package com.cangshuo.toolbox.feature.favorites.domain

import com.cangshuo.toolbox.core.model.ToolMetadata

data class FavoriteTool(val code: String, val addedAt: Long)

/** Missing implementations retain their bookmark and can still be removed. */
data class FavoriteItem(val code: String, val metadata: ToolMetadata?, val addedAt: Long)
