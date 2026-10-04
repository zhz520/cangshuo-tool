package com.cangshuo.toolbox.feature.favorites.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.feature.home.ui.ToolCard
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun FavoritesScreen(
    state: FavoritesUiState,
    onSetFavorite: (String, Boolean) -> Unit,
    onToolSelected: (String) -> Unit,
    onRetry: () -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = (maxWidth / 168.dp).toInt().coerceIn(2, 5)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.nav_favorites), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.favorites_local_hint), style = MaterialTheme.typography.bodyMedium)
                }
            }
            val list = state.list
            if (list is FavoritesListState.Content) {
                item(key = "count", span = { GridItemSpan(maxLineSpan) }) {
                    Text(pluralStringResource(R.plurals.favorite_count, list.items.size, list.items.size))
                }
                items(list.items, key = { "favorite:" + it.code }) { item ->
                    val metadata = item.metadata
                    if (metadata != null) {
                        ToolCard(
                            tool = metadata,
                            onClick = { onToolSelected(item.code) },
                            isFavorite = true,
                            favoriteEnabled = item.code !in state.pendingCodes,
                            favoriteSaving = item.code in state.pendingCodes,
                            onFavoriteChanged = { onSetFavorite(item.code, it) },
                        )
                    } else {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.favorite_missing_title), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.favorite_missing_description))
                                TextButton(
                                    onClick = { onSetFavorite(item.code, false) },
                                    enabled = item.code !in state.pendingCodes,
                                ) {
                                    if (item.code in state.pendingCodes) {
                                        ToolboxLoadingIndicator(compact = true)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.favorite_saving))
                                    } else {
                                        Text(stringResource(R.string.favorite_remove))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                    if (list == FavoritesListState.Loading) {
                        ToolboxLoadingState(stringResource(R.string.favorites_loading))
                    } else {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(
                                Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                when (list) {
                                    FavoritesListState.Loading -> Unit
                                    FavoritesListState.Empty -> {
                                        Text(stringResource(R.string.favorites_empty_title), style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.favorites_empty_description))
                                        Button(onClick = onBrowse) { Text(stringResource(R.string.action_browse_tools)) }
                                    }
                                    FavoritesListState.Error -> {
                                        Text(stringResource(R.string.favorites_error_title), style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.favorites_error_description))
                                        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                                    }
                                    is FavoritesListState.Content -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Favorites Chinese", locale = "zh")
@Preview(showBackground = true, name = "Favorites English", locale = "en")
@Composable
private fun EmptyFavoritesPreview() {
    ToolboxTheme {
        FavoritesScreen(
            state = FavoritesUiState(list = FavoritesListState.Empty),
            onSetFavorite = { _, _ -> }, onToolSelected = {}, onRetry = {}, onBrowse = {},
        )
    }
}
