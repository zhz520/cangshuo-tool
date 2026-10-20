package com.cangshuo.toolbox.feature.tapspeed.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import com.cangshuo.toolbox.feature.tapspeed.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LocalTapRepository(context: Context) : TapRepository {
    private val preferences = context.getSharedPreferences("tap_speed", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    override fun now() = SystemClock.uptimeMillis()
    override fun wall() = System.currentTimeMillis()
    override suspend fun history() = withContext(Dispatchers.IO) { mutex.withLock { read() } }
    override suspend fun save(record: TapRecord) = withContext(Dispatchers.IO) {
        require(TapPolicy.valid(record))
        mutex.withLock {
            val result = (listOf(record) + read().filterNot { it.id == record.id }).take(TapPolicy.HISTORY_LIMIT)
            write(result); result
        }
    }
    override suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock { write(emptyList()); emptyList<TapRecord>() } }

    private fun read(): List<TapRecord> {
        val raw = preferences.getString("history", "[]") ?: "[]"
        check(raw.length <= 65_536)
        val array = JSONArray(raw)
        check(array.length() <= TapPolicy.HISTORY_LIMIT)
        val result = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val bins = item.getJSONArray("bins")
            val seconds = item.getInt("seconds")
            check(seconds in TapPolicy.durations && bins.length() == seconds)
            TapRecord(item.getString("id"), item.getLong("at"), seconds, item.getBoolean("multi"),
                (0 until seconds).map { bins.getInt(it) }, item.getInt("sources")).also { check(TapPolicy.valid(it)) }
        }
        check(result.map { it.id }.distinct().size == result.size)
        return result
    }

    @SuppressLint("UseKtx") // A failed durable commit must be surfaced to the user.
    private fun write(records: List<TapRecord>) {
        val array = JSONArray()
        records.forEach { array.put(JSONObject().put("id", it.id).put("at", it.at).put("seconds", it.seconds)
            .put("multi", it.multiTouch).put("bins", JSONArray(it.bins)).put("sources", it.sources)) }
        check(preferences.edit().putString("history", array.toString()).commit())
    }
}
