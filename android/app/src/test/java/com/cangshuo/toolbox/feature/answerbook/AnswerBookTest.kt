package com.cangshuo.toolbox.feature.answerbook

import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.answerbook.domain.*
import com.cangshuo.toolbox.feature.answerbook.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnswerBookTest {
    private val dispatcher = StandardTestDispatcher()
    private class Fake : AnswerBookRepository {
        var book = AnswerBookState()
        var fail = false
        var draws = 0
        override suspend fun load(): AnswerBookState { check(!fail); return book }
        override suspend fun draw(theme: AnswerTheme): AnswerBookState {
            check(!fail); draws++; book = AnswerBookPolicy.draw(book, theme, { 0 }, "draw-$draws", draws.toLong()); return book
        }
        override suspend fun favorite(answer: Int): AnswerBookState { check(!fail); book = AnswerBookPolicy.favorite(book, answer); return book }
        override suspend fun clearHistory(): AnswerBookState { check(!fail); book = book.copy(history = emptyList()); return book }
        override suspend fun reset(): AnswerBookState { book = AnswerBookState(); fail = false; return book }
    }
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun catalogHasSixtyStableIdsAndThreeEqualThemes() {
        assertEquals((0 until 60).toList(), AnswerBookPolicy.entries.map { it.id })
        assertEquals(60, AnswerBookPolicy.pool(AnswerTheme.ALL).size)
        AnswerTheme.entries.drop(1).forEach { assertEquals(20, AnswerBookPolicy.pool(it).size) }
    }
    @Test fun drawsVisitEveryPageExactlyOnceWithinEachThemeCycle() {
        AnswerTheme.entries.forEach { theme ->
            var s = AnswerBookState()
            val selected = mutableListOf<Int>()
            repeat(AnswerBookPolicy.pool(theme).size) { n ->
                s = AnswerBookPolicy.draw(s, theme, { it - 1 }, "test-$n", n + 1L)
                selected += s.last!!
            }
            assertEquals(AnswerBookPolicy.pool(theme).toSet(), selected.toSet())
            assertEquals(0, AnswerBookPolicy.remaining(s, theme))
        }
    }
    @Test fun newCycleAvoidsImmediateDuplicateAndCanReachEveryEntryAgain() {
        var s = AnswerBookState()
        repeat(20) { s = AnswerBookPolicy.draw(s, AnswerTheme.ACTION, { 0 }, "first-$it", it + 1L) }
        val previous = s.last
        s = AnswerBookPolicy.draw(s, AnswerTheme.ACTION, { it - 1 }, "next", 21)
        assertNotEquals(previous, s.last)
        repeat(19) { s = AnswerBookPolicy.draw(s, AnswerTheme.ACTION, { 0 }, "next-$it", 22L + it) }
        assertEquals(20, s.used[AnswerTheme.ACTION]!!.size)
    }
    @Test fun themesKeepIndependentProgressAndHistoryIsBounded() {
        var s = AnswerBookState()
        repeat(75) { s = AnswerBookPolicy.draw(s, AnswerTheme.ALL, { 0 }, "all-$it", 1L + it) }
        assertEquals(50, s.history.size); assertEquals("all-74", s.history.first().token)
        s = AnswerBookPolicy.draw(s, AnswerTheme.REST, { 0 }, "rest", 76)
        assertEquals(1, s.used[AnswerTheme.REST]!!.size); assertEquals(15, s.used[AnswerTheme.ALL]!!.size)
        assertTrue(AnswerBookPolicy.valid(s))
    }
    @Test fun favoritesToggleWithoutChangingDrawProgressOrHistory() {
        val s = AnswerBookPolicy.draw(AnswerBookState(), AnswerTheme.ALL, { 0 }, "a", 1)
        val marked = AnswerBookPolicy.favorite(s, 0)
        assertEquals(setOf(0), marked.favorites); assertEquals(s.used, marked.used); assertEquals(s.history, marked.history)
        assertEquals(s, AnswerBookPolicy.favorite(marked, 0))
    }
    @Test fun clearHistoryCannotEraseCycleOrFavorites() {
        val s = AnswerBookPolicy.favorite(AnswerBookPolicy.draw(AnswerBookState(), AnswerTheme.ACTION, { 0 }, "a", 1), 0)
        val cleared = s.copy(history = emptyList())
        val next = AnswerBookPolicy.draw(cleared, AnswerTheme.ACTION, { 0 }, "b", 2)
        assertEquals(1, next.last); assertEquals(setOf(0), next.favorites)
    }
    @Test fun invalidPersistentIdsDuplicatesAndThemeMismatchesAreRejected() {
        val good = AnswerBookState(history = listOf(AnswerDraw("a", 0, AnswerTheme.ACTION, 1)))
        assertTrue(AnswerBookPolicy.valid(good))
        assertFalse(AnswerBookPolicy.valid(good.copy(favorites = setOf(60))))
        assertFalse(AnswerBookPolicy.valid(good.copy(last = -1)))
        assertFalse(AnswerBookPolicy.valid(good.copy(history = good.history + good.history)))
        assertFalse(AnswerBookPolicy.valid(good.copy(used = mapOf(AnswerTheme.ACTION to setOf(40)))))
        assertFalse(AnswerBookPolicy.valid(good.copy(history = listOf(AnswerDraw("a", 20, AnswerTheme.ACTION, 1)))))
    }
    @Test(expected = IllegalArgumentException::class) fun randomIndexMustRemainInsideCandidatePool() {
        AnswerBookPolicy.draw(AnswerBookState(), AnswerTheme.ALL, { it }, "a", 1)
    }
    @Test fun repeatedClicksAreSerializedAndOnlyCommittedResultIsShown() = runTest(dispatcher) {
        val fake = Fake(); val m = AnswerBookViewModel(AnswerBookUseCases(fake), SavedStateHandle())
        runCurrent(); m.draw(); m.draw(); m.draw(); assertNull(m.state.value.current)
        runCurrent(); assertEquals(1, fake.draws); assertEquals(0, m.state.value.current)
        m.draw(); runCurrent(); assertEquals(1, m.state.value.current)
    }
    @Test fun failedDrawKeepsPreviousAnswerAndRequiresReload() = runTest(dispatcher) {
        val fake = Fake(); val m = AnswerBookViewModel(AnswerBookUseCases(fake), SavedStateHandle())
        runCurrent(); m.draw(); runCurrent(); fake.fail = true; m.draw(); runCurrent()
        assertEquals(BookError.WRITE, m.state.value.error); assertEquals(0, m.state.value.current)
        fake.fail = false; m.draw(); runCurrent(); assertEquals(1, fake.draws)
        m.load(); runCurrent(); m.draw(); runCurrent(); assertEquals(2, fake.draws)
    }
    @Test fun persistedCyclesSurviveNewViewModelAndSelectingOldPageDoesNotDraw() = runTest(dispatcher) {
        val fake = Fake(); val saved = SavedStateHandle(); val m = AnswerBookViewModel(AnswerBookUseCases(fake), saved)
        runCurrent(); m.theme(AnswerTheme.PERSPECTIVE); m.draw(); runCurrent(); m.favorite(40); runCurrent()
        m.tab(true); m.select(0)
        val restored = AnswerBookViewModel(AnswerBookUseCases(fake), saved); runCurrent()
        assertEquals(AnswerTheme.PERSPECTIVE, restored.state.value.theme); assertEquals(0, restored.state.value.current)
        assertEquals(1, fake.draws); restored.draw(); runCurrent(); assertEquals(41, restored.state.value.current)
    }
    @Test fun corruptLoadCanRecoverWithExplicitReset() = runTest(dispatcher) {
        val fake = Fake().apply { fail = true }; val m = AnswerBookViewModel(AnswerBookUseCases(fake), SavedStateHandle())
        runCurrent(); assertEquals(BookError.LOAD, m.state.value.error); assertFalse(m.state.value.loaded)
        m.draw(); runCurrent(); assertEquals(0, fake.draws)
        m.reset(); runCurrent(); assertTrue(m.state.value.loaded); assertNull(m.state.value.current)
        m.draw(); runCurrent(); assertEquals(1, fake.draws)
    }
}
