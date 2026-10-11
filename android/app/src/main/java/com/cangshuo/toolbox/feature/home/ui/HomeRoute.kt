package com.cangshuo.toolbox.feature.home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.core.ui.ToolboxIconButton
import com.cangshuo.toolbox.core.ui.ToolboxMotion
import com.cangshuo.toolbox.core.ui.ToolboxTopBar
import com.cangshuo.toolbox.core.ui.ToolboxScaffold
import com.cangshuo.toolbox.feature.auth.ui.AuthRoute
import com.cangshuo.toolbox.feature.auth.ui.AuthViewModel
import com.cangshuo.toolbox.feature.favorites.ui.FavoriteButton
import com.cangshuo.toolbox.feature.favorites.ui.FavoriteMessage
import com.cangshuo.toolbox.feature.favorites.ui.FavoritesUiState
import com.cangshuo.toolbox.feature.favorites.ui.FavoritesViewModel
import com.cangshuo.toolbox.feature.feedback.ui.FeedbackRoute
import com.cangshuo.toolbox.feature.recent.ui.RecentScreen
import com.cangshuo.toolbox.feature.recent.ui.RecentViewModel
import com.cangshuo.toolbox.feature.search.ui.SearchRoute
import com.cangshuo.toolbox.feature.settings.ui.SettingsScreen

@Composable
fun HomeRoute(
    factory: ViewModelProvider.Factory,
    searchFactory: ViewModelProvider.Factory,
    favoritesFactory: ViewModelProvider.Factory,
    recentFactory: ViewModelProvider.Factory,
    authFactory: ViewModelProvider.Factory,
    syncFactory: ViewModelProvider.Factory,
    extrasFactory: ViewModelProvider.Factory,
    feedbackFactory: ViewModelProvider.Factory,
    diagnosticsFactory: ViewModelProvider.Factory,
    gridColumns: Int = 0,
    settingsFactory: ViewModelProvider.Factory,
    accentFactory: ViewModelProvider.Factory,
) {
    val model: HomeViewModel = viewModel(factory = factory)
    val favorites: FavoritesViewModel = viewModel(key = "local.favorites", factory = favoritesFactory)
    val recent: RecentViewModel = viewModel(key = "local.recent", factory = recentFactory)
    val auth: AuthViewModel = viewModel(key = "account.auth", factory = authFactory)
    val authState by auth.uiState.collectAsStateWithLifecycle()
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

    BackHandler(enabled = openedTool != null || state.isSearchOpen || state.overlay != null || state.tab != HomeTab.HOME) {
        when {
            openedTool != null -> model.closeTool()
            state.isSearchOpen -> model.closeSearch()
            state.overlay != null -> model.closeOverlay()
            else -> model.selectTab(HomeTab.HOME)
        }
    }

    val page: HomePage = when {
        openedTool != null -> HomePage.Tool(openedTool)
        state.isSearchOpen -> HomePage.Search
        state.overlay == HomeOverlay.RECENT -> HomePage.Recent
        state.overlay == HomeOverlay.SETTINGS -> HomePage.Settings
        state.overlay == HomeOverlay.AUTH -> HomePage.Auth
        state.overlay == HomeOverlay.FEEDBACK -> HomePage.Feedback
        else -> HomePage.Home
    }
    // List position, scroll offsets and form drafts survive forward/back navigation.
    val pageStates = rememberSaveableStateHolder()
    val routeContent: @Composable (HomePage) -> Unit = { target ->
        when (target) {
            HomePage.Home -> HomeScreen(
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
                onOpenRecent = model::openRecent,
                onOpenSettings = model::openSettings,
                onOpenAuth = { model.openOverlay(HomeOverlay.AUTH) },
                onOpenFeedback = { model.openOverlay(HomeOverlay.FEEDBACK) },
                onShowUnavailable = model::showFeatureUnavailable,
                homeExtras = { HomeExtrasRoute(extrasFactory) { model.selectTool(it, applicationContext) } },
                gridColumns = gridColumns,
                authState = authState,
            )
            HomePage.Search -> SearchRoute(
                factory = searchFactory,
                favoriteState = favoriteState,
                recentCodes = recentState.codes,
                onSetFavorite = favorites::changeFavorite,
                onClose = model::closeSearch,
                onToolSelected = { model.selectTool(it, applicationContext) },
            )
            HomePage.Recent -> RecentScreen(
                state = recentState,
                onToolSelected = { model.selectTool(it, applicationContext) },
                onClear = recent::clear,
                onRetry = recent::refresh,
                onClose = model::closeOverlay,
            )
            HomePage.Settings -> SettingsScreen(
                syncFactory = syncFactory,
                factory = settingsFactory,
                diagnosticsFactory = diagnosticsFactory,
                accentFactory = accentFactory,
                onClose = model::closeOverlay,
                onShowUnavailable = model::showFeatureUnavailable,
            )
            HomePage.Auth -> OverlayPage(stringResource(if(authState.account != null) R.string.auth_profile_heading
                else if(authState.registering) R.string.auth_register else R.string.auth_login), model::closeOverlay) { modifier ->
                AuthRoute(authFactory, modifier, syncFactory, feedbackFactory, diagnosticsFactory)
            }
            HomePage.Feedback -> OverlayPage(stringResource(R.string.profile_feedback), model::closeOverlay) { modifier ->
                Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    FeedbackRoute(feedbackFactory) { model.openOverlay(HomeOverlay.AUTH) }
                }
            }
            is HomePage.Tool -> ToolDetailPage(
                definition = target.definition,
                favoriteState = favoriteState,
                onClose = model::closeTool,
                onSetFavorite = favorites::changeFavorite,
            )
        }
    }
    AnimatedContent(
        targetState = page,
        contentKey = { it.key },
        transitionSpec = {
            if (initialState is HomePage.Home && targetState !is HomePage.Home) ToolboxMotion.forward()
            else if (initialState !is HomePage.Home && targetState is HomePage.Home) ToolboxMotion.backward()
            else ToolboxMotion.tab()
        },
        label = "home-page-transition",
    ) { target ->
        // Tool pages open fresh on every launch; other pages keep their previous state.
        if (target is HomePage.Tool) routeContent(target)
        else pageStates.SaveableStateProvider(target.key) { routeContent(target) }
    }

    state.notice?.let {
        AlertDialog(
            onDismissRequest = model::dismissNotice,
            title = { Text(stringResource(R.string.feature_unavailable_title)) },
            text = { Text(stringResource(R.string.feature_unavailable_message)) },
            confirmButton = { TextButton(onClick = model::dismissNotice) { Text(stringResource(R.string.action_understood)) } },
        )
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = model::dismissMessage,
            title = { Text(stringResource(R.string.tool_unavailable_title)) },
            text = { Text(stringResource(message.labelResource())) },
            confirmButton = {
                TextButton(onClick = model::dismissMessage) { Text(stringResource(R.string.action_understood)) }
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

    data object Home : HomePage { override val key: String = "home" }
    data object Search : HomePage { override val key: String = "search" }
    data object Recent : HomePage { override val key: String = "recent" }
    data object Settings : HomePage { override val key: String = "settings" }
    data object Auth : HomePage { override val key: String = "auth" }
    data object Feedback : HomePage { override val key: String = "feedback" }
    data class Tool(val definition: ToolDefinition) : HomePage { override val key: String get() = "tool:${definition.code}" }
}

/** Full-screen overlay page with a back top bar and shared hairline divider. */
@Composable
private fun OverlayPage(
    title: String,
    onClose: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    ToolboxScaffold(title = title, onBack = onClose, content = content)
}

@Composable
private fun ToolDetailPage(
    definition: ToolDefinition,
    favoriteState: FavoritesUiState,
    onClose: () -> Unit,
    onSetFavorite: (String, Boolean) -> Unit,
) {
    ToolboxScaffold(
        title = definition.metadata.name,
        onBack = onClose,
        actions = {
                        FavoriteButton(
                            selected = definition.code in favoriteState.codes,
                            enabled = favoriteState.ready && definition.code !in favoriteState.pendingCodes,
                            saving = definition.code in favoriteState.pendingCodes,
                            toolName = definition.metadata.name,
                            onChange = { onSetFavorite(definition.code, it) },
                        )
        },
    ) { modifier ->
        // WEB tools (Decision 019) are hosted on the official site and opened in the shared
        // WebView container instead of a native screen.
        Box(modifier) {
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
