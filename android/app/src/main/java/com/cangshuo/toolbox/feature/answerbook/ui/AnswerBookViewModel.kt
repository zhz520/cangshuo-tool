package com.cangshuo.toolbox.feature.answerbook.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.answerbook.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BookError { LOAD, WRITE }
data class BookUiState(val book: AnswerBookState = AnswerBookState(), val theme: AnswerTheme = AnswerTheme.ALL,
    val current: Int? = null, val busy: Boolean = false, val loaded: Boolean = false, val error: BookError? = null, val favoritesOnly: Boolean = false)

class AnswerBookViewModel(private val cases: AnswerBookUseCases, private val saved: SavedStateHandle) : ViewModel() {
    private val mutable = MutableStateFlow(BookUiState(
        theme = saved.get<String>("bookTheme")?.let { runCatching { AnswerTheme.valueOf(it) }.getOrNull() } ?: AnswerTheme.ALL,
        current = saved.get<Int>("bookCurrent")?.takeIf { it in AnswerBookPolicy.entries.indices },
        favoritesOnly = saved.get<Boolean>("bookFavorites") ?: false))
    val state = mutable.asStateFlow()
    init { load() }
    fun theme(theme: AnswerTheme) { if (!mutable.value.busy) { saved["bookTheme"] = theme.name; mutable.value = mutable.value.copy(theme = theme) } }
    fun tab(favorites: Boolean) { saved["bookFavorites"] = favorites; mutable.value = mutable.value.copy(favoritesOnly = favorites) }
    fun select(id: Int) { if (id in AnswerBookPolicy.entries.indices) { saved["bookCurrent"] = id; mutable.value = mutable.value.copy(current = id) } }
    fun load() = action(BookError.LOAD, selectLast = false) { cases.load() }
    fun draw() {
        if (!mutable.value.loaded || mutable.value.error != null) return
        val theme = mutable.value.theme
        action(BookError.WRITE, selectLast = true) { cases.draw(theme) }
    }
    fun favorite(id: Int) { if (mutable.value.loaded) action(BookError.WRITE) { cases.favorite(id) } }
    fun clearHistory() { if (mutable.value.loaded) action(BookError.WRITE) { cases.clearHistory() } }
    fun reset() = action(BookError.WRITE, resetCurrent = true) { cases.reset() }
    private fun action(error: BookError, selectLast: Boolean = false, resetCurrent: Boolean = false, work: suspend () -> AnswerBookState) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val book = work()
                val current = if (resetCurrent) null else if (selectLast) book.last else mutable.value.current ?: book.last
                saved["bookCurrent"] = current
                mutable.value = mutable.value.copy(book = book, current = current, busy = false, loaded = true)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, error = error) }
        }
    }
    companion object {
        fun factory(cases: AnswerBookUseCases): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = AnswerBookViewModel(cases, extras.createSavedStateHandle()) as T
        }
    }
}
