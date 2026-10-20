package com.cangshuo.toolbox.feature.answerbook.data

import android.annotation.SuppressLint
import android.content.Context
import com.cangshuo.toolbox.feature.answerbook.domain.*
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LocalAnswerBookRepository(context: Context) : AnswerBookRepository {
    private val preferences = context.getSharedPreferences("answer_book", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val random = SecureRandom()
    override suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock { read() } }
    override suspend fun draw(theme: AnswerTheme) = change { AnswerBookPolicy.draw(it, theme, random::nextInt, UUID.randomUUID().toString(), System.currentTimeMillis()) }
    override suspend fun favorite(answer: Int) = change { AnswerBookPolicy.favorite(it, answer) }
    override suspend fun clearHistory() = change { it.copy(history = emptyList()) }
    override suspend fun reset() = withContext(Dispatchers.IO) { mutex.withLock { AnswerBookState().also(::write) } }

    private suspend fun change(action: (AnswerBookState) -> AnswerBookState) = withContext(Dispatchers.IO) {
        mutex.withLock { action(read()).also(::write) }
    }
    private fun JSONArray.ids(): Set<Int> {
        check(length() <= 60)
        val values = (0 until length()).map { getInt(it) }
        check(values.size == values.distinct().size)
        return values.toSet()
    }
    private fun read(): AnswerBookState {
        val raw = preferences.getString("state", null) ?: return AnswerBookState()
        check(raw.length <= 65_536)
        val json = JSONObject(raw)
        check(json.getInt("version") == 1)
        val history = json.getJSONArray("history")
        check(history.length() <= AnswerBookPolicy.HISTORY_LIMIT)
        val used = json.getJSONObject("used")
        val usedKeys = used.keys().asSequence().toSet()
        check(usedKeys.all { name -> AnswerTheme.entries.any { it.name == name } })
        return AnswerBookState(
            history = (0 until history.length()).map { history.getJSONObject(it).let { item ->
                AnswerDraw(item.getString("token"), item.getInt("answer"), AnswerTheme.valueOf(item.getString("theme")), item.getLong("at")) } },
            favorites = json.getJSONArray("favorites").ids(),
            used = usedKeys.associate { name -> AnswerTheme.valueOf(name) to used.getJSONArray(name).ids() },
            last = if (json.isNull("last")) null else json.getInt("last"),
        ).also { check(AnswerBookPolicy.valid(it)) }
    }
    @SuppressLint("UseKtx") // Check the durable-write result before showing a draw as saved.
    private fun write(state: AnswerBookState) {
        check(AnswerBookPolicy.valid(state))
        val history = JSONArray()
        state.history.forEach { history.put(JSONObject().put("token", it.token).put("answer", it.answer).put("theme", it.theme.name).put("at", it.at)) }
        val used = JSONObject()
        state.used.forEach { (key, value) -> used.put(key.name, JSONArray(value.sorted())) }
        val json = JSONObject().put("version", 1).put("history", history).put("favorites", JSONArray(state.favorites.sorted()))
            .put("used", used).put("last", state.last ?: JSONObject.NULL)
        check(preferences.edit().putString("state", json.toString()).commit())
    }
}
