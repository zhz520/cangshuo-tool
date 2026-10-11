package com.cangshuo.toolbox.feature.home.ui

import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.home.domain.HomeContent
import com.cangshuo.toolbox.feature.webtools.domain.CatalogSyncState

enum class HomeTab { HOME, TOOLS, FAVORITES, PROFILE }

/** Full-screen pages pushed over the tab scaffold. */
enum class HomeOverlay { RECENT, POPULAR, SETTINGS, AUTH, FEEDBACK }

/** Transient, honest notices for not-yet-implemented actions. */
enum class HomeNotice { FEATURE_UNAVAILABLE }

sealed interface HomeCatalogState {
    data object Loading : HomeCatalogState
    data class Content(val value: HomeContent) : HomeCatalogState
    data class Empty(val value: HomeContent) : HomeCatalogState
    data object Error : HomeCatalogState
}

enum class HomeMessage {
    NOT_FOUND, DISABLED, MAINTENANCE, UNSUPPORTED, LOGIN_REQUIRED, PERMISSION_REQUIRED, OPEN_ERROR,
}

data class HomeUiState(
    val tab: HomeTab = HomeTab.HOME,
    val category: ToolCategory? = null,
    val catalog: HomeCatalogState = HomeCatalogState.Loading,
    val isSearchOpen: Boolean = false,
    val openedTool: ToolDefinition? = null,
    val overlay: HomeOverlay? = null,
    val notice: HomeNotice? = null,
    val message: HomeMessage? = null,
    val sync: CatalogSyncState = CatalogSyncState.IDLE,
)
