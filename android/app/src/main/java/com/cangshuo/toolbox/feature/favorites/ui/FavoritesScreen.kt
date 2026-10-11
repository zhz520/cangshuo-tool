package com.cangshuo.toolbox.feature.favorites.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolListItem
import com.cangshuo.toolbox.feature.home.ui.toolIconResource
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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        val list = state.list
        if (list is FavoritesListState.Content) {
            item(key = "count") {
                Text(
                    pluralStringResource(R.plurals.favorite_count, list.items.size, list.items.size),
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(list.items, key = { "favorite:" + it.code }) { item ->
                val metadata = item.metadata
                if (metadata != null) {
                    ToolListItem(
                        title = metadata.name,
                        subtitle = metadata.description,
                        icon = toolIconResource(metadata.icon),
                        onClick = { onToolSelected(item.code) },
                        trailing = {
                            FavoriteButton(
                                selected = true,
                                enabled = item.code !in state.pendingCodes,
                                toolName = metadata.name,
                                onChange = { onSetFavorite(item.code, it) },
                                saving = item.code in state.pendingCodes,
                            )
                        },
                    )
                } else {
                    ToolListItem(
                        title = stringResource(R.string.favorite_missing_title),
                        subtitle = stringResource(R.string.favorite_missing_description),
                        icon = null,
                        onClick = null,
                        trailing = {
                            ToolboxTextButton(
                                onClick = { onSetFavorite(item.code, false) },
                                enabled = item.code !in state.pendingCodes,
                            ) {
                                if (item.code in state.pendingCodes) {
                                    ToolboxLoadingIndicator(compact = true)
                                } else {
                                    Text(stringResource(R.string.favorite_remove))
                                }
                            }
                        },
                    )
                }
            }
        } else {
            item(key = "status") {
                if (list == FavoritesListState.Loading) {
                    ToolboxLoadingState(stringResource(R.string.favorites_loading), Modifier.padding(top = 20.dp))
                } else {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (list) {
                            FavoritesListState.Loading -> Unit
                            FavoritesListState.Empty -> {
                                Text(stringResource(R.string.favorites_empty_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.favorites_empty_description),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                ToolboxButton(onClick = onBrowse) { Text(stringResource(R.string.action_browse_tools)) }
                            }
                            FavoritesListState.Error -> {
                                Text(stringResource(R.string.favorites_error_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.favorites_error_description),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                ToolboxButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                            }
                            is FavoritesListState.Content -> Unit
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
