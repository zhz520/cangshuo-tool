package com.cangshuo.toolbox.feature.diagnostics.domain

enum class ExitReason(val code: Int) { JAVA_CRASH(4), NATIVE_CRASH(5), ANR(6) }
data class ExitEvent(val timestamp: Long, val reason: ExitReason)
data class Diagnostics(val events: List<ExitEvent>, val systemHistoryAvailable: Boolean)
interface DiagnosticsRepository {
    suspend fun read(): Diagnostics
    suspend fun clear()
}
class DiagnosticsUseCases(private val repository: DiagnosticsRepository) {
    suspend fun read() = repository.read()
    suspend fun clear() = repository.clear()
}

internal fun boundedEvents(raw: List<Pair<Long, Int>>, now: Long, clearedAt: Long): List<ExitEvent> {
    val earliest = (now - 7 * 86_400_000L).coerceAtLeast(0)
    val result = mutableListOf<ExitEvent>()
    raw.take(40).mapNotNull { (time, code) ->
        val reason = ExitReason.entries.firstOrNull { it.code == code } ?: return@mapNotNull null
        if (time !in earliest..now || time <= clearedAt) null else ExitEvent(time, reason)
    }.sortedByDescending { it.timestamp }.forEach { event ->
        if (result.none { it.reason == event.reason && kotlin.math.abs(it.timestamp - event.timestamp) <= 2_000 }) result += event
    }
    return result.take(10)
}

// The exception and thread are only passed through to Android's existing handler.
internal class SafeCrashHandler(private val record: () -> Unit, private val delegate: Thread.UncaughtExceptionHandler) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        try { record() } catch (_: Throwable) { /* Diagnostics must not replace crash handling. */ }
        finally { delegate.uncaughtException(thread, error) }
    }
}
