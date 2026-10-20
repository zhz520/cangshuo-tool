package com.cangshuo.toolbox.feature.reaction.domain

import kotlin.math.sqrt

data class ReactionRecord(
    val id: String,
    val completedAt: Long,
    val samples: List<Long>,
    val early: Int,
    val missed: Int,
)

data class ReactionStatistics(val best: Long, val worst: Long, val mean: Double, val median: Double, val deviation: Double)

object ReactionPolicy {
    const val MIN_WAIT = 1_500
    const val MAX_WAIT = 4_500
    const val MIN_REACTION = 80L
    const val MAX_REACTION = 3_000L
    const val HISTORY_LIMIT = 20
    val roundOptions = listOf(3, 5, 10)

    fun statistics(samples: List<Long>): ReactionStatistics? {
        if (samples.isEmpty()) return null
        require(samples.size <= 10 && samples.all { it in MIN_REACTION..MAX_REACTION })
        val sorted = samples.sorted()
        val mean = samples.average()
        val middle = sorted.size / 2
        val median = if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle].toDouble()
        return ReactionStatistics(sorted.first(), sorted.last(), mean, median,
            sqrt(samples.sumOf { (it - mean) * (it - mean) } / samples.size))
    }

    fun valid(record: ReactionRecord): Boolean = record.id.matches(Regex("[a-zA-Z0-9-]{1,64}")) &&
        record.completedAt > 0 && record.samples.size in roundOptions &&
        record.samples.all { it in MIN_REACTION..MAX_REACTION } && record.early >= 0 && record.missed >= 0

    fun csv(samples: List<Long>): String = "round,reaction_ms\n" +
        samples.mapIndexed { index, value -> "${index + 1},$value" }.joinToString("\n")
}

interface ReactionRepository {
    fun nowMs(): Long
    fun wallMs(): Long
    fun waitMs(): Long
    suspend fun history(): List<ReactionRecord>
    suspend fun save(record: ReactionRecord): List<ReactionRecord>
    suspend fun clear(): List<ReactionRecord>
}

class ReactionUseCases(private val repository: ReactionRepository) {
    fun now() = repository.nowMs()
    fun wall() = repository.wallMs()
    fun waitMs() = repository.waitMs().also { require(it in ReactionPolicy.MIN_WAIT.toLong()..ReactionPolicy.MAX_WAIT.toLong()) }
    suspend fun history() = repository.history()
    suspend fun save(record: ReactionRecord) = repository.save(record)
    suspend fun clear() = repository.clear()
}
