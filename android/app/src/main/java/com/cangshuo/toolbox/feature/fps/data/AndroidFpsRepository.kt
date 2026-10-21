package com.cangshuo.toolbox.feature.fps.data

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import com.cangshuo.toolbox.feature.fps.domain.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AndroidFpsRepository(context: Context) : FpsRepository {
    private val preferences = context.getSharedPreferences("frame_rate_test", Context.MODE_PRIVATE)
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val mutex = Mutex()
    override fun observe(request: FpsRequest, displayId: Int): Flow<FpsSnapshot> = callbackFlow {
        require(FpsPolicy.valid(request))
        val choreographer = Choreographer.getInstance()
        val handler = Handler(Looper.getMainLooper())
        fun refresh() = displays?.getDisplay(displayId)?.refreshRate?.toDouble()?.takeIf(FpsPolicy::refresh)
        val initial = refresh()
        if (initial == null) { trySend(FpsSnapshot(FpsStage.FAILED)); close(); return@callbackFlow }
        val accumulator = FpsAccumulator(request, if (request.targetHz == 0) initial else request.targetHz.toDouble())
        val id = UUID.randomUUID().toString()
        val at = System.currentTimeMillis()
        var lastTick = SystemClock.uptimeMillis()
        var published = lastTick
        var rate: Double = initial
        var terminal = false
        trySend(FpsSnapshot(FpsStage.WAITING))
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (terminal) return
                val now = SystemClock.uptimeMillis()
                lastTick = now
                if (now - published >= 250) rate = refresh() ?: rate
                accumulator.frame(frameTimeNanos, rate)
                terminal = accumulator.stage !in setOf(FpsStage.WAITING, FpsStage.RUNNING)
                if (terminal || now - published >= 250) {
                    trySend(FpsSnapshot(accumulator.stage, accumulator.snapshot(id, at)))
                    published = now
                }
                if (terminal) close() else choreographer.postFrameCallback(this)
            }
        }
        val watchdog = object : Runnable {
            override fun run() {
                if (terminal) return
                if (SystemClock.uptimeMillis() - lastTick >= 3_000) {
                    terminal = true
                    choreographer.removeFrameCallback(callback)
                    trySend(FpsSnapshot(FpsStage.NO_FRAMES, accumulator.snapshot(id, at))); close()
                } else handler.postDelayed(this, 500)
            }
        }
        choreographer.postFrameCallback(callback)
        handler.postDelayed(watchdog, 500)
        awaitClose {
            terminal = true
            choreographer.removeFrameCallback(callback)
            handler.removeCallbacks(watchdog)
        }
    }.flowOn(Dispatchers.Main.immediate)

    override suspend fun history() = withContext(Dispatchers.IO) { mutex.withLock { read() } }
    override suspend fun save(record: FpsRecord) = withContext(Dispatchers.IO) { mutex.withLock {
        require(FpsPolicy.valid(record))
        val records = (listOf(record) + read().filterNot { it.id == record.id }).take(FpsPolicy.HISTORY_LIMIT)
        write(records); records
    } }
    override suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock { write(emptyList()); emptyList<FpsRecord>() } }
    private fun read(): List<FpsRecord> {
        val raw = preferences.getString("history", "[]") ?: "[]"
        check(raw.length <= FpsPolicy.STORAGE_LIMIT)
        val array = JSONArray(raw)
        check(array.length() <= FpsPolicy.HISTORY_LIMIT)
        return (0 until array.length()).map { FpsRecordJson.decode(array.getJSONObject(it)) }.also {
            check(it.map(FpsRecord::id).distinct().size == it.size)
        }
    }
    @SuppressLint("UseKtx") // Report a failed durable write.
    private fun write(records: List<FpsRecord>) {
        val raw = JSONArray().apply { records.forEach { put(FpsRecordJson.encode(it)) } }.toString()
        check(raw.length <= FpsPolicy.STORAGE_LIMIT)
        check(preferences.edit().putString("history", raw).commit())
    }
}

/** Used only by the storage adapter; ViewModels keep framework-neutral records. */
object FpsRecordJson {
    fun encode(r: FpsRecord): JSONObject = JSONObject().put("id", r.id).put("at", r.at)
        .put("seconds", r.request.seconds).put("target", r.request.targetHz).put("objects", r.request.objects)
        .put("budget", r.budgetHz).put("elapsed", r.elapsedNanos).put("frames", r.frames)
        .put("p95", r.p95Nanos).put("p99", r.p99Nanos).put("worst", r.worstNanos).put("long", r.longFrames)
        .put("missed", r.estimatedMissed).put("ignored", r.ignored).put("min", r.minRefresh).put("max", r.maxRefresh)
        .put("complete", r.complete).put("windows", JSONArray().apply {
            r.windows.forEach { put(JSONArray().put(it.nanos).put(it.count)) }
        })
    fun decode(o: JSONObject): FpsRecord {
        val windows = o.getJSONArray("windows")
        check(windows.length() in 1..80)
        return FpsRecord(o.getString("id"), o.getLong("at"), FpsRequest(o.getInt("seconds"), o.getInt("target"), o.getInt("objects")),
            o.getDouble("budget"), o.getLong("elapsed"), o.getInt("frames"), o.getLong("p95"), o.getLong("p99"), o.getLong("worst"),
            o.getInt("long"), o.getLong("missed"), o.getInt("ignored"), o.getDouble("min"), o.getDouble("max"),
            (0 until windows.length()).map { windows.getJSONArray(it).let { row -> check(row.length() == 2); FpsWindow(row.getLong(0), row.getInt(1)) } },
            o.getBoolean("complete")).also { check(FpsPolicy.valid(it)) }
    }
}
