package com.cangshuo.toolbox.feature.fps.domain

import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.Flow

enum class FpsStage { IDLE, WAITING, RUNNING, COMPLETE, STOPPED, INTERRUPTED, NO_FRAMES, FAILED, LIMIT }
data class FpsRequest(val seconds: Int = 10, val targetHz: Int = 0, val objects: Int = 24)
data class FpsWindow(val nanos: Long, val count: Int) {
    val fps get() = count * 1_000_000_000.0 / nanos
}
data class FpsRecord(
    val id: String, val at: Long, val request: FpsRequest, val budgetHz: Double,
    val elapsedNanos: Long, val frames: Int, val p95Nanos: Long, val p99Nanos: Long, val worstNanos: Long,
    val longFrames: Int, val estimatedMissed: Long, val ignored: Int,
    val minRefresh: Double, val maxRefresh: Double, val windows: List<FpsWindow>, val complete: Boolean,
) {
    val fps get() = frames * 1_000_000_000.0 / elapsedNanos
    val averageMs get() = elapsedNanos / 1_000_000.0 / frames
    val longPercent get() = longFrames * 100.0 / frames
}
data class FpsSnapshot(val stage: FpsStage, val record: FpsRecord? = null)

object FpsPolicy {
    val durations = listOf(10, 30, 60)
    val budgets = listOf(0, 30, 60, 90, 120, 144, 240)
    val loads = listOf(8, 24, 64)
    const val MAX_SAMPLES = 30_000
    const val MAX_GAP = 10_000_000_000L
    const val HISTORY_LIMIT = 10
    const val STORAGE_LIMIT = 131_072
    fun valid(request: FpsRequest) = request.seconds in durations && request.targetHz in budgets && request.objects in loads
    fun refresh(value: Double) = value.isFinite() && value in 20.0..360.0
    fun valid(r: FpsRecord): Boolean = r.id.matches(Regex("[A-Za-z0-9-]{1,64}")) && r.at > 0 && valid(r.request) &&
        refresh(r.budgetHz) && refresh(r.minRefresh) && refresh(r.maxRefresh) && r.minRefresh <= r.maxRefresh &&
        r.frames in 1..MAX_SAMPLES && r.elapsedNanos in 1..(r.request.seconds * 1_000_000_000L + MAX_GAP) &&
        r.p95Nanos in 1..r.p99Nanos && r.p99Nanos <= r.worstNanos && r.worstNanos <= minOf(MAX_GAP, r.elapsedNanos) &&
        r.longFrames in 0..r.frames && r.estimatedMissed in 0..30_000 && r.ignored in 0..Int.MAX_VALUE &&
        r.windows.size in 1..80 && r.windows.all { it.nanos in 1..(MAX_GAP + 1_000_000_000L) && it.count in 1..MAX_SAMPLES } &&
        r.windows.sumOf { it.nanos } == r.elapsedNanos && r.windows.sumOf { it.count.toLong() } == r.frames.toLong() &&
        (!r.complete || r.elapsedNanos >= r.request.seconds * 1_000_000_000L)

    fun csv(r: FpsRecord) = "window,elapsed_ms,callback_fps,intervals,window_ms,budget_hz,complete\n" + buildString {
        var elapsed = 0L
        r.windows.forEachIndexed { index, window ->
            elapsed += window.nanos
            append("${index + 1},${elapsed / 1_000_000.0},${window.fps},${window.count},${window.nanos / 1_000_000.0},${r.budgetHz},${r.complete}\n")
        }
    }
}

/** Nanosecond intervals are retained in a bounded primitive array; the first callback is only an anchor. */
class FpsAccumulator(private val request: FpsRequest, private val budgetHz: Double) {
    init { require(FpsPolicy.valid(request) && FpsPolicy.refresh(budgetHz)) }
    private val intervals = LongArray(FpsPolicy.MAX_SAMPLES)
    private var previous = -1L
    private var count = 0
    private var elapsed = 0L
    private var windowNanos = 0L
    private var windowCount = 0
    private var ignored = 0
    private var longFrames = 0
    private var missed = 0L
    private var minRefresh = Double.POSITIVE_INFINITY
    private var maxRefresh = Double.NEGATIVE_INFINITY
    private val windows = mutableListOf<FpsWindow>()
    var stage = FpsStage.WAITING
        private set

    fun frame(nanos: Long, refreshHz: Double) {
        if (stage != FpsStage.WAITING && stage != FpsStage.RUNNING) return
        if (FpsPolicy.refresh(refreshHz)) { minRefresh = minOf(minRefresh, refreshHz); maxRefresh = maxOf(maxRefresh, refreshHz) }
        if (nanos < 0 || (previous >= 0 && nanos <= previous)) { ignored = (ignored.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(); return }
        if (previous < 0) { previous = nanos; stage = FpsStage.RUNNING; return }
        val delta = nanos - previous
        if (delta > FpsPolicy.MAX_GAP) { stage = FpsStage.INTERRUPTED; return }
        if (count == intervals.size) { stage = FpsStage.LIMIT; return }
        previous = nanos
        intervals[count++] = delta
        elapsed += delta; windowNanos += delta; windowCount++
        val budget = 1_000_000_000.0 / budgetHz
        if (delta > budget * 1.5) longFrames++
        missed += (delta / budget).roundToLong().minus(1).coerceAtLeast(0)
        if (windowNanos >= 1_000_000_000L) {
            windows += FpsWindow(windowNanos, windowCount); windowNanos = 0; windowCount = 0
        }
        if (elapsed >= request.seconds * 1_000_000_000L) stage = FpsStage.COMPLETE
    }

    fun snapshot(id: String, at: Long, stopped: Boolean = false): FpsRecord? {
        if (count == 0) return null
        val sorted = intervals.copyOf(count).apply { sort() }
        fun percentile(p: Double) = sorted[(ceil(count * p).toInt() - 1).coerceIn(0, count - 1)]
        val all = windows.toList() + if (windowCount > 0) listOf(FpsWindow(windowNanos, windowCount)) else emptyList()
        return FpsRecord(id, at, request, budgetHz, elapsed, count, percentile(.95), percentile(.99), sorted.last(),
            longFrames, missed, ignored, minRefresh.takeIf(FpsPolicy::refresh) ?: budgetHz,
            maxRefresh.takeIf(FpsPolicy::refresh) ?: budgetHz, all, stage == FpsStage.COMPLETE && !stopped)
    }
}

interface FpsRepository {
    fun observe(request: FpsRequest, displayId: Int): Flow<FpsSnapshot>
    suspend fun history(): List<FpsRecord>
    suspend fun save(record: FpsRecord): List<FpsRecord>
    suspend fun clear(): List<FpsRecord>
}
class FpsUseCases(private val repository: FpsRepository) {
    fun observe(request: FpsRequest, displayId: Int) = repository.observe(request, displayId)
    suspend fun history() = repository.history()
    suspend fun save(record: FpsRecord) = repository.save(record)
    suspend fun clear() = repository.clear()
}
