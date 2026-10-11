package com.cangshuo.toolbox.feature.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolListItem
import com.cangshuo.toolbox.core.ui.ToolboxScaffold
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.favorites.ui.FavoriteButton
import com.cangshuo.toolbox.feature.favorites.ui.FavoritesUiState
import com.cangshuo.toolbox.feature.home.ui.labelResource
import com.cangshuo.toolbox.feature.home.ui.toolIconResource
import com.cangshuo.toolbox.feature.search.domain.MAX_SEARCH_QUERY_LENGTH
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun SearchScreen(
    state: SearchUiState,
    favoriteState: FavoritesUiState,
    onSetFavorite: (String, Boolean) -> Unit,
    onQueryChanged: (String) -> Unit,
    onCategorySelected: (ToolCategory?) -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    onToolSelected: (String) -> Unit,
) {
    ToolboxScaffold(title = stringResource(R.string.search_title), onBack = onClose) { modifier ->
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        ) {
            item(key = "query") {
                ToolboxTextField(
                    value = state.query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(R.string.home_search_hint),
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            ToolboxTextButton(onClick = { onQueryChanged("") }) {
                                Text(stringResource(R.string.search_clear_query))
                            }
                        }
                    },
                    supportingText = {
                        Text(
                            if (state.queryTooLong) pluralStringResource(
                                R.plurals.search_query_too_long, MAX_SEARCH_QUERY_LENGTH, MAX_SEARCH_QUERY_LENGTH,
                            ) else stringResource(R.string.search_local_hint),
                        )
                    },
                    isError = state.queryTooLong,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                )
            }
            item(key = "categories") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "all") {
                        FilterChip(
                            selected = state.category == null,
                            onClick = { onCategorySelected(null) },
                            label = { Text(stringResource(R.string.category_all)) },
                            colors = chipColors(),
                        )
                    }
                    items(ToolCategory.entries, key = { it.code }) { category ->
                        FilterChip(
                            selected = state.category == category,
                            onClick = { onCategorySelected(category) },
                            label = { Text(stringResource(category.labelResource())) },
                            colors = chipColors(),
                        )
                    }
                }
            }
            val results = state.results
            if (results is SearchResultsState.Content) {
                item(key = "count") {
                    Text(
                        pluralStringResource(R.plurals.search_result_count, results.tools.size, results.tools.size),
                        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(results.tools, key = { "result:" + it.code }) { tool ->
                    ToolListItem(
                        title = tool.name,
                        subtitle = tool.description,
                        icon = toolIconResource(tool.icon),
                        onClick = { onToolSelected(tool.code) },
                        trailing = {
                            FavoriteButton(
                                selected = tool.code in favoriteState.codes,
                                enabled = favoriteState.ready && tool.code !in favoriteState.pendingCodes,
                                toolName = tool.name,
                                onChange = { onSetFavorite(tool.code, it) },
                                saving = tool.code in favoriteState.pendingCodes,
                            )
                        },
                    )
                }
            } else {
                item(key = "status") {
                    if (results == SearchResultsState.Loading) {
                        ToolboxLoadingState(stringResource(R.string.search_loading), Modifier.padding(top = 20.dp))
                    } else {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            when (results) {
                                SearchResultsState.Loading -> Unit
                                SearchResultsState.Error -> {
                                    Text(stringResource(R.string.search_error_title), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(R.string.search_error_description),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ToolboxButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                                }
                                SearchResultsState.Empty -> {
                                    Text(stringResource(R.string.search_empty_title), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(R.string.search_empty_description),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (state.query.isNotEmpty() || state.category != null) {
                                        ToolboxTextButton(onClick = onClearFilters) {
                                            Text(stringResource(R.string.search_clear_filters))
                                        }
                                    }
                                }
                                is SearchResultsState.Content -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.primary,
)

@Preview(showBackground = true, name = "Search Chinese", locale = "zh")
@Preview(showBackground = true, name = "Search English", locale = "en")
@Composable
private fun EmptySearchPreview() {
    ToolboxTheme {
        SearchScreen(
            state = SearchUiState(query = "UUID", results = SearchResultsState.Empty),
            favoriteState = FavoritesUiState(),
            onSetFavorite = { _, _ -> },
            onQueryChanged = {}, onCategorySelected = {}, onClearFilters = {}, onRetry = {},
            onSubmit = {}, onClose = {}, onToolSelected = {},
        )
    }
}
