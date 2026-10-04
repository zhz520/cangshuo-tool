package com.cangshuo.toolbox.feature.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.feature.home.ui.ToolCard
import com.cangshuo.toolbox.feature.home.ui.labelResource
import com.cangshuo.toolbox.feature.search.domain.MAX_SEARCH_QUERY_LENGTH
import com.cangshuo.toolbox.ui.theme.ToolboxTheme
import com.cangshuo.toolbox.feature.favorites.ui.FavoritesUiState

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
    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_back), stringResource(R.string.action_back))
                }
                Text(stringResource(R.string.search_title), style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).imePadding()) {
            val columns = (maxWidth / 168.dp).toInt().coerceIn(2, 5)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "query", span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.search_title)) },
                        placeholder = { Text(stringResource(R.string.home_search_hint)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                TextButton(onClick = { onQueryChanged("") }) {
                                    Text(stringResource(R.string.search_clear_query))
                                }
                            }
                        },
                        supportingText = {
                            Text(
                                if (state.queryTooLong) pluralStringResource(
                                    R.plurals.search_query_too_long, MAX_SEARCH_QUERY_LENGTH, MAX_SEARCH_QUERY_LENGTH,
                                )
                                else stringResource(R.string.search_local_hint),
                            )
                        },
                        isError = state.queryTooLong,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    )
                }
                item(key = "categories", span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "all") {
                            FilterChip(
                                selected = state.category == null,
                                onClick = { onCategorySelected(null) },
                                label = { Text(stringResource(R.string.category_all)) },
                            )
                        }
                        items(ToolCategory.entries, key = { it.code }) { category ->
                            FilterChip(
                                selected = state.category == category,
                                onClick = { onCategorySelected(category) },
                                label = { Text(stringResource(category.labelResource())) },
                            )
                        }
                    }
                }
                val results = state.results
                if (results is SearchResultsState.Content) {
                    item(key = "count", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            pluralStringResource(R.plurals.search_result_count, results.tools.size, results.tools.size),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(results.tools, key = { "result:" + it.code }) { tool ->
                        ToolCard(
                            tool, onClick = { onToolSelected(tool.code) },
                            isFavorite = tool.code in favoriteState.codes,
                            favoriteEnabled = favoriteState.ready && tool.code !in favoriteState.pendingCodes,
                            onFavoriteChanged = { onSetFavorite(tool.code, it) },
                        )
                    }
                } else {
                    item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(
                                Modifier.fillMaxWidth().padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                when (results) {
                                    SearchResultsState.Loading -> {
                                        CircularProgressIndicator()
                                        Text(stringResource(R.string.search_loading))
                                    }
                                    SearchResultsState.Error -> {
                                        Text(stringResource(R.string.search_error_title), style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.search_error_description))
                                        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                                    }
                                    SearchResultsState.Empty -> {
                                        Text(stringResource(R.string.search_empty_title), style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.search_empty_description))
                                        if (state.query.isNotEmpty() || state.category != null) {
                                            TextButton(onClick = onClearFilters) {
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
}

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
