package com.cangshuo.toolbox.feature.reaction.data

import android.content.Context
import android.annotation.SuppressLint
import android.os.SystemClock
import com.cangshuo.toolbox.feature.reaction.domain.*
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LocalReactionRepository(context: Context) : ReactionRepository {
    private val preferences = context.getSharedPreferences("reaction_test", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val random = SecureRandom()
    override fun nowMs() = SystemClock.uptimeMillis()
    override fun wallMs() = System.currentTimeMillis()
    override fun waitMs() = (ReactionPolicy.MIN_WAIT + random.nextInt(ReactionPolicy.MAX_WAIT - ReactionPolicy.MIN_WAIT + 1)).toLong()

    override suspend fun history() = withContext(Dispatchers.IO) { lock.withLock { read() } }
    override suspend fun save(record: ReactionRecord) = withContext(Dispatchers.IO) {
        require(ReactionPolicy.valid(record))
        lock.withLock {
            val records = (listOf(record) + read().filterNot { it.id == record.id }).take(ReactionPolicy.HISTORY_LIMIT)
            write(records)
            records
        }
    }
    override suspend fun clear() = withContext(Dispatchers.IO) {
        lock.withLock { write(emptyList()); emptyList<ReactionRecord>() }
    }

    private fun read(): List<ReactionRecord> {
        val raw = preferences.getString("history", "[]") ?: "[]"
        check(raw.length <= 32_768)
        val array = JSONArray(raw)
        check(array.length() <= ReactionPolicy.HISTORY_LIMIT)
        val records = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val values = item.getJSONArray("samples")
            check(values.length() in ReactionPolicy.roundOptions)
            ReactionRecord(item.getString("id"), item.getLong("at"),
                (0 until values.length()).map { values.getLong(it) }, item.getInt("early"), item.getInt("missed"))
                .also { check(ReactionPolicy.valid(it)) }
        }
        check(records.map { it.id }.distinct().size == records.size)
        return records
    }

    @SuppressLint("UseKtx") // commit()'s Boolean is needed to report a failed durable write.
    private fun write(records: List<ReactionRecord>) {
        val array = JSONArray()
        records.forEach { record -> array.put(JSONObject().put("id", record.id).put("at", record.completedAt)
            .put("samples", JSONArray(record.samples)).put("early", record.early).put("missed", record.missed)) }
        check(preferences.edit().putString("history", array.toString()).commit())
    }
}
