package com.cangshuo.toolbox.feature.recent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.recent.domain.ClearRecentUseCase
import com.cangshuo.toolbox.feature.recent.domain.ObserveRecentUseCase
import com.cangshuo.toolbox.feature.recent.domain.RecordToolUseUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecentViewModel(
    private val observeRecent: ObserveRecentUseCase,
    private val recordToolUse: RecordToolUseUseCase,
    private val clearRecent: ClearRecentUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RecentUiState())
    val uiState = mutableState.asStateFlow()
    private var observation: Job? = null

    init { refresh() }

    fun refresh() {
        observation?.cancel()
        mutableState.update { it.copy(list = RecentListState.Loading) }
        observation = viewModelScope.launch {
            try {
                observeRecent().collect { items ->
                    mutableState.update {
                        it.copy(
                            list = if (items.isEmpty()) RecentListState.Empty else RecentListState.Content(items),
                            codes = items.map { item -> item.code }.toSet(),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(list = RecentListState.Error) }
            }
        }
    }

    /** Fire-and-forget: a failed history write must not interrupt opening a tool. */
    fun recordUse(code: String) {
        viewModelScope.launch {
            try {
                recordToolUse(code)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
    }

    fun clear() {
        viewModelScope.launch {
            try {
                clearRecent()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(message = RecentMessage.CLEAR_FAILED) }
            }
        }
    }

    fun dismissMessage() { mutableState.update { it.copy(message = null) } }

    companion object {
        fun factory(
            observe: ObserveRecentUseCase,
            record: RecordToolUseUseCase,
            clear: ClearRecentUseCase,
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { RecentViewModel(observe, record, clear) } }
    }
}
