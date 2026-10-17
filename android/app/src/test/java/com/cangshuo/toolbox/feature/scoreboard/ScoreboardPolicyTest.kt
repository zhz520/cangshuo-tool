package com.cangshuo.toolbox.feature.scoreboard

import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreActionKind
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreboardCore
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreboardPolicy
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreboardSession
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreboardPolicyTest {
    @Test fun pointsChangeOnlyTheSelectedSideAndUndoRestoresTheExactPreviousCore() {
        val first = ScoreboardPolicy.addPoints(ScoreboardSession(), ScoreSide.LEFT, 2, 100L)
        val second = ScoreboardPolicy.addPoints(first, ScoreSide.RIGHT, 3, 200L)
        assertEquals(2, second.core.leftScore)
        assertEquals(3, second.core.rightScore)
        assertEquals(2, second.journal.size)
        assertEquals(ScoreActionKind.SCORE, second.journal.last().action.kind)
        val undone = ScoreboardPolicy.undo(second)
        assertEquals(2, undone.core.leftScore)
        assertEquals(0, undone.core.rightScore)
        assertEquals(1, undone.journal.size)
        assertEquals(ScoreboardCore(), ScoreboardPolicy.undo(undone).core)
    }

    @Test fun decrementFloorsAtZeroAndNoOpOperationsDoNotGrowHistory() {
        val empty = ScoreboardSession()
        assertEquals(empty, ScoreboardPolicy.addPoints(empty, ScoreSide.LEFT, -1, 1L))
        val score = ScoreboardPolicy.addPoints(empty, ScoreSide.LEFT, ScoreboardPolicy.MAX_SCORE, 2L)
        assertEquals(ScoreboardPolicy.MAX_SCORE, score.core.leftScore)
        assertEquals(score, ScoreboardPolicy.addPoints(score, ScoreSide.LEFT, 1, 3L))
        val same = ScoreboardPolicy.setScore(score, ScoreSide.LEFT, score.core.leftScore, 4L)
        assertEquals(score, same)
    }

    @Test fun clampedPointChangesRecordTheActualDelta() {
        val nearTop = ScoreboardPolicy.setScore(ScoreboardSession(), ScoreSide.LEFT, 998, 1L)
        val capped = ScoreboardPolicy.addPoints(nearTop, ScoreSide.LEFT, 3, 2L)
        assertEquals(999, capped.core.leftScore)
        assertEquals(1, capped.journal.last().action.delta)

        val nearBottom = ScoreboardPolicy.setScore(ScoreboardSession(), ScoreSide.RIGHT, 1, 3L)
        val floored = ScoreboardPolicy.addPoints(nearBottom, ScoreSide.RIGHT, -3, 4L)
        assertEquals(0, floored.core.rightScore)
        assertEquals(-1, floored.journal.last().action.delta)
    }

    @Test fun directCorrectionIsBoundedAndRecordedAsItsOwnAction() {
        val session = ScoreboardPolicy.setScore(ScoreboardSession(), ScoreSide.RIGHT, 42, 10L)
        assertEquals(42, session.core.rightScore)
        assertEquals(ScoreActionKind.SET_SCORE, session.journal.single().action.kind)
        assertEquals(42, session.journal.single().action.targetScore)
        val rejected = ScoreboardPolicy.setScore(session, ScoreSide.RIGHT, ScoreboardPolicy.MAX_SCORE + 1, 11L)
        assertEquals(session, rejected)
    }

    @Test fun swapIsSymmetricAndCanBeUndone() {
        val base = ScoreboardSession(
            core = ScoreboardCore("Blue", "Red", 7, 5, 3),
            step = 2,
        )
        val swapped = ScoreboardPolicy.swap(base, 12L)
        assertEquals(ScoreboardCore("Red", "Blue", 5, 7, 3), swapped.core)
        assertEquals(base.core, ScoreboardPolicy.undo(swapped).core)
        assertEquals(2, swapped.step)
    }

    @Test fun nextPeriodResetsScoresWhileNamesAndTimerStayAvailableForUndo() {
        val base = ScoreboardSession(
            core = ScoreboardCore("Home", "Away", 11, 9, 2),
            running = true,
            accumulatedMs = 45_000,
            startedAtMs = 100_000,
        )
        val next = ScoreboardPolicy.nextPeriod(base, 13L)
        assertEquals(3, next.core.period)
        assertEquals(0, next.core.leftScore)
        assertEquals(0, next.core.rightScore)
        assertEquals("Home", next.core.leftName)
        assertEquals(100_000L, next.startedAtMs)
        assertEquals(base.core, ScoreboardPolicy.undo(next).core)
        val capped = base.copy(core = base.core.copy(period = ScoreboardPolicy.MAX_PERIOD))
        assertEquals(capped, ScoreboardPolicy.nextPeriod(capped, 14L))
    }

    @Test fun namesAreTrimmedBoundedToCodePointsAndRejectControlFormatting() {
        val emoji = "😀".repeat(ScoreboardPolicy.MAX_NAME_CODE_POINTS)
        val base = ScoreboardSession(core = ScoreboardCore("Alpha", "Beta"))
        val edited = ScoreboardPolicy.editNames(base, "  $emoji  ", "A\u202EB\nC", 20L)
        assertEquals(emoji, edited.core.leftName)
        assertEquals("ABC", edited.core.rightName)
        assertEquals(" Team A ", ScoreboardPolicy.nameInput(" Team A "))
        assertEquals("👨‍👩‍👧", ScoreboardPolicy.nameInput("👨‍👩‍👧"))
        assertEquals("", ScoreboardPolicy.nameInput("\u200B\u2060"))
        assertFalse(ScoreboardPolicy.isUsableName("\u200C\u200D"))
        val longer = emoji + "x"
        assertEquals(ScoreboardPolicy.MAX_NAME_CODE_POINTS, ScoreboardPolicy.boundedName(longer).codePointCount(0, ScoreboardPolicy.boundedName(longer).length))
        assertEquals("Alpha", ScoreboardPolicy.editedName("   ", "Alpha"))
    }

    @Test fun historyKeepsTheMostRecentOperationsAndEachRemainsUndoable() {
        var session = ScoreboardSession()
        repeat(ScoreboardPolicy.MAX_HISTORY + 7) { index ->
            session = ScoreboardPolicy.addPoints(session, ScoreSide.LEFT, 1, index.toLong())
        }
        assertEquals(ScoreboardPolicy.MAX_HISTORY + 7, session.core.leftScore)
        assertEquals(ScoreboardPolicy.MAX_HISTORY, session.journal.size)
        repeat(ScoreboardPolicy.MAX_HISTORY) { session = ScoreboardPolicy.undo(session) }
        assertEquals(7, session.core.leftScore)
        assertTrue(session.journal.isEmpty())
    }

    @Test fun timerUsesMonotonicSegmentsAndStopsAtTheDisplayBudget() {
        val started = ScoreboardPolicy.startTimer(ScoreboardSession(), 1_000)
        assertEquals(1_500L, started.elapsedMs(2_500))
        val paused = ScoreboardPolicy.pauseTimer(started, 2_500)
        assertFalse(paused.running)
        assertEquals(1_500L, paused.elapsedMs(99_000))
        val resumed = ScoreboardPolicy.startTimer(paused, 100_000)
        assertEquals(3_500L, resumed.elapsedMs(102_000))
        val reset = ScoreboardPolicy.resetTimer(resumed, 102_000)
        assertEquals(0L, reset.elapsedMs(102_000))
        assertEquals(3_000L, reset.elapsedMs(105_000))
        assertEquals("00:00:00", ScoreboardPolicy.formatDuration(-1L))
        assertEquals("01:02:03", ScoreboardPolicy.formatDuration(3_723_999L))
        assertEquals("99:59:59", ScoreboardPolicy.formatDuration(Long.MAX_VALUE))
    }

    @Test fun timerRestoreClampsARebootedClockAndAcceptanceRanges() {
        val active = ScoreboardPolicy.restoreTimer(
            true, 2_000, 10_000, 1_000_000, 12_500, 1_002_500,
        )
        assertEquals(4_500L, active)
        val rebooted = ScoreboardPolicy.restoreTimer(
            true, 7_000, 500_000, 2_000_000, 600_000, 2_005_000,
        )
        assertEquals(12_000L, rebooted)
        assertEquals(2_000L, ScoreboardPolicy.restoreTimer(false, 2_000, 10_000, null, 20_000, 2_000_000))
    }

    @Test fun savedJournalRoundTripsUnicodeAndIgnoresCorruptEntries() {
        var session = ScoreboardPolicy.editNames(ScoreboardSession(), "主队🚀", "客队", 1L)
        session = ScoreboardPolicy.addPoints(session, ScoreSide.LEFT, 2, 2L)
        session = ScoreboardPolicy.swap(session, 3L)
        val encoded = ScoreboardPolicy.encodeJournal(session.journal)
        assertEquals(session.journal, ScoreboardPolicy.decodeJournal(encoded))
        val corrupted = arrayListOf("junk", encoded.first(), "1,X,LEFT,1,,1,,,0,0,1,,,0,0,1")
        assertTrue(ScoreboardPolicy.decodeJournal(corrupted).isNotEmpty())
        val badTime = encoded.first().split(',').toMutableList()
        badTime[5] = Long.MAX_VALUE.toString()
        assertTrue(ScoreboardPolicy.decodeJournal(listOf(badTime.joinToString(","))).isEmpty())
    }

    @Test fun resetKeepsNamesAndStepButClearsScoreTimerAndHistory() {
        val session = ScoreboardPolicy.addPoints(
            ScoreboardSession(ScoreboardCore("Home", "Away", 4, 3, 2), step = 3, running = true, accumulatedMs = 5_000, startedAtMs = 10_000),
            ScoreSide.RIGHT, 2, 20L,
        )
        val reset = ScoreboardPolicy.reset(session)
        assertEquals(ScoreboardCore("Home", "Away"), reset.core)
        assertEquals(3, reset.step)
        assertFalse(reset.running)
        assertEquals(0L, reset.elapsedMs(99_000))
        assertTrue(reset.journal.isEmpty())
    }
}
