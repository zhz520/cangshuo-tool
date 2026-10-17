package com.cangshuo.toolbox.feature.scoreboard.domain

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

enum class ScoreSide { LEFT, RIGHT }

enum class ScoreActionKind { SCORE, SET_SCORE, SWAP, NEXT_PERIOD, EDIT_NAMES }

data class ScoreAction(
    val kind: ScoreActionKind,
    val side: ScoreSide? = null,
    val delta: Int = 0,
    val targetScore: Int? = null,
)

data class ScoreboardCore(
    val leftName: String = "",
    val rightName: String = "",
    val leftScore: Int = 0,
    val rightScore: Int = 0,
    val period: Int = 1,
) {
    fun score(side: ScoreSide): Int = if (side == ScoreSide.LEFT) leftScore else rightScore

    fun withScore(side: ScoreSide, value: Int): ScoreboardCore =
        if (side == ScoreSide.LEFT) copy(leftScore = value) else copy(rightScore = value)
}

data class ScoreboardJournalEntry(
    val before: ScoreboardCore,
    val after: ScoreboardCore,
    val action: ScoreAction,
    val atEpochMs: Long,
)

data class ScoreboardSession(
    val core: ScoreboardCore = ScoreboardCore(),
    val step: Int = 1,
    val running: Boolean = false,
    val accumulatedMs: Long = 0L,
    val startedAtMs: Long? = null,
    val journal: List<ScoreboardJournalEntry> = emptyList(),
) {
    fun elapsedMs(nowMonotonicMs: Long): Long {
        val started = startedAtMs
        val live = if (running && started != null) {
            accumulatedMs + (nowMonotonicMs - started).coerceAtLeast(0L)
        } else accumulatedMs
        return live.coerceIn(0L, ScoreboardPolicy.MAX_TIMER_MS)
    }
}

data class ScoreboardClock(val monotonicMs: Long, val epochMs: Long)

interface ScoreboardRepository {
    fun clock(): ScoreboardClock
}

class ScoreboardUseCases(private val repository: ScoreboardRepository) {
    fun clock(): ScoreboardClock = repository.clock()

    fun addPoints(session: ScoreboardSession, side: ScoreSide, delta: Int, nowEpochMs: Long) =
        ScoreboardPolicy.addPoints(session, side, delta, nowEpochMs)

    fun setScore(session: ScoreboardSession, side: ScoreSide, score: Int, nowEpochMs: Long) =
        ScoreboardPolicy.setScore(session, side, score, nowEpochMs)

    fun swap(session: ScoreboardSession, nowEpochMs: Long): ScoreboardSession =
        ScoreboardPolicy.swap(session, nowEpochMs)

    fun nextPeriod(session: ScoreboardSession, nowEpochMs: Long): ScoreboardSession =
        ScoreboardPolicy.nextPeriod(session, nowEpochMs)

    fun editNames(
        session: ScoreboardSession,
        leftName: String,
        rightName: String,
        nowEpochMs: Long,
    ): ScoreboardSession = ScoreboardPolicy.editNames(session, leftName, rightName, nowEpochMs)

    fun undo(session: ScoreboardSession): ScoreboardSession = ScoreboardPolicy.undo(session)

    fun reset(session: ScoreboardSession): ScoreboardSession = ScoreboardPolicy.reset(session)
}

object ScoreboardPolicy {
    const val MAX_SCORE = 999
    const val MAX_PERIOD = 99
    const val MAX_NAME_CODE_POINTS = 24
    const val MAX_HISTORY = 50
    const val DISPLAY_HISTORY = 20
    const val MIN_STEP = 1
    const val MAX_STEP = 3
    const val TICK_MS = 250L
    const val MAX_TIMER_MS = 359_999_000L // 99:59:59
    const val CLOCK_TOLERANCE_MS = 2_000L
    const val MAX_EVENT_EPOCH_MS = 253_402_300_799_999L

    fun nameInput(raw: String): String {
        val cleaned = raw.filterNot(::unsafeNameCharacter)
        val count = cleaned.codePointCount(0, cleaned.length)
        if (count <= MAX_NAME_CODE_POINTS) return cleaned
        return cleaned.substring(0, cleaned.offsetByCodePoints(0, MAX_NAME_CODE_POINTS))
    }

    private fun unsafeNameCharacter(character: Char): Boolean =
        Character.isISOControl(character) ||
            (Character.getType(character) == Character.FORMAT.toInt() &&
                character != '\u200C' && character != '\u200D')

    fun boundedName(raw: String): String = nameInput(raw.trim())

    fun isUsableName(raw: String): Boolean = nameInput(raw).any {
        !it.isWhitespace() && it != '\u200C' && it != '\u200D'
    }

    fun editedName(raw: String, fallback: String): String {
        val cleaned = boundedName(raw)
        return if (isUsableName(cleaned)) cleaned else boundedName(fallback)
    }

    fun addPoints(session: ScoreboardSession, side: ScoreSide, delta: Int, nowEpochMs: Long): ScoreboardSession {
        if (delta !in -MAX_SCORE..MAX_SCORE) return session
        val before = session.core
        val score = (before.score(side) + delta).coerceIn(0, MAX_SCORE)
        if (score == before.score(side)) return session
        val actualDelta = score - before.score(side)
        val after = before.withScore(side, score)
        return append(
            session,
            before,
            after,
            ScoreAction(ScoreActionKind.SCORE, side = side, delta = actualDelta),
            nowEpochMs,
        )
    }

    fun setScore(session: ScoreboardSession, side: ScoreSide, score: Int, nowEpochMs: Long): ScoreboardSession {
        if (score !in 0..MAX_SCORE) return session
        val before = session.core
        if (before.score(side) == score) return session
        val after = before.withScore(side, score)
        return append(session, before, after, ScoreAction(ScoreActionKind.SET_SCORE, side = side, targetScore = score), nowEpochMs)
    }

    fun swap(session: ScoreboardSession, nowEpochMs: Long): ScoreboardSession {
        val before = session.core
        if (before.leftScore == 0 && before.rightScore == 0 && before.leftName.isBlank() && before.rightName.isBlank()) {
            return session
        }
        val after = before.copy(
            leftName = before.rightName,
            rightName = before.leftName,
            leftScore = before.rightScore,
            rightScore = before.leftScore,
        )
        return append(session, before, after, ScoreAction(ScoreActionKind.SWAP), nowEpochMs)
    }

    fun nextPeriod(session: ScoreboardSession, nowEpochMs: Long): ScoreboardSession {
        val before = session.core
        if (before.period >= MAX_PERIOD) return session
        val after = before.copy(leftScore = 0, rightScore = 0, period = before.period + 1)
        return append(session, before, after, ScoreAction(ScoreActionKind.NEXT_PERIOD), nowEpochMs)
    }

    fun editNames(
        session: ScoreboardSession,
        leftName: String,
        rightName: String,
        nowEpochMs: Long,
    ): ScoreboardSession {
        val before = session.core
        val left = editedName(leftName, before.leftName)
        val right = editedName(rightName, before.rightName)
        if (left == before.leftName && right == before.rightName) return session
        val after = before.copy(leftName = left, rightName = right)
        return append(session, before, after, ScoreAction(ScoreActionKind.EDIT_NAMES), nowEpochMs)
    }

    fun undo(session: ScoreboardSession): ScoreboardSession {
        val last = session.journal.lastOrNull() ?: return session
        return session.copy(core = last.before, journal = session.journal.dropLast(1))
    }

    fun reset(session: ScoreboardSession): ScoreboardSession = session.copy(
        core = ScoreboardCore(leftName = session.core.leftName, rightName = session.core.rightName),
        running = false,
        accumulatedMs = 0L,
        startedAtMs = null,
        journal = emptyList(),
    )

    fun normalizeStep(step: Int): Int = step.coerceIn(MIN_STEP, MAX_STEP)

    fun startTimer(session: ScoreboardSession, nowMonotonicMs: Long): ScoreboardSession =
        if (session.running) session else session.copy(
            running = true,
            accumulatedMs = session.elapsedMs(nowMonotonicMs),
            startedAtMs = nowMonotonicMs,
        )

    fun pauseTimer(session: ScoreboardSession, nowMonotonicMs: Long): ScoreboardSession = if (!session.running) session
    else session.copy(
        running = false,
        accumulatedMs = session.elapsedMs(nowMonotonicMs),
        startedAtMs = null,
    )

    fun resetTimer(session: ScoreboardSession, nowMonotonicMs: Long): ScoreboardSession = session.copy(
        running = session.running,
        accumulatedMs = 0L,
        startedAtMs = if (session.running) nowMonotonicMs else null,
    )

    fun restoreTimer(
        running: Boolean,
        accumulatedMs: Long,
        startedAtMs: Long?,
        savedAtEpochMs: Long?,
        nowMonotonicMs: Long,
        nowEpochMs: Long,
    ): Long {
        val accumulated = accumulatedMs.coerceIn(0L, MAX_TIMER_MS)
        if (!running) return accumulated
        val monotonicDelta = startedAtMs?.takeIf { it in 1..nowMonotonicMs }
            ?.let { (nowMonotonicMs - it).coerceAtLeast(0L) }
        val wallDelta = savedAtEpochMs
            ?.takeIf { it in 0..MAX_EVENT_EPOCH_MS }
            ?.let { (nowEpochMs.coerceIn(0L, MAX_EVENT_EPOCH_MS) - it).coerceAtLeast(0L) }
        val segment = when {
            wallDelta == null -> monotonicDelta ?: 0L
            monotonicDelta == null -> wallDelta
            kotlin.math.abs(wallDelta - monotonicDelta) <= CLOCK_TOLERANCE_MS -> monotonicDelta
            // A device reboot can reset elapsedRealtime() while wall time keeps advancing.
            else -> wallDelta
        }
        return (accumulated + segment).coerceIn(0L, MAX_TIMER_MS)
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = ms.coerceIn(0L, MAX_TIMER_MS) / 1_000
        val hours = totalSeconds / 3_600
        val minutes = totalSeconds / 60 % 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun append(
        session: ScoreboardSession,
        before: ScoreboardCore,
        after: ScoreboardCore,
        action: ScoreAction,
        nowEpochMs: Long,
    ): ScoreboardSession = session.copy(
        core = after,
        journal = (session.journal + ScoreboardJournalEntry(before, after, action, nowEpochMs))
            .takeLast(MAX_HISTORY),
    )

    /** Compact SavedStateHandle representation; malformed entries are ignored independently. */
    fun encodeJournal(journal: List<ScoreboardJournalEntry>): ArrayList<String> = ArrayList(
        journal.takeLast(MAX_HISTORY).map { entry ->
            listOf(
                "1", entry.action.kind.name, entry.action.side?.name.orEmpty(),
                entry.action.delta.toString(), entry.action.targetScore?.toString().orEmpty(),
                entry.atEpochMs.toString(),
                encodeText(entry.before.leftName), encodeText(entry.before.rightName),
                entry.before.leftScore.toString(), entry.before.rightScore.toString(), entry.before.period.toString(),
                encodeText(entry.after.leftName), encodeText(entry.after.rightName),
                entry.after.leftScore.toString(), entry.after.rightScore.toString(), entry.after.period.toString(),
            ).joinToString(",")
        },
    )

    fun decodeJournal(values: List<String>): List<ScoreboardJournalEntry> = buildList {
        values.takeLast(MAX_HISTORY).forEach { raw ->
            val parts = raw.split(',')
            if (parts.size != 16 || parts[0] != "1") return@forEach
            val kind = runCatching { ScoreActionKind.valueOf(parts[1]) }.getOrNull() ?: return@forEach
            val side = parts[2].takeIf { it.isNotEmpty() }
                ?.let { runCatching { ScoreSide.valueOf(it) }.getOrNull() }
            if (parts[2].isNotEmpty() && side == null) return@forEach
            val delta = parts[3].toIntOrNull() ?: return@forEach
            val target = parts[4].takeIf { it.isNotEmpty() }?.toIntOrNull()
            val at = parts[5].toLongOrNull()?.takeIf { it in 0..MAX_EVENT_EPOCH_MS } ?: return@forEach
            val before = decodeCore(parts, 6) ?: return@forEach
            val after = decodeCore(parts, 11) ?: return@forEach
            if (kind == ScoreActionKind.SCORE &&
                (side == null || delta == 0 || delta !in -MAX_SCORE..MAX_SCORE)) return@forEach
            if (kind == ScoreActionKind.SET_SCORE && (side == null || target == null || target !in 0..MAX_SCORE)) {
                return@forEach
            }
            add(ScoreboardJournalEntry(before, after, ScoreAction(kind, side, delta, target), at))
        }
    }

    private fun decodeCore(parts: List<String>, start: Int): ScoreboardCore? {
        val left = decodeText(parts[start]) ?: return null
        val right = decodeText(parts[start + 1]) ?: return null
        val leftScore = parts[start + 2].toIntOrNull()?.takeIf { it in 0..MAX_SCORE } ?: return null
        val rightScore = parts[start + 3].toIntOrNull()?.takeIf { it in 0..MAX_SCORE } ?: return null
        val period = parts[start + 4].toIntOrNull()?.takeIf { it in 1..MAX_PERIOD } ?: return null
        return ScoreboardCore(boundedName(left), boundedName(right), leftScore, rightScore, period)
    }

    private fun encodeText(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    }.getOrNull()
}
