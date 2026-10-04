package com.cangshuo.toolbox.feature.recent.domain
private val recentCodePattern = Regex("[a-z][a-z0-9_]{0,63}")
class RecordToolUseUseCase(private val repository: RecentRepository) {
    suspend operator fun invoke(code: String) {
        require(recentCodePattern.matches(code)) { "Invalid recent code" }
        repository.recordUsage(code, System.currentTimeMillis())
    }
}
