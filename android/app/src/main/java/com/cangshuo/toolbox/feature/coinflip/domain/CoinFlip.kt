package com.cangshuo.toolbox.feature.coinflip.domain

enum class CoinSide { HEADS, TAILS }
data class CoinLabels(val heads: String, val tails: String) {
    fun forSide(side: CoinSide): String = if (side == CoinSide.HEADS) heads else tails
}
data class CoinBatch(val sides: List<CoinSide>, val labels: CoinLabels) {
    val heads get() = sides.count { it == CoinSide.HEADS }
    val tails get() = sides.size - heads
    val majority: CoinSide? get() = when {
        heads > tails -> CoinSide.HEADS
        tails > heads -> CoinSide.TAILS
        else -> null
    }
}
data class CoinRecord(val number: Long, val side: CoinSide, val labels: CoinLabels)

/** Counts cover the whole session; only the most recent records occupy memory. */
data class CoinSession(
    val heads: Long = 0,
    val tails: Long = 0,
    val lastSide: CoinSide? = null,
    val streak: Int = 0,
    val longestStreak: Int = 0,
    val records: List<CoinRecord> = emptyList(),
) {
    val total get() = heads + tails
    fun add(batch: CoinBatch): CoinSession {
        require(batch.sides.size in 1..CoinPolicy.MAX_BATCH)
        require(total in 0..CoinPolicy.MAX_SESSION && batch.sides.size <= CoinPolicy.MAX_SESSION - total)
        require(CoinPolicy.labels(batch.labels.heads, batch.labels.tails) == batch.labels)
        var previous = lastSide
        var current = streak
        var longest = longestStreak
        batch.sides.forEach { side ->
            current = if (side == previous) current + 1 else 1
            previous = side
            longest = maxOf(longest, current)
        }
        val added = batch.sides.mapIndexed { i, side -> CoinRecord(total + i + 1, side, batch.labels) }
        return CoinSession(heads + batch.heads, tails + batch.tails, previous, current, longest,
            (records + added).takeLast(CoinPolicy.HISTORY_LIMIT))
    }
}

interface CoinRepository { suspend fun flip(count: Int): List<CoinSide> }
fun interface CoinRandomSource { fun nextHeads(): Boolean }

class FlipCoinsUseCase(private val repository: CoinRepository) {
    suspend operator fun invoke(count: Int): List<CoinSide> {
        require(count in 1..CoinPolicy.MAX_BATCH)
        return repository.flip(count).also { check(it.size == count) }
    }
}

object CoinPolicy {
    const val MAX_BATCH = 100
    const val HISTORY_LIMIT = 100
    const val MAX_SESSION = 1_000_000L
    const val LABEL_CODE_POINTS = 20
    const val REQUEST_TIMEOUT_MS = 3_000L

    fun count(text: String): Int? = text.takeIf { it.length in 1..3 && it.all { c -> c in '0'..'9' } }
        ?.toIntOrNull()?.takeIf { it in 1..MAX_BATCH }

    fun labels(heads: String, tails: String): CoinLabels? {
        val a = heads.trim()
        val b = tails.trim()
        if (!validLabel(a) || !validLabel(b) || a.equals(b, ignoreCase = true)) return null
        return CoinLabels(a, b)
    }

    fun validLabel(text: String): Boolean = text.isNotBlank() && text.length <= LABEL_CODE_POINTS * 2 &&
        text.codePointCount(0, text.length) <= LABEL_CODE_POINTS && text.none {
            Character.isISOControl(it) || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' || it == '\u200E' || it == '\u200F'
        } && validSurrogates(text)

    private fun validSurrogates(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val c = text[i++]
            if (Character.isHighSurrogate(c)) {
                if (i >= text.length || !Character.isLowSurrogate(text[i++])) return false
            } else if (Character.isLowSurrogate(c)) return false
        }
        return true
    }

    fun csv(records: List<CoinRecord>): String {
        require(records.size <= HISTORY_LIMIT)
        fun cell(value: String): String {
            // User labels are quoted and protected when opened in spreadsheet applications.
            val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        return buildString {
            append("number,side,result,heads_label,tails_label\r\n")
            records.forEach { row ->
                append("${row.number},${row.side.name},${cell(row.labels.forSide(row.side))},${cell(row.labels.heads)},${cell(row.labels.tails)}\r\n")
            }
        }
    }
}
