package com.cangshuo.toolbox.feature.recent.ui

import com.cangshuo.toolbox.feature.recent.domain.RecentItem

sealed interface RecentListState {
    data object Loading : RecentListState
    data object Empty : RecentListState
    data class Content(val items: List<RecentItem>) : RecentListState
    data object Error : RecentListState
}

enum class RecentMessage { CLEAR_FAILED }

data class RecentUiState(
    val list: RecentListState = RecentListState.Loading,
    val codes: Set<String> = emptySet(),
    val message: RecentMessage? = null,
) {
    val ready: Boolean get() = list is RecentListState.Content || list == RecentListState.Empty
}
