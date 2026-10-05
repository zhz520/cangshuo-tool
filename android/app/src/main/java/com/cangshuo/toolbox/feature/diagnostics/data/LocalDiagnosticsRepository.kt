package com.cangshuo.toolbox.feature.diagnostics.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.AtomicFile
import com.cangshuo.toolbox.feature.diagnostics.domain.*
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.withLock

/** Only two timestamps are persisted. Never inspect exception messages or OS trace streams. */
internal class DiagnosticStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "diagnostic-times.txt"))
    private fun readUnlocked(): Pair<Long, Long> {
        if (!file.baseFile.exists()) return 0L to 0L
        val bytes = file.openRead().use { input ->
            val buffer = ByteArray(129); var count = 0
            while (count < buffer.size) { val read = input.read(buffer, count, buffer.size - count); if (read <= 0) break; count += read }
            buffer.copyOf(count)
        }
        if (bytes.size > 128) return 0L to 0L
        val parts = bytes.toString(Charsets.US_ASCII).split('\n')
        return (parts.getOrNull(0)?.toLongOrNull()?.coerceAtLeast(0) ?: 0L) to
            (parts.getOrNull(1)?.toLongOrNull()?.coerceAtLeast(0) ?: 0L)
    }
    fun read(): Pair<Long, Long> = lock.withLock { readUnlocked() }
    private fun write(lastCrash: Long, cutoff: Long) {
        val stream = file.startWrite()
        try { stream.write("$lastCrash\n$cutoff\n".toByteArray(Charsets.US_ASCII)); file.finishWrite(stream) }
        catch (error: Throwable) { file.failWrite(stream); throw error }
    }
    fun recordCrash() {
        // Never wait for another thread that may itself have crashed while holding this lock.
        if (!lock.tryLock()) return
        try { write(System.currentTimeMillis(), readUnlocked().second) } finally { lock.unlock() }
    }
    fun clear() = lock.withLock { write(0, System.currentTimeMillis()) }
    companion object { private val lock = ReentrantLock() }
}

fun installLocalCrashMonitor(context: Context) {
    val existing = Thread.getDefaultUncaughtExceptionHandler() ?: return
    val store = DiagnosticStore(context.applicationContext)
    Thread.setDefaultUncaughtExceptionHandler(SafeCrashHandler(store::recordCrash, existing))
}

class LocalDiagnosticsRepository(context: Context) : DiagnosticsRepository {
    private val app = context.applicationContext
    private val store = DiagnosticStore(app)
    override suspend fun read(): Diagnostics = withContext(Dispatchers.IO) {
        val (lastJavaCrash, cutoff) = store.read()
        val raw = mutableListOf(lastJavaCrash to 4)
        var available = false
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val manager = app.getSystemService(ActivityManager::class.java)
                if (manager != null) {
                    manager.getHistoricalProcessExitReasons(app.packageName, 0, 20).take(20).forEach { raw += it.timestamp to it.reason }
                    available = true
                }
            } catch (_: Exception) { /* Device restrictions: keep the Java fallback. */ }
        }
        Diagnostics(boundedEvents(raw, System.currentTimeMillis(), cutoff), available)
    }
    override suspend fun clear() = withContext(Dispatchers.IO) { store.clear() }
}
