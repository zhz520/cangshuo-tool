package com.cangshuo.toolbox.feature.recent.domain

class ClearRecentUseCase(private val repository: RecentRepository) {
    suspend operator fun invoke() { repository.clear() }
}
