package com.cangshuo.toolbox.feature.home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.core.ui.ToolboxMotion
import com.cangshuo.toolbox.feature.search.ui.SearchRoute
import com.cangshuo.toolbox.feature.favorites.ui.FavoriteButton
import com.cangshuo.toolbox.feature.favorites.ui.FavoriteMessage
import com.cangshuo.toolbox.feature.favorites.ui.FavoritesUiState
import com.cangshuo.toolbox.feature.favorites.ui.FavoritesViewModel
import com.cangshuo.toolbox.feature.recent.ui.RecentViewModel

@Composable
fun HomeRoute(
    factory: ViewModelProvider.Factory,
    searchFactory: ViewModelProvider.Factory,
    favoritesFactory: ViewModelProvider.Factory,
    recentFactory: ViewModelProvider.Factory,
    authFactory: ViewModelProvider.Factory,
    syncFactory: ViewModelProvider.Factory,
    gridColumns: Int = 0,
) {
    val model: HomeViewModel = viewModel(factory = factory)
    val favorites: FavoritesViewModel = viewModel(key = "local.favorites", factory = favoritesFactory)
    val recent: RecentViewModel = viewModel(key = "local.recent", factory = recentFactory)
    val state by model.uiState.collectAsStateWithLifecycle()
    val favoriteState by favorites.uiState.collectAsStateWithLifecycle()
    val recentState by recent.uiState.collectAsStateWithLifecycle()
    val applicationContext = LocalContext.current.applicationContext
    val openedTool = state.openedTool
    val languageTags = LocalConfiguration.current.locales.toLanguageTags()
    LifecycleStartEffect(Unit) {
        model.refreshRemoteCatalog(manual = false)
        onStopOrDispose { }
    }
    LaunchedEffect(languageTags) {
        model.refresh()
        favorites.refresh()
        recent.refresh()
    }
    LaunchedEffect(openedTool?.code) { openedTool?.let { recent.recordUse(it.code) } }

    BackHandler(enabled = openedTool != null || state.isSearchOpen || state.tab != HomeTab.HOME) {
        when {
            openedTool != null -> model.closeTool()
            state.isSearchOpen -> model.closeSearch()
            else -> model.selectTab(HomeTab.HOME)
        }
    }

    val page: HomePage = when {
        openedTool != null -> HomePage.Tool(openedTool)
        state.isSearchOpen -> HomePage.Search
        else -> HomePage.Home
    }
    AnimatedContent(
        targetState = page,
        contentKey = { it.key },
        transitionSpec = {
            when {
                initialState is HomePage.Home && targetState is HomePage.Tool -> ToolboxMotion.forward()
                initialState is HomePage.Tool && targetState is HomePage.Home -> ToolboxMotion.backward()
                initialState is HomePage.Home && targetState is HomePage.Search -> ToolboxMotion.forward()
                initialState is HomePage.Search && targetState is HomePage.Home -> ToolboxMotion.backward()
                else -> ToolboxMotion.tab()
            }
        },
        label = "home-page-transition",
    ) { target ->
        when (target) {
            HomePage.Home -> HomeScreen(
                gridColumns = gridColumns,
                state = state,
                onTabSelected = model::selectTab,
                onCategorySelected = model::selectCategory,
                onToolSelected = { model.selectTool(it, applicationContext) },
                onRetry = model::refresh,
                onSearch = model::openSearch,
                favoriteState = favoriteState,
                onSetFavorite = favorites::changeFavorite,
                onFavoritesRetry = favorites::refresh,
                recentState = recentState,
                onRecentRetry = recent::refresh,
                onClearRecent = recent::clear,
                onCatalogRefresh = { model.refreshRemoteCatalog() },
                profileContent = { modifier -> com.cangshuo.toolbox.feature.auth.ui.AuthRoute(authFactory, modifier, syncFactory) },
            )
            HomePage.Search -> SearchRoute(
                factory = searchFactory,
                favoriteState = favoriteState,
                recentCodes = recentState.codes,
                onSetFavorite = favorites::changeFavorite,
                onClose = model::closeSearch,
                onToolSelected = { model.selectTool(it, applicationContext) },
            )
            is HomePage.Tool -> ToolDetailPage(
                definition = target.definition,
                favoriteState = favoriteState,
                onClose = model::closeTool,
                onSetFavorite = favorites::changeFavorite,
            )
        }
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = model::dismissMessage,
            title = { Text(stringResource(R.string.tool_unavailable_title)) },
            text = { Text(stringResource(message.labelResource())) },
            confirmButton = {
                TextButton(onClick = model::dismissMessage) {
                    Text(stringResource(R.string.action_understood))
                }
            },
        )
    }
    if (state.message == null) favoriteState.message?.let { message ->
        AlertDialog(
            onDismissRequest = favorites::dismissMessage,
            title = { Text(stringResource(R.string.favorite_action_failed_title)) },
            text = {
                Text(stringResource(if (message == FavoriteMessage.LOAD_FAILED) R.string.favorite_load_failed else R.string.favorite_save_failed))
            },
            confirmButton = {
                TextButton(onClick = favorites::dismissMessage) { Text(stringResource(R.string.action_understood)) }
            },
        )
    }
    if (state.message == null && favoriteState.message == null) recentState.message?.let { _ ->
        AlertDialog(
            onDismissRequest = recent::dismissMessage,
            title = { Text(stringResource(R.string.recent_clear_failed_title)) },
            text = { Text(stringResource(R.string.recent_clear_failed)) },
            confirmButton = {
                TextButton(onClick = recent::dismissMessage) { Text(stringResource(R.string.action_understood)) }
            },
        )
    }
}

private sealed interface HomePage {
    val key: String

    data object Home : HomePage {
        override val key: String = "home"
    }

    data object Search : HomePage {
        override val key: String = "search"
    }

    data class Tool(val definition: ToolDefinition) : HomePage {
        override val key: String get() = "tool:${definition.code}"
    }
}

@Composable
private fun ToolDetailPage(
    definition: ToolDefinition,
    favoriteState: FavoritesUiState,
    onClose: () -> Unit,
    onSetFavorite: (String, Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_back), stringResource(R.string.action_back))
                }
                Text(
                    definition.metadata.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FavoriteButton(
                    selected = definition.code in favoriteState.codes,
                    enabled = favoriteState.ready && definition.code !in favoriteState.pendingCodes,
                    saving = definition.code in favoriteState.pendingCodes,
                    toolName = definition.metadata.name,
                    onChange = { onSetFavorite(definition.code, it) },
                )
            }
        },
    ) { padding ->
        // WEB tools (Decision 019) are hosted on the official site and opened in the shared
        // WebView container instead of a native screen.
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (definition.metadata.mode == com.cangshuo.toolbox.core.model.ToolMode.WEB) {
                com.cangshuo.toolbox.core.ui.ToolboxWebScreen(
                    title = definition.name,
                    code = definition.code,
                    onClose = onClose,
                )
            } else {
                definition.Screen()
            }
        }
    }
}
