package com.cangshuo.toolbox.feature.recent.domain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
class ObserveRecentUseCase(private val repository: RecentRepository) {
    operator fun invoke(): Flow<List<RecentItem>> = repository.observeRecent().map { recent ->
        recent.map { RecentItem(it.code, repository.getTool(it.code), it.lastUsedAt, it.useCount) }
    }
}
