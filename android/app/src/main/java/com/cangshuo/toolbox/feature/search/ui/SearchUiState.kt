package com.cangshuo.toolbox.feature.search.ui

import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata

sealed interface SearchResultsState {
    data object Loading : SearchResultsState
    data class Content(val tools: List<ToolMetadata>) : SearchResultsState
    data object Empty : SearchResultsState
    data object Error : SearchResultsState
}

data class SearchUiState(
    val query: String = "",
    val category: ToolCategory? = null,
    val results: SearchResultsState = SearchResultsState.Loading,
    val queryTooLong: Boolean = false,
)
