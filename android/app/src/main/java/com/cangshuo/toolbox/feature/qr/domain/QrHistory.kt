package com.cangshuo.toolbox.feature.qr.domain

import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.flow.Flow

data class QrHistoryEntry(val key: String, val result: QrDecodeEntry, val scannedAt: Long)
data class QrHistoryState(val enabled: Boolean = false, val entries: List<QrHistoryEntry> = emptyList())
enum class QrHistoryRecordResult { SAVED, DISABLED, SKIPPED }
class QrHistoryException : Exception("Scan history unavailable", null)

interface QrHistoryRepository {
    fun observe(): Flow<QrHistoryState>
    suspend fun setEnabled(enabled: Boolean)
    suspend fun record(entries: List<QrDecodeEntry>): QrHistoryRecordResult
    suspend fun remove(key: String)
    suspend fun clear()
}

/** Explicit domain entry points; QR screens never access a database. */
class QrHistoryUseCases(private val repository: QrHistoryRepository) {
    fun observe() = repository.observe()
    suspend fun setEnabled(enabled: Boolean) = repository.setEnabled(enabled)
    suspend fun record(entries: List<QrDecodeEntry>) = repository.record(entries)
    suspend fun remove(key: String) = repository.remove(key)
    suspend fun clear() = repository.clear()
}

object QrHistoryPolicy {
    const val MAX_BYTES = 16000
    const val MAX_BATCH = 50
    fun mayStore(result: QrDecodeEntry): Boolean {
        val text = result.text
        if (text.isEmpty() || text.length > MAX_BYTES) return false
        val prefix = text.trimStart().take(32).lowercase(Locale.ROOT)
        if (prefix.startsWith("wifi:") || prefix.startsWith("otpauth:") || prefix.startsWith("otpauth-migration:")) return false
        var index = 0
        while (index < text.length) {
            val ch = text[index++]
            if (ch.isHighSurrogate()) {
                if (index == text.length || !text[index++].isLowSurrogate()) return false
            } else if (ch.isLowSurrogate()) return false
        }
        return text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES
    }
    fun key(result: QrDecodeEntry): String = MessageDigest.getInstance("SHA-256")
        .digest((result.symbology.name + "\u0000" + result.text).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
