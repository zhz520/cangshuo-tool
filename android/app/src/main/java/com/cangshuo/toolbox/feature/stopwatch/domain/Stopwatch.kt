package com.cangshuo.toolbox.feature.stopwatch.domain

import java.util.Locale

data class StopwatchLap(val number: Int, val splitMs: Long, val totalMs: Long)

data class StopwatchSession(
    val running: Boolean = false,
    val accumulatedMs: Long = 0,
    val startedAtMs: Long? = null,
    val laps: List<StopwatchLap> = emptyList(),
) {
    /** Elapsed time from the monotonic clock; a stale segment cannot make it go backwards. */
    fun elapsedMs(nowMs: Long): Long {
        val started = startedAtMs
        return if (running && started != null) accumulatedMs + (nowMs - started).coerceAtLeast(0)
        else accumulatedMs
    }
}

interface StopwatchRepository { fun nowMs(): Long }

class StopwatchUseCases(private val repository: StopwatchRepository) {
    fun now(): Long = repository.nowMs()
}

object StopwatchPolicy {
    const val MAX_LAPS = 500
    const val DISPLAY_LIMIT = 20
    const val TICK_MS = 33L

    /** 99:59:59.99 in milliseconds; the display clamps here instead of overflowing the layout. */
    const val MAX_DISPLAY_MS = 359_999_990L

    fun start(session: StopwatchSession, nowMs: Long): StopwatchSession =
        if (session.running) session else session.copy(running = true, startedAtMs = nowMs)

    fun pause(session: StopwatchSession, nowMs: Long): StopwatchSession = if (!session.running) session
    else session.copy(running = false, accumulatedMs = session.elapsedMs(nowMs), startedAtMs = null)

    /** Returns null when the lap is not allowed; callers decide between NOT_RUNNING and LAP_LIMIT. */
    fun lap(session: StopwatchSession, nowMs: Long): StopwatchSession? {
        if (!session.running || session.laps.size >= MAX_LAPS) return null
        val total = session.elapsedMs(nowMs)
        val previous = session.laps.lastOrNull()?.totalMs ?: 0L
        if (total <= previous) return null
        return session.copy(laps = session.laps + StopwatchLap(session.laps.size + 1, total - previous, total))
    }

    fun fastestLap(laps: List<StopwatchLap>): StopwatchLap? =
        laps.takeIf { it.size >= 2 }?.minByOrNull { it.splitMs }

    fun slowestLap(laps: List<StopwatchLap>): StopwatchLap? =
        laps.takeIf { it.size >= 2 }?.maxByOrNull { it.splitMs }

    fun averageSplitMs(laps: List<StopwatchLap>): Long? =
        laps.takeIf { it.isNotEmpty() }?.let { list -> list.sumOf(StopwatchLap::splitMs) / list.size }

    /** Monospace-friendly stopwatch format: MM:SS.CC, or H:MM:SS.CC after an hour. */
    fun format(ms: Long): String {
        val clamped = ms.coerceIn(0L, MAX_DISPLAY_MS)
        val centis = clamped % 1_000 / 10
        val seconds = clamped / 1_000 % 60
        val minutes = clamped / 60_000 % 60
        val hours = clamped / 3_600_000
        return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d.%02d", hours, minutes, seconds, centis)
        else String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)
    }

    fun csv(laps: List<StopwatchLap>): String {
        require(laps.size <= MAX_LAPS)
        return buildString {
            append("lap,split_ms,total_ms\r\n")
            laps.forEach { append("${it.number},${it.splitMs},${it.totalMs}\r\n") }
        }
    }

    /** One line per lap for SavedStateHandle; numbers stay ASCII and bounded. */
    fun encode(laps: List<StopwatchLap>): ArrayList<String> =
        ArrayList(laps.take(MAX_LAPS).map { "${it.number},${it.splitMs},${it.totalMs}" })

    fun decode(values: List<String>): List<StopwatchLap> {
        val result = ArrayList<StopwatchLap>()
        var previousTotal = 0L
        values.take(MAX_LAPS).forEach { raw ->
            val parts = raw.split(',')
            if (parts.size != 3) return@forEach
            val number = parts[0].toIntOrNull()
            val split = parts[1].toLongOrNull()
            val total = parts[2].toLongOrNull()
            if (number == null || split == null || total == null) return@forEach
            if (number != result.size + 1 || split <= 0 || total <= previousTotal) return@forEach
            if (split != total - previousTotal) return@forEach
            result.add(StopwatchLap(number, split, total))
            previousTotal = total
        }
        return result
    }

    /** A startedAt beyond the current monotonic clock means the device rebooted mid-session. */
    fun restore(running: Boolean, accumulatedMs: Long, startedAtMs: Long?, laps: List<StopwatchLap>, nowMs: Long): StopwatchSession {
        val lastLap = laps.lastOrNull()?.totalMs ?: 0L
        if (!running) return StopwatchSession(false, accumulatedMs.coerceAtLeast(lastLap), null, laps)
        val started = startedAtMs?.takeIf { it in 1..nowMs } ?: nowMs
        val accumulated = accumulatedMs.coerceAtLeast((lastLap - (nowMs - started)).coerceAtLeast(0))
        return StopwatchSession(true, accumulated, started, laps)
    }
}
