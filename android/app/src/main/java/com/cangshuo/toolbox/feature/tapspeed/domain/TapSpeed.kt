package com.cangshuo.toolbox.feature.tapspeed.domain

enum class TapStage { IDLE, ARMED, RUNNING, COMPLETE, INTERRUPTED, LIMIT }

data class TapSession(
    val stage: TapStage = TapStage.IDLE,
    val seconds: Int = 10,
    val multiTouch: Boolean = false,
    val startedAt: Long = 0,
    val elapsedMs: Long = 0,
    val bins: List<Int> = List(seconds) { 0 },
    val sources: Int = 0,
    val lastInputAt: Long = 0,
) {
    val taps get() = bins.sum()
    val average get() = taps * 1000.0 / (if (stage == TapStage.COMPLETE) seconds * 1000L else elapsedMs.coerceAtLeast(1000))
    val peak get() = bins.maxOrNull() ?: 0
    val remainingMs get() = (seconds * 1000L - elapsedMs).coerceAtLeast(0)
}

data class TapRecord(val id: String, val at: Long, val seconds: Int, val multiTouch: Boolean, val bins: List<Int>, val sources: Int) {
    val taps get() = bins.sum()
    val cps get() = taps.toDouble() / seconds
}

object TapPolicy {
    val durations = listOf(1, 5, 10, 30, 60)
    const val MAX_TAPS = 10_000
    const val HISTORY_LIMIT = 20
    const val TOUCH = 1
    const val KEYBOARD = 2
    const val ACCESSIBILITY = 4

    fun validBins(seconds: Int, bins: List<Int>) = seconds in durations && bins.size == seconds &&
        bins.all { it in 0..MAX_TAPS } && bins.sumOf { it.toLong() } <= MAX_TAPS

    fun valid(record: TapRecord) = record.id.matches(Regex("[A-Za-z0-9-]{1,64}")) && record.at > 0 &&
        validBins(record.seconds, record.bins) && record.taps > 0 && record.sources in 1..7

    fun advance(session: TapSession, now: Long): TapSession {
        if (session.stage != TapStage.RUNNING) return session
        val elapsed = (now - session.startedAt).coerceIn(session.elapsedMs, session.seconds * 1000L)
        return session.copy(elapsedMs = elapsed, stage = if (elapsed == session.seconds * 1000L) TapStage.COMPLETE else TapStage.RUNNING)
    }

    fun hit(session: TapSession, eventTime: Long, now: Long, source: Int): TapSession {
        if (source !in listOf(TOUCH, KEYBOARD, ACCESSIBILITY) || eventTime > now || eventTime < 0) return session
        val current = advance(session, now)
        if (current.stage !in setOf(TapStage.ARMED, TapStage.RUNNING)) return current
        if (current.stage == TapStage.RUNNING && eventTime < current.lastInputAt) return current
        val start = if (current.stage == TapStage.ARMED) eventTime else current.startedAt
        val offset = eventTime - start
        if (offset !in 0 until current.seconds * 1000L) return current
        val index = (offset / 1000).toInt()
        val bins = current.bins.toMutableList().apply { this[index]++ }
        val hit = current.copy(stage = TapStage.RUNNING, startedAt = start, elapsedMs = (now - start).coerceAtMost(current.seconds * 1000L),
            bins = bins, sources = current.sources or source, lastInputAt = eventTime)
        return if (hit.taps >= MAX_TAPS) hit.copy(stage = TapStage.LIMIT) else hit
    }

    fun downCount(multi: Boolean, alreadyPressed: Boolean, freshDowns: Int): Int =
        if (multi) freshDowns.coerceAtLeast(0) else if (alreadyPressed) 0 else freshDowns.coerceIn(0, 1)

    fun visibleBins(s: TapSession) = s.bins.take(if (s.stage == TapStage.COMPLETE) s.seconds else (s.elapsedMs / 1000 + 1).toInt().coerceAtMost(s.seconds))
    fun csv(s: TapSession) = "second,taps,configured_seconds,elapsed_ms,multitouch,input_sources,complete\n" +
        visibleBins(s).mapIndexed { index, count -> "${index + 1},$count,${s.seconds},${s.elapsedMs},${s.multiTouch},${s.sources},${s.stage == TapStage.COMPLETE}" }.joinToString("\n")
}

interface TapRepository {
    fun now(): Long
    fun wall(): Long
    suspend fun history(): List<TapRecord>
    suspend fun save(record: TapRecord): List<TapRecord>
    suspend fun clear(): List<TapRecord>
}

class TapUseCases(private val repository: TapRepository) {
    fun now() = repository.now()
    fun wall() = repository.wall()
    suspend fun history() = repository.history()
    suspend fun save(record: TapRecord) = repository.save(record)
    suspend fun clear() = repository.clear()
}
