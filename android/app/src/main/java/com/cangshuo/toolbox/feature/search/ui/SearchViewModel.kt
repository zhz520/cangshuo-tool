package com.cangshuo.toolbox.feature.search.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.feature.search.domain.MAX_SEARCH_QUERY_LENGTH
import com.cangshuo.toolbox.feature.search.domain.SearchToolsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val QUERY_KEY = "search.query"
private const val CATEGORY_KEY = "search.category"

class SearchViewModel(
    private val searchTools: SearchToolsUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        SearchUiState(
            query = savedStateHandle.get<String>(QUERY_KEY).orEmpty().take(MAX_SEARCH_QUERY_LENGTH),
            category = savedStateHandle.get<String>(CATEGORY_KEY)?.let(ToolCategory::fromCode),
        ),
    )
    val uiState = mutableState.asStateFlow()

    init { refresh() }

    fun updateQuery(query: String) {
        if (query.length > MAX_SEARCH_QUERY_LENGTH) {
            mutableState.update { it.copy(queryTooLong = true) }
            return
        }
        savedStateHandle[QUERY_KEY] = query
        mutableState.update { it.copy(query = query, queryTooLong = false) }
        refresh()
    }

    fun selectCategory(category: ToolCategory?) {
        savedStateHandle[CATEGORY_KEY] = category?.code
        mutableState.update { it.copy(category = category) }
        refresh()
    }

    fun clearFilters() {
        savedStateHandle[QUERY_KEY] = ""
        savedStateHandle[CATEGORY_KEY] = null
        mutableState.update { it.copy(query = "", category = null, queryTooLong = false) }
        refresh()
    }

    fun refresh() {
        mutableState.update { it.copy(results = SearchResultsState.Loading) }
        val state = mutableState.value
        val results = try {
            val tools = searchTools(state.query, state.category)
            if (tools.isEmpty()) SearchResultsState.Empty else SearchResultsState.Content(tools)
        } catch (_: Exception) {
            SearchResultsState.Error
        }
        mutableState.update { it.copy(results = results) }
    }

    companion object {
        fun factory(searchTools: SearchToolsUseCase): ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(searchTools, createSavedStateHandle()) }
        }
    }
}
