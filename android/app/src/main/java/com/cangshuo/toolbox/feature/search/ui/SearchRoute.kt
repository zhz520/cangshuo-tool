package com.cangshuo.toolbox.feature.search.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.feature.favorites.ui.FavoritesUiState

@Composable
fun SearchRoute(
    factory: ViewModelProvider.Factory,
    favoriteState: FavoritesUiState,
    onSetFavorite: (String, Boolean) -> Unit,
    onClose: () -> Unit,
    onToolSelected: (String) -> Unit,
) {
    val model: SearchViewModel = viewModel(key = "local.search", factory = factory)
    val state by model.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val languageTags = LocalConfiguration.current.locales.toLanguageTags()
    LaunchedEffect(languageTags) { model.refresh() }
    LaunchedEffect(favoriteState.codes) { model.updateFavorites(favoriteState.codes) }

    SearchScreen(
        state = state,
        favoriteState = favoriteState,
        onSetFavorite = onSetFavorite,
        onQueryChanged = model::updateQuery,
        onCategorySelected = model::selectCategory,
        onClearFilters = model::clearFilters,
        onRetry = model::refresh,
        onSubmit = {
            keyboard?.hide()
            model.refresh()
        },
        onClose = {
            keyboard?.hide()
            onClose()
        },
        onToolSelected = {
            focus.clearFocus()
            keyboard?.hide()
            onToolSelected(it)
        },
    )
}
