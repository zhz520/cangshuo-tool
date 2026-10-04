package com.cangshuo.toolbox.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.feature.home.domain.HomeCategory
import com.cangshuo.toolbox.feature.home.domain.HomeContent
import com.cangshuo.toolbox.ui.theme.ToolboxTheme
import com.cangshuo.toolbox.feature.favorites.ui.FavoriteButton
import com.cangshuo.toolbox.feature.favorites.ui.FavoritesScreen
import com.cangshuo.toolbox.feature.favorites.ui.FavoritesUiState
import com.cangshuo.toolbox.feature.recent.ui.RecentListState
import com.cangshuo.toolbox.feature.recent.ui.RecentUiState

private const val RECENT_PREVIEW_LIMIT = 6

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
) {
    var confirmClearRecent by remember { mutableStateOf(false) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.tab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = { Icon(painterResource(tab.iconResource()), contentDescription = null) },
                        label = { Text(stringResource(tab.labelResource())) },
                    )
                }
            }
        },
    ) { padding ->
        when (state.tab) {
            HomeTab.FAVORITES -> FavoritesScreen(
                modifier = Modifier.padding(padding),
                state = favoriteState,
                onSetFavorite = onSetFavorite,
                onToolSelected = onToolSelected,
                onRetry = onFavoritesRetry,
                onBrowse = { onCategorySelected(null) },
            )
            HomeTab.PROFILE -> InformationPage(
                modifier = Modifier.padding(padding),
                title = stringResource(R.string.nav_profile),
                icon = R.drawable.ic_person,
                heading = stringResource(R.string.profile_guest_title),
                description = stringResource(R.string.profile_guest_description),
                onBrowse = { onCategorySelected(null) },
                showPrivacy = true,
            )
            HomeTab.HOME, HomeTab.TOOLS -> CatalogPage(
                modifier = Modifier.padding(padding),
                state = state,
                onCategorySelected = onCategorySelected,
                onToolSelected = onToolSelected,
                onRetry = onRetry,
                onSearch = onSearch,
                favoriteState = favoriteState,
                onSetFavorite = onSetFavorite,
                recentState = recentState,
                onRecentRetry = onRecentRetry,
                onClearRecentRequest = { confirmClearRecent = true },
            )
        }
    }
    if (confirmClearRecent) {
        AlertDialog(
            onDismissRequest = { confirmClearRecent = false },
            title = { Text(stringResource(R.string.home_recent_clear_title)) },
            text = { Text(stringResource(R.string.home_recent_clear_message)) },
            confirmButton = {
                TextButton(onClick = { confirmClearRecent = false; onClearRecent() }) {
                    Text(stringResource(R.string.home_recent_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearRecent = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CatalogPage(
    modifier: Modifier,
    state: HomeUiState,
    onCategorySelected: (ToolCategory?) -> Unit,
    onToolSelected: (String) -> Unit,
    onRetry: () -> Unit,
    onSearch: () -> Unit,
    favoriteState: FavoritesUiState,
    onSetFavorite: (String, Boolean) -> Unit,
    recentState: RecentUiState,
    onRecentRetry: () -> Unit,
    onClearRecentRequest: () -> Unit,
) {
    val content = when (val catalog = state.catalog) {
        is HomeCatalogState.Content -> catalog.value
        is HomeCatalogState.Empty -> catalog.value
        else -> null
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = (maxWidth / 168.dp).toInt().coerceIn(2, 5)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                PageHeader(state.tab)
            }
            if (state.tab == HomeTab.HOME) {
                item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                    HeroBanner(onBrowse = { onCategorySelected(null) })
                }
            }
            item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                SearchEntry(onSearch)
            }
            if (content != null) {
                item(key = "categories", span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(stringResource(R.string.home_categories))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item(key = "all") {
                                FilterChip(
                                    selected = state.category == null,
                                    onClick = { onCategorySelected(null) },
                                    label = {
                                        Text(
                                            stringResource(
                                                R.string.home_category_count,
                                                stringResource(R.string.category_all),
                                                content.totalToolCount,
                                            ),
                                        )
                                    },
                                )
                            }
                            items(content.categories, key = { it.category.code }) { entry ->
                                FilterChip(
                                    selected = state.category == entry.category,
                                    onClick = { onCategorySelected(entry.category) },
                                    label = {
                                        Text(
                                            stringResource(
                                                R.string.home_category_count,
                                                stringResource(entry.category.labelResource()),
                                                entry.toolCount,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                item(key = "catalog-heading", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(
                        title = stringResource(
                            if (state.tab == HomeTab.HOME) R.string.home_common else R.string.home_all_tools,
                        ),
                        trailing = {
                            Text(
                                pluralStringResource(
                                    R.plurals.tool_count,
                                    if (state.tab == HomeTab.HOME) content.commonTools.size else content.tools.size,
                                    if (state.tab == HomeTab.HOME) content.commonTools.size else content.tools.size,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
                if (content.tools.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyCatalog(
                            filtered = state.category != null,
                            onClear = { onCategorySelected(null) },
                        )
                    }
                } else {
                    val tools = if (state.tab == HomeTab.HOME) content.commonTools else content.tools
                    items(tools, key = { "tool:" + it.code }) { tool ->
                        ToolCard(
                            tool, onClick = { onToolSelected(tool.code) },
                            isFavorite = tool.code in favoriteState.codes,
                            favoriteEnabled = favoriteState.ready && tool.code !in favoriteState.pendingCodes,
                            favoriteSaving = tool.code in favoriteState.pendingCodes,
                            onFavoriteChanged = { onSetFavorite(tool.code, it) },
                        )
                    }
                    if (state.tab == HomeTab.HOME && content.tools.size > content.commonTools.size) {
                        item(key = "browse", span = { GridItemSpan(maxLineSpan) }) {
                            OutlinedButton(onClick = { onCategorySelected(null) }) {
                                Text(stringResource(R.string.action_browse_tools))
                            }
                        }
                    }
                }
                if (state.tab == HomeTab.HOME && content.featuredTools.isNotEmpty()) {
                    item(key = "featured-heading", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(stringResource(R.string.home_featured))
                    }
                    items(content.featuredTools, key = { "featured:" + it.code }) { tool ->
                        ToolCard(
                            tool, onClick = { onToolSelected(tool.code) },
                            isFavorite = tool.code in favoriteState.codes,
                            favoriteEnabled = favoriteState.ready && tool.code !in favoriteState.pendingCodes,
                            favoriteSaving = tool.code in favoriteState.pendingCodes,
                            onFavoriteChanged = { onSetFavorite(tool.code, it) },
                        )
                    }
                }
                if (state.tab == HomeTab.HOME) {
                    item(key = "recent-heading", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(stringResource(R.string.home_recent)) {
                            if (recentState.list is RecentListState.Content) {
                                TextButton(onClick = onClearRecentRequest) {
                                    Text(stringResource(R.string.home_recent_clear))
                                }
                            }
                        }
                    }
                    when (val recent = recentState.list) {
                        RecentListState.Loading -> item(key = "recent-status", span = { GridItemSpan(maxLineSpan) }) {
                            RecentStatusRow(stringResource(R.string.recent_loading), showIndicator = true)
                        }
                        RecentListState.Empty -> item(key = "recent-status", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(R.string.home_recent_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RecentListState.Error -> item(key = "recent-status", span = { GridItemSpan(maxLineSpan) }) {
                            RecentStatusRow(stringResource(R.string.recent_error), onRetry = onRecentRetry)
                        }
                        is RecentListState.Content -> {
                            val visible = recent.items.take(RECENT_PREVIEW_LIMIT).filter { it.metadata != null }
                            if (visible.isEmpty()) {
                                item(key = "recent-status", span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        stringResource(R.string.home_recent_description),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                items(visible, key = { "recent:" + it.code }) { entry ->
                                    entry.metadata?.let { metadata ->
                                        ToolCard(
                                            tool = metadata,
                                            onClick = { onToolSelected(entry.code) },
                                            isFavorite = entry.code in favoriteState.codes,
                                            favoriteEnabled = favoriteState.ready && entry.code !in favoriteState.pendingCodes,
                                            favoriteSaving = entry.code in favoriteState.pendingCodes,
                                            onFavoriteChanged = { onSetFavorite(entry.code, it) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                    if (state.catalog == HomeCatalogState.Loading) {
                        ToolboxLoadingState(stringResource(R.string.home_loading))
                    } else {
                        StatusCard(
                            title = stringResource(R.string.home_error_title),
                            description = stringResource(R.string.home_error_description),
                            action = {
                                Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageHeader(tab: HomeTab) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                painter = painterResource(R.drawable.ic_toolbox),
                contentDescription = null,
                modifier = Modifier.padding(12.dp).size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column {
            Text(
                stringResource(if (tab == HomeTab.HOME) R.string.app_name else tab.labelResource()),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroBanner(onBrowse: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.home_local_first),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.home_greeting),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.home_hero_description), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onBrowse) { Text(stringResource(R.string.action_browse_tools)) }
        }
    }
}

@Composable
private fun SearchEntry(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null)
            Text(
                stringResource(R.string.home_search_hint),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        trailing()
    }
}

@Composable
internal fun ToolCard(
    tool: ToolMetadata,
    onClick: () -> Unit,
    isFavorite: Boolean,
    favoriteEnabled: Boolean,
    onFavoriteChanged: (Boolean) -> Unit,
    favoriteSaving: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(toolIconResource(tool.icon)),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                FavoriteButton(isFavorite, favoriteEnabled, tool.name, onFavoriteChanged, saving = favoriteSaving)
            }
            Text(tool.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(tool.mode.labelResource()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmptyCatalog(filtered: Boolean, onClear: () -> Unit) {
    StatusCard(
        title = stringResource(if (filtered) R.string.home_category_empty_title else R.string.home_empty_title),
        description = stringResource(
            if (filtered) R.string.home_category_empty_description else R.string.home_empty_description,
        ),
        action = {
            if (filtered) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear_category)) }
            }
        },
    )
}

@Composable
private fun StatusCard(title: String, description: String, action: @Composable () -> Unit = {}) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_toolbox),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            action()
        }
    }
}

@Composable
private fun RecentStatusRow(
    message: String,
    onRetry: (() -> Unit)? = null,
    showIndicator: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showIndicator) ToolboxLoadingIndicator(compact = true)
        Text(
            message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
private fun InformationPage(
    modifier: Modifier,
    title: String,
    @DrawableRes icon: Int,
    heading: String,
    description: String,
    onBrowse: () -> Unit,
    showPrivacy: Boolean = false,
) {
    // Use a lazy container so large text and short landscape windows remain scrollable.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(painterResource(icon), null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text(heading, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onBrowse) { Text(stringResource(R.string.action_browse_tools)) }
            }
        }
        if (showPrivacy) {
            item {
                StatusCard(
                    title = stringResource(R.string.profile_privacy_title),
                    description = stringResource(R.string.profile_privacy_description),
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Home empty")
@Composable
private fun EmptyHomePreview() {
    ToolboxTheme {
        HomeScreen(
            state = HomeUiState(
                catalog = HomeCatalogState.Empty(
                    HomeContent(emptyList(), emptyList(), emptyList(), ToolCategory.entries.map { HomeCategory(it, 0) }, 0),
                ),
            ),
            onTabSelected = {},
            onCategorySelected = {},
            onToolSelected = {},
            onRetry = {},
            onSearch = {},
            favoriteState = FavoritesUiState(),
            onSetFavorite = { _, _ -> },
            onFavoritesRetry = {},
            recentState = RecentUiState(list = RecentListState.Empty),
            onRecentRetry = {},
            onClearRecent = {},
        )
    }
}
