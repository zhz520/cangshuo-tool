package com.cangshuo.toolbox.feature.answerbook.domain

enum class AnswerTheme { ALL, ACTION, REST, PERSPECTIVE }
data class AnswerEntry(val id: Int, val theme: AnswerTheme)
data class AnswerDraw(val token: String, val answer: Int, val theme: AnswerTheme, val at: Long)
data class AnswerBookState(
    val history: List<AnswerDraw> = emptyList(),
    val favorites: Set<Int> = emptySet(),
    val used: Map<AnswerTheme, Set<Int>> = emptyMap(),
    val last: Int? = null,
)

object AnswerBookPolicy {
    const val HISTORY_LIMIT = 50
    val entries = (0 until 60).map { AnswerEntry(it, AnswerTheme.entries[1 + it / 20]) }
    fun pool(theme: AnswerTheme) = entries.filter { theme == AnswerTheme.ALL || it.theme == theme }.map { it.id }
    fun remaining(state: AnswerBookState, theme: AnswerTheme) = pool(theme).size - state.used[theme].orEmpty().size
    fun valid(state: AnswerBookState): Boolean = state.history.size <= HISTORY_LIMIT &&
        state.history.all { it.answer in entries.indices && it.at > 0 && it.token.matches(Regex("[A-Za-z0-9-]{1,64}")) && it.answer in pool(it.theme) } &&
        state.history.map { it.token }.distinct().size == state.history.size &&
        state.favorites.all { it in entries.indices } && (state.last == null || state.last in entries.indices) &&
        state.used.all { (theme, ids) -> ids.all { it in pool(theme) } }

    fun draw(state: AnswerBookState, theme: AnswerTheme, index: (Int) -> Int, token: String, now: Long): AnswerBookState {
        require(valid(state) && now > 0 && token.matches(Regex("[A-Za-z0-9-]{1,64}")) && state.history.none { it.token == token })
        val pool = pool(theme)
        val unseen = pool.filterNot { it in state.used[theme].orEmpty() }
        val exhausted = unseen.isEmpty()
        // Exclude the immediately previous draw only at cycle boundaries; all other unseen entries remain eligible.
        val candidates = if (exhausted) pool.filterNot { it == state.last } else unseen
        val chosenIndex = index(candidates.size).also { require(it in candidates.indices) }
        val answer = candidates[chosenIndex]
        val used = (if (exhausted) emptySet() else state.used[theme].orEmpty()) + answer
        return state.copy(history = (listOf(AnswerDraw(token, answer, theme, now)) + state.history).take(HISTORY_LIMIT),
            used = state.used + (theme to used), last = answer)
    }

    fun favorite(state: AnswerBookState, answer: Int): AnswerBookState {
        require(answer in entries.indices)
        return state.copy(favorites = if (answer in state.favorites) state.favorites - answer else state.favorites + answer)
    }
}

interface AnswerBookRepository {
    suspend fun load(): AnswerBookState
    suspend fun draw(theme: AnswerTheme): AnswerBookState
    suspend fun favorite(answer: Int): AnswerBookState
    suspend fun clearHistory(): AnswerBookState
    suspend fun reset(): AnswerBookState
}
class AnswerBookUseCases(private val repository: AnswerBookRepository) {
    suspend fun load() = repository.load()
    suspend fun draw(theme: AnswerTheme) = repository.draw(theme)
    suspend fun favorite(answer: Int) = repository.favorite(answer)
    suspend fun clearHistory() = repository.clearHistory()
    suspend fun reset() = repository.reset()
}
