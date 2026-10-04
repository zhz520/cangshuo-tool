package com.cangshuo.toolbox.feature.home.ui

import androidx.activity.compose.BackHandler
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.feature.search.ui.SearchRoute
import com.cangshuo.toolbox.feature.favorites.ui.FavoriteButton
import com.cangshuo.toolbox.feature.favorites.ui.FavoriteMessage
import com.cangshuo.toolbox.feature.favorites.ui.FavoritesViewModel

@Composable
fun HomeRoute(
    factory: ViewModelProvider.Factory,
    searchFactory: ViewModelProvider.Factory,
    favoritesFactory: ViewModelProvider.Factory,
) {
    val model: HomeViewModel = viewModel(factory = factory)
    val favorites: FavoritesViewModel = viewModel(key = "local.favorites", factory = favoritesFactory)
    val state by model.uiState.collectAsStateWithLifecycle()
    val favoriteState by favorites.uiState.collectAsStateWithLifecycle()
    val applicationContext = LocalContext.current.applicationContext
    val openedTool = state.openedTool
    val languageTags = LocalConfiguration.current.locales.toLanguageTags()
    LaunchedEffect(languageTags) {
        model.refresh()
        favorites.refresh()
    }

    BackHandler(enabled = openedTool != null || state.isSearchOpen || state.tab != HomeTab.HOME) {
        when {
            openedTool != null -> model.closeTool()
            state.isSearchOpen -> model.closeSearch()
            else -> model.selectTab(HomeTab.HOME)
        }
    }

    if (openedTool == null && state.isSearchOpen) {
        SearchRoute(
            factory = searchFactory,
            favoriteState = favoriteState,
            onSetFavorite = favorites::changeFavorite,
            onClose = model::closeSearch,
            onToolSelected = { model.selectTool(it, applicationContext) },
        )
    } else if (openedTool == null) {
        HomeScreen(
            state = state,
            onTabSelected = model::selectTab,
            onCategorySelected = model::selectCategory,
            onToolSelected = { model.selectTool(it, applicationContext) },
            onRetry = model::refresh,
            onSearch = model::openSearch,
            favoriteState = favoriteState,
            onSetFavorite = favorites::changeFavorite,
            onFavoritesRetry = favorites::refresh,
        )
    } else {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = model::closeTool) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.action_back))
                    }
                    Text(
                        openedTool.metadata.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FavoriteButton(
                        selected = openedTool.code in favoriteState.codes,
                        enabled = favoriteState.ready && openedTool.code !in favoriteState.pendingCodes,
                        saving = openedTool.code in favoriteState.pendingCodes,
                        toolName = openedTool.metadata.name,
                        onChange = { favorites.changeFavorite(openedTool.code, it) },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { openedTool.Screen() }
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
}
