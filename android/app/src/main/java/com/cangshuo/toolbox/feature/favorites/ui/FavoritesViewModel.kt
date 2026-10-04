package com.cangshuo.toolbox.feature.favorites.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.favorites.domain.ObserveFavoritesUseCase
import com.cangshuo.toolbox.feature.favorites.domain.SetFavoriteUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FavoritesViewModel(
    private val observeFavorites: ObserveFavoritesUseCase,
    private val setFavorite: SetFavoriteUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(FavoritesUiState())
    val uiState = mutableState.asStateFlow()
    private var observation: Job? = null

    init { refresh() }

    fun refresh() {
        observation?.cancel()
        mutableState.update {
            it.copy(list = FavoritesListState.Loading, message = if (it.message == FavoriteMessage.LOAD_FAILED) null else it.message)
        }
        observation = viewModelScope.launch {
            try {
                observeFavorites().collect { items ->
                    mutableState.update {
                        it.copy(
                            list = if (items.isEmpty()) FavoritesListState.Empty else FavoritesListState.Content(items),
                            codes = items.map { item -> item.code }.toSet(),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(list = FavoritesListState.Error, message = FavoriteMessage.LOAD_FAILED) }
            }
        }
    }

    fun changeFavorite(code: String, selected: Boolean) {
        val state = mutableState.value
        if (!state.ready || code in state.pendingCodes) return
        mutableState.update { it.copy(pendingCodes = it.pendingCodes + code) }
        viewModelScope.launch {
            try {
                setFavorite(code, selected)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(message = FavoriteMessage.SAVE_FAILED) }
            } finally {
                mutableState.update { it.copy(pendingCodes = it.pendingCodes - code) }
            }
        }
    }

    fun dismissMessage() { mutableState.update { it.copy(message = null) } }

    companion object {
        fun factory(observe: ObserveFavoritesUseCase, setFavorite: SetFavoriteUseCase): ViewModelProvider.Factory =
            viewModelFactory { initializer { FavoritesViewModel(observe, setFavorite) } }
    }
}
