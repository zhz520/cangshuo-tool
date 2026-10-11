package com.cangshuo.toolbox.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import com.cangshuo.toolbox.core.ui.ToolboxMotion
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.ui.ToolGridTile
import com.cangshuo.toolbox.core.ui.ToolListItem
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxChevron
import com.cangshuo.toolbox.core.ui.ToolboxIconButton
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTopBar
import com.cangshuo.toolbox.core.ui.ToolboxScaffold
import com.cangshuo.toolbox.core.ui.ToolboxDivider
import com.cangshuo.toolbox.core.ui.toolboxGridColumns
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import com.cangshuo.toolbox.feature.auth.ui.AuthUiState
import com.cangshuo.toolbox.core.ui.ToolboxAvatar
import com.cangshuo.toolbox.feature.favorites.ui.FavoritesScreen
import com.cangshuo.toolbox.feature.favorites.ui.FavoritesUiState
import com.cangshuo.toolbox.feature.home.domain.HomeContent
import com.cangshuo.toolbox.feature.recent.ui.RecentListState
import com.cangshuo.toolbox.feature.recent.ui.RecentUiState

private const val RECENT_PREVIEW_LIMIT = 2
private const val COMMON_GRID_LIMIT = 8
private const val EXPLORE_LIMIT = 4
private const val POPULAR_PREVIEW_LIMIT = 4

@Composable
fun HomeScreen(
    state: HomeUiState,
    onTabSelected: (HomeTab) -> Unit,
    onCategorySelected: (ToolCategory?) -> Unit,
    onToolSelected: (String) -> Unit,
    onRetry: () -> Unit,
    onSearch: () -> Unit,
    favoriteState: FavoritesUiState,
    onSetFavorite: (String, Boolean) -> Unit,
    onFavoritesRetry: () -> Unit,
    recentState: RecentUiState,
    onRecentRetry: () -> Unit,
    onClearRecent: () -> Unit,
    onCatalogRefresh: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenPopular: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenFeedback: () -> Unit,
    onShowUnavailable: () -> Unit,
    homeExtras: (@Composable () -> Unit)? = null,
    gridColumns: Int = 0,
    authState: AuthUiState = AuthUiState(),
) {
    val tabState = rememberSaveableStateHolder()
    ToolboxScaffold(
        title = stringResource(if (state.tab == HomeTab.HOME) R.string.app_name else state.tab.labelResource()),
        actions = {
            when (state.tab) {
                HomeTab.HOME -> {
                    ToolboxIconButton(R.drawable.ic_person, stringResource(R.string.profile_login), onOpenAuth)
                    ToolboxIconButton(R.drawable.ic_more, stringResource(R.string.home_more), onOpenSettings)
                }
                HomeTab.TOOLS -> ToolboxIconButton(R.drawable.ic_search, stringResource(R.string.home_search_hint), onSearch)
                HomeTab.PROFILE -> ToolboxIconButton(R.drawable.ic_gear, stringResource(R.string.profile_settings), onOpenSettings)
                HomeTab.FAVORITES -> Unit
            }
        },
        bottomBar = {
            Column {
            ToolboxDivider(startIndent = 0.dp)
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            ) {
                HomeTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.tab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = { Icon(painterResource(tab.iconResource()), contentDescription = null, modifier = Modifier.size(22.dp)) },
                        label = { Text(stringResource(tab.labelResource())) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
            }
        },
    ) { contentModifier ->
        AnimatedContent(targetState=state.tab,transitionSpec={ToolboxMotion.tab()},label="home-tab-transition",modifier=contentModifier) { tab ->
        tabState.SaveableStateProvider(tab.name) {
        when (tab) {
            HomeTab.HOME -> HomeTabContent(
                state, Modifier.fillMaxSize(), onCategorySelected, onToolSelected, onSearch, onRetry,
                    favoriteState, onSetFavorite, recentState, onOpenRecent, onOpenPopular, onCatalogRefresh, homeExtras,
            )
            HomeTab.TOOLS -> LibraryContent(
                state, Modifier.fillMaxSize(), onCategorySelected, onToolSelected,
                favoriteState, onSetFavorite, onSearch, onRetry, gridColumns,
            )
            HomeTab.FAVORITES -> FavoritesScreen(
                modifier = Modifier.fillMaxSize(),
                state = favoriteState,
                onSetFavorite = onSetFavorite,
                onToolSelected = onToolSelected,
                onRetry = onFavoritesRetry,
                onBrowse = { onTabSelected(HomeTab.TOOLS) },
            )
            HomeTab.PROFILE -> ProfileContent(
                Modifier.fillMaxSize(), onOpenAuth, onOpenSettings, onOpenFeedback,
                onOpenRecent = onOpenRecent,
                onOpenFavorites = { onTabSelected(HomeTab.FAVORITES) },
                onShowUnavailable = onShowUnavailable,
                authState = authState,
            )
        }
        }
        }
    }
}

@Composable
private fun HomeTabContent(
    state: HomeUiState,
    modifier: Modifier,
    onCategorySelected: (ToolCategory?) -> Unit,
    onToolSelected: (String) -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    favoriteState: FavoritesUiState,
    onSetFavorite: (String, Boolean) -> Unit,
    recentState: RecentUiState,
    onOpenRecent: () -> Unit,
    onOpenPopular: () -> Unit,
    onCatalogRefresh: () -> Unit,
    homeExtras: (@Composable () -> Unit)?,
) {
    val content = (state.catalog as? HomeCatalogState.Content)?.value
        ?: (state.catalog as? HomeCatalogState.Empty)?.value
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
    ) {
        item(key = "search") { SearchEntry(onSearch) }
        if (content != null) {
            item(key = "common-heading") { ToolSectionHeader(stringResource(R.string.home_common), Modifier.padding(top = 12.dp)) }
            item(key = "common-grid") {
                val tools = content.commonTools.take(COMMON_GRID_LIMIT)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // Same four-column rhythm as the tool library; only an overflow row is centered.
                    val columns = toolboxGridColumns(maxWidth.value, LocalDensity.current.fontScale, 4)
                    val rows = tools.chunked(columns)
                    Column(verticalArrangement = Arrangement.spacedBy(ToolboxDimens.gridRowGap)) {
                        rows.forEachIndexed { index, row ->
                            val missing = if (index == rows.lastIndex && rows.size > 1) columns - row.size else 0
                            val leading = missing / 2
                            Row(horizontalArrangement = Arrangement.spacedBy(ToolboxDimens.gridGap)) {
                                repeat(leading) { Box(Modifier.weight(1f)) }
                                row.forEach { tool ->
                                    ToolGridTile(tool.name, toolIconResource(tool.icon), Modifier.weight(1f)) { onToolSelected(tool.code) }
                                }
                                repeat(missing - leading) { Box(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            item(key = "recent-heading") {
                ToolSectionHeader(stringResource(R.string.home_recent), Modifier.padding(top = 8.dp)) {
                    ToolboxTextButton(onClick = onOpenRecent) {
                        Text(stringResource(R.string.home_all_categories), style = MaterialTheme.typography.labelMedium)
                        ToolboxChevron()
                    }
                }
            }
            val recentTools = (recentState.list as? RecentListState.Content)?.items.orEmpty()
                .mapNotNull { entry -> entry.metadata?.let { entry.code to it } }
                .take(RECENT_PREVIEW_LIMIT)
            if (recentTools.isEmpty()) {
                item(key = "recent-empty") {
                    Text(
                        stringResource(R.string.home_recent_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(recentTools, key = { "recent:" + it.first }) { (code, metadata) ->
                    ToolListItem(
                        title = metadata.name,
                        subtitle = metadata.description,
                        icon = toolIconResource(metadata.icon),
                        onClick = { onToolSelected(code) },
                        trailing = { ToolboxChevron() },
                    )
                }
            }
            val popularRanking = content.popularTools
            if (popularRanking.isNotEmpty()) {
                item(key = "popular-heading") {
                    ToolSectionHeader(stringResource(R.string.home_popular_ranking), Modifier.padding(top = 8.dp)) {
                        ToolboxTextButton(onClick = onOpenPopular) {
                            Text(stringResource(R.string.home_all_categories), style = MaterialTheme.typography.labelMedium)
                            ToolboxChevron()
                        }
                    }
                }
                val rankingByCode = content.tools.associateBy { it.code }
                items(
                    popularRanking.take(POPULAR_PREVIEW_LIMIT).mapNotNull { entry -> rankingByCode[entry.code]?.let { entry to it } },
                    key = { "popular:" + it.second.code },
                ) { (rankEntry, metadata) ->
                    ToolListItem(
                        title = metadata.name,
                        subtitle = stringResource(R.string.popular_use_count, rankEntry.useCount),
                        icon = toolIconResource(metadata.icon),
                        onClick = { onToolSelected(metadata.code) },
                        trailing = { ToolboxChevron() },
                    )
                }
            }
            item(key = "explore-heading") {
                ToolSectionHeader(stringResource(R.string.home_explore_categories), Modifier.padding(top = 8.dp)) {
                    ToolboxTextButton(onClick = { onCategorySelected(null) }) {
                        Text(stringResource(R.string.home_all_categories), style = MaterialTheme.typography.labelMedium)
                        ToolboxChevron()
                    }
                }
            }
            item(key = "explore") {
                val categories = content.categories.take(EXPLORE_LIMIT)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    categories.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { entry ->
                                ExploreCard(
                                    name = stringResource(entry.category.labelResource()),
                                    count = entry.toolCount,
                                    icon = entry.category.iconResource(),
                                    modifier = Modifier.weight(1f),
                                    onClick = { onCategorySelected(entry.category) },
                                )
                            }
                            if (row.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            }
            if (homeExtras != null) {
                item(key = "home-extras") { Box(Modifier.padding(top = 12.dp)) { homeExtras() } }
            }
        } else {
            item(key = "status") {
                Box(Modifier.padding(top = 24.dp)) {
                    if (state.catalog == HomeCatalogState.Loading) {
                        ToolboxLoadingState(stringResource(R.string.home_loading))
                    } else {
                        StatusMessage(
                            title = stringResource(R.string.home_error_title),
                            description = stringResource(R.string.home_error_description),
                            onRetry = onRetry,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryContent(
    state: HomeUiState,
    modifier: Modifier,
    onCategorySelected: (ToolCategory?) -> Unit,
    onToolSelected: (String) -> Unit,
    favoriteState: FavoritesUiState,
    onSetFavorite: (String, Boolean) -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    gridColumns: Int,
) {
    val content = (state.catalog as? HomeCatalogState.Content)?.value
        ?: (state.catalog as? HomeCatalogState.Empty)?.value
    Column(modifier) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { SearchEntry(onSearch) }
        if (content != null) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .width(ToolboxDimens.categoryWidth)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .verticalScroll(rememberScrollState()),
                ) {
                    LibraryCategory(stringResource(R.string.category_all), state.category == null) { onCategorySelected(null) }
                    content.categories.forEach { entry ->
                        LibraryCategory(stringResource(entry.category.labelResource()), state.category == entry.category) {
                            onCategorySelected(entry.category)
                        }
                    }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxSize()) {
                val columns = toolboxGridColumns(maxWidth.value - 28f, LocalDensity.current.fontScale, gridColumns)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
                        ToolSectionHeader(
                            title = state.category?.let { stringResource(it.labelResource()) } ?: stringResource(R.string.home_all_tools),
                            trailing = {
                                Text(
                                    stringResource(R.string.library_count, content.tools.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                    if (content.tools.isEmpty()) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(R.string.home_empty_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 20.dp),
                            )
                        }
                    } else {
                        items(content.tools, key = { it.code }) { tool ->
                            ToolGridTile(tool.name, toolIconResource(tool.icon)) { onToolSelected(tool.code) }
                        }
                    }
                }
                }
            }
        } else {
            Box(Modifier.padding(16.dp)) {
                if (state.catalog == HomeCatalogState.Loading) {
                    ToolboxLoadingState(stringResource(R.string.home_loading))
                } else {
                    StatusMessage(stringResource(R.string.home_error_title), stringResource(R.string.home_error_description), onRetry)
                }
            }
        }
    }
}

@Composable
private fun LibraryCategory(label: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if(selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceVariant, label="category-background")
    val foreground by animateColorAsState(if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label="category-foreground")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ToolboxDimens.touchTarget)
            .background(background)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(MaterialTheme.shapes.small)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = foreground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ProfileContent(
    modifier: Modifier,
    onOpenAuth: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenFavorites: () -> Unit,
    onShowUnavailable: () -> Unit,
    authState: AuthUiState,
) {
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
        item(key = "account") {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenAuth).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ToolboxAvatar(authState.avatar, authState.avatarLoading)
                Column(Modifier.weight(1f)) {
                    Text(authState.account?.nickname ?: stringResource(R.string.profile_login), style = MaterialTheme.typography.titleLarge)
                    Text(
                        authState.account?.email ?: stringResource(R.string.profile_sync_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                ToolboxChevron()
            }
            ToolboxDivider(startIndent = 0.dp)
        }
        item(key = "menu") {
            Column {
                ToolListItem(stringResource(R.string.profile_my_favorites), icon = R.drawable.ic_star, onClick = onOpenFavorites, trailing = { ToolboxChevron() })
                ToolListItem(stringResource(R.string.profile_recent), icon = R.drawable.ic_timestamp, onClick = onOpenRecent, trailing = { ToolboxChevron() })
                ToolListItem(stringResource(R.string.profile_stats), icon = R.drawable.ic_chart, onClick = onShowUnavailable, trailing = { ToolboxChevron() })
                ToolListItem(stringResource(R.string.profile_feedback), icon = R.drawable.ic_chat, onClick = onOpenFeedback, trailing = { ToolboxChevron() })
                ToolListItem(stringResource(R.string.profile_about), icon = R.drawable.ic_info, onClick = onShowUnavailable, trailing = { ToolboxChevron() }, showDivider = false)
            }
        }
    }
}

@Composable
private fun SearchEntry(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = ToolboxDimens.touchTarget).padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.home_search_hint), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ExploreCard(name: String, count: Int, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            Text(
                stringResource(R.string.library_count, count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun StatusMessage(title: String, description: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        ToolboxButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}
