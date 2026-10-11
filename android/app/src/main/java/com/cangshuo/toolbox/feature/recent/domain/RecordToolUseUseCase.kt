package com.cangshuo.toolbox.feature.recent.domain
private val recentCodePattern = Regex("[a-z][a-z0-9_]{0,63}")
class RecordToolUseUseCase(
    private val repository: RecentRepository,
    private val remoteUsage: suspend (String) -> Unit = {},
) {
    suspend operator fun invoke(code: String) {
        require(recentCodePattern.matches(code)) { "Invalid recent code" }
        repository.recordUsage(code, System.currentTimeMillis())
        // Anonymous aggregate counting; never lets a network problem affect the local record.
        try { remoteUsage(code) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { }
    }
}
