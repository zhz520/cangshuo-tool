package com.cangshuo.toolbox.feature.home.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.feature.home.domain.GetHomeUseCase
import com.cangshuo.toolbox.feature.home.domain.HomeToolResult
import com.cangshuo.toolbox.feature.home.domain.OpenHomeToolUseCase
import com.cangshuo.toolbox.feature.webtools.domain.RequestWebToolCatalogSyncUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAB_KEY = "home.tab"
private const val CATEGORY_KEY = "home.category"
private const val SEARCH_OPEN_KEY = "home.searchOpen"

class HomeViewModel(
    private val getHome: GetHomeUseCase,
    private val openTool: OpenHomeToolUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val catalogSync: RequestWebToolCatalogSyncUseCase? = null,
    private val settings: kotlinx.coroutines.flow.StateFlow<com.cangshuo.toolbox.feature.sync.domain.AppSettings>? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        HomeUiState(
            tab = HomeTab.entries.firstOrNull { it.name == savedStateHandle.get<String>(TAB_KEY) }
                ?: HomeTab.HOME,
            category = savedStateHandle.get<String>(CATEGORY_KEY)?.let(ToolCategory::fromCode),
            isSearchOpen = savedStateHandle[SEARCH_OPEN_KEY] ?: false,
        ),
    )
    val uiState = mutableState.asStateFlow()

    init {
        refresh()
        settings?.let { stream -> viewModelScope.launch { stream.collect { value ->
            if (value.ready && !savedStateHandle.contains(TAB_KEY)) selectTab(HomeTab.valueOf(value.startupPage))
        } } }
        catalogSync?.let { sync ->
            viewModelScope.launch { sync.state.collect { state -> mutableState.update { it.copy(sync = state) } } }
        }
        viewModelScope.launch {
            getHome.observeCatalogChanges().collect { loadCatalog(showLoading = false) }
        }
    }

    fun selectTab(tab: HomeTab) {
        savedStateHandle[TAB_KEY] = tab.name
        if (tab == HomeTab.HOME) savedStateHandle[CATEGORY_KEY] = null
        mutableState.update {
            it.copy(tab = tab, category = if (tab == HomeTab.HOME) null else it.category, message = null)
        }
        refresh()
    }

    fun selectCategory(category: ToolCategory?) {
        savedStateHandle[TAB_KEY] = HomeTab.TOOLS.name
        savedStateHandle[CATEGORY_KEY] = category?.code
        mutableState.update { it.copy(tab = HomeTab.TOOLS, category = category, message = null) }
        refresh()
    }

    fun refresh() {
        loadCatalog(showLoading = true)
    }

    fun refreshRemoteCatalog(manual: Boolean = true) {
        catalogSync?.let { sync -> viewModelScope.launch { sync(manual) } }
    }

    private fun loadCatalog(showLoading: Boolean) {
        if (showLoading) mutableState.update { it.copy(catalog = HomeCatalogState.Loading) }
        try {
            val content = getHome(mutableState.value.category)
            val catalog = if (content.tools.isEmpty()) {
                HomeCatalogState.Empty(content)
            } else {
                HomeCatalogState.Content(content)
            }
            mutableState.update { it.copy(catalog = catalog) }
        } catch (_: Exception) {
            mutableState.update { it.copy(catalog = HomeCatalogState.Error) }
        }
    }

    fun selectTool(code: String, context: Context) {
        try {
            val result = openTool(code, context)
            val message = when (result) {
                is HomeToolResult.Opened -> null
                HomeToolResult.NotFound -> HomeMessage.NOT_FOUND
                HomeToolResult.Disabled -> HomeMessage.DISABLED
                HomeToolResult.Maintenance -> HomeMessage.MAINTENANCE
                HomeToolResult.Unsupported -> HomeMessage.UNSUPPORTED
                HomeToolResult.LoginRequired -> HomeMessage.LOGIN_REQUIRED
                HomeToolResult.PermissionRequired -> HomeMessage.PERMISSION_REQUIRED
            }
            mutableState.update {
                it.copy(
                    openedTool = (result as? HomeToolResult.Opened)?.tool ?: it.openedTool,
                    message = message,
                )
            }
        } catch (_: Exception) {
            mutableState.update { it.copy(message = HomeMessage.OPEN_ERROR) }
        }
    }

    fun closeTool() {
        mutableState.update { it.copy(openedTool = null) }
    }

    fun openSearch() {
        savedStateHandle[SEARCH_OPEN_KEY] = true
        mutableState.update { it.copy(isSearchOpen = true, message = null) }
    }

    fun closeSearch() {
        savedStateHandle[SEARCH_OPEN_KEY] = false
        mutableState.update { it.copy(isSearchOpen = false, message = null) }
    }

    fun dismissMessage() {
        mutableState.update { it.copy(message = null) }
    }

    companion object {
        fun factory(getHome: GetHomeUseCase, openTool: OpenHomeToolUseCase, catalogSync: RequestWebToolCatalogSyncUseCase? = null,
            settings: kotlinx.coroutines.flow.StateFlow<com.cangshuo.toolbox.feature.sync.domain.AppSettings>? = null): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { HomeViewModel(getHome, openTool, createSavedStateHandle(), catalogSync, settings) }
            }
    }
}
