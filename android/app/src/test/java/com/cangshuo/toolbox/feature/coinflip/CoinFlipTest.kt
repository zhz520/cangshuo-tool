package com.cangshuo.toolbox.feature.coinflip

import com.cangshuo.toolbox.feature.coinflip.data.LocalCoinRepository
import com.cangshuo.toolbox.feature.coinflip.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoinFlipTest {
    private val labels = CoinLabels("Heads", "Tails")
    private fun batch(vararg sides: CoinSide) = CoinBatch(sides.toList(), labels)

    @Test fun batchCountAcceptsOnlyAsciiWholeNumbersWithinTheBudget() {
        listOf("", "0", "101", "1000", "-1", "+2", "1.0", " 2", "２", "NaN").forEach { assertNull(it, CoinPolicy.count(it)) }
        assertEquals(1, CoinPolicy.count("001")); assertEquals(100, CoinPolicy.count("100"))
    }
    @Test fun labelsTrimEdgesRejectAmbiguousOrHiddenTextAndKeepEmojiIntact() {
        assertEquals(CoinLabels("Go", "Stay"), CoinPolicy.labels(" Go ", "Stay"))
        listOf("", "x".repeat(21), "a\nb", "a\u0000b", "a\u202Eb", "a\u2066b", "\uD800").forEach {
            assertNull(it, CoinPolicy.labels(it, "Other"))
        }
        assertNull(CoinPolicy.labels("GO", "go"))
        val emoji = "\uD83D\uDE00".repeat(20)
        assertEquals(emoji, CoinPolicy.labels(emoji, "Other")!!.heads)
        assertNull(CoinPolicy.labels(emoji + "x", "Other"))
    }
    @Test fun consecutiveResultsAreNotArtificiallyBalanced() = runTest {
        val repository = LocalCoinRepository(CoinRandomSource { true }, StandardTestDispatcher(testScheduler))
        val sides = FlipCoinsUseCase(repository)(100)
        assertEquals(List(100) { CoinSide.HEADS }, sides)
        val session = CoinSession().add(CoinBatch(sides, labels))
        assertEquals(100L, session.heads); assertEquals(0L, session.tails)
        assertEquals(100, session.longestStreak)
    }
    @Test fun localSourcePreservesEachDrawExactlyOnceInOrder() = runTest {
        var calls = 0
        val repository = LocalCoinRepository(CoinRandomSource { calls++ % 2 == 0 }, StandardTestDispatcher(testScheduler))
        assertEquals(listOf(CoinSide.HEADS, CoinSide.TAILS, CoinSide.HEADS), repository.flip(3))
        assertEquals(3, calls)
    }
    @Test fun invalidBatchDoesNotConsumeAnyRandomness() = runTest {
        var calls = 0
        val useCase = FlipCoinsUseCase(LocalCoinRepository(CoinRandomSource { calls++; true }, StandardTestDispatcher(testScheduler)))
        for (count in listOf(-1, 0, 101, Int.MAX_VALUE)) {
            try { useCase(count); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, calls)
    }
    @Test fun malformedRepositoryOutputCannotBecomeAPartialSuccess() = runTest {
        val useCase = FlipCoinsUseCase(object : CoinRepository { override suspend fun flip(count: Int) = emptyList<CoinSide>() })
        try { useCase(2); fail("Expected rejection") } catch (_: IllegalStateException) { }
    }
    @Test fun sessionCountsAndStreaksContinueAcrossBatchBoundaries() {
        val first = CoinSession().add(batch(CoinSide.HEADS, CoinSide.TAILS, CoinSide.TAILS))
        val second = first.add(batch(CoinSide.TAILS, CoinSide.HEADS))
        assertEquals(2L, second.heads); assertEquals(3L, second.tails); assertEquals(5L, second.total)
        assertEquals(1, second.streak); assertEquals(3, second.longestStreak)
        assertEquals(CoinSide.HEADS, second.lastSide)
    }
    @Test fun historyBudgetDoesNotTruncateStatisticsOrSequenceNumbers() {
        var session = CoinSession()
        repeat(3) { session = session.add(CoinBatch(List(100) { CoinSide.HEADS }, labels)) }
        assertEquals(300L, session.total); assertEquals(300, session.streak)
        assertEquals(100, session.records.size)
        assertEquals(201L, session.records.first().number); assertEquals(300L, session.records.last().number)
    }
    @Test fun oldLabelsRemainAssociatedWithTheirOwnResults() {
        val before = CoinSession().add(batch(CoinSide.HEADS))
        val after = before.add(CoinBatch(listOf(CoinSide.TAILS), CoinLabels("Buy", "Wait")))
        assertEquals("Heads", after.records.first().labels.forSide(after.records.first().side))
        assertEquals("Wait", after.records.last().labels.forSide(after.records.last().side))
    }
    @Test fun majorityAndTieAreDerivedFromActualDraws() {
        assertNull(batch(CoinSide.HEADS, CoinSide.TAILS).majority)
        assertEquals(CoinSide.TAILS, batch(CoinSide.TAILS, CoinSide.HEADS, CoinSide.TAILS).majority)
    }
    @Test fun sessionLimitIsAcceptedExactlyAndCannotBeExceeded() {
        val session = CoinSession(heads = CoinPolicy.MAX_SESSION - 1, lastSide = CoinSide.HEADS, streak = 1)
            .add(batch(CoinSide.TAILS))
        assertEquals(CoinPolicy.MAX_SESSION, session.total)
        try { session.add(batch(CoinSide.TAILS)); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }
    @Test fun csvKeepsOriginalLabelsQuotesCommasAndNeutralisesFormulas() {
        val custom = CoinLabels("=SUM(1,2)", "say \"no\"")
        val records = CoinSession().add(CoinBatch(listOf(CoinSide.HEADS, CoinSide.TAILS), custom)).records
        val csv = CoinPolicy.csv(records)
        assertTrue(csv.startsWith("number,side,result,heads_label,tails_label\r\n"))
        assertTrue(csv.contains("1,HEADS,\"'=SUM(1,2)\""))
        assertTrue(csv.contains("2,TAILS,\"say \"\"no\"\"\""))
        assertEquals("=SUM(1,2)", records.first().labels.heads)
        assertEquals(4, csv.split("\r\n").size)
    }
    @Test fun csvRejectsAnUnboundedRecordList() {
        try { CoinPolicy.csv(List(101) { CoinRecord(it.toLong(), CoinSide.HEADS, labels) }); fail("Expected rejection") }
        catch (_: IllegalArgumentException) { }
    }
}
