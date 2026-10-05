package com.cangshuo.toolbox.feature.qr.data

import com.cangshuo.toolbox.core.database.QrHistoryDao
import com.cangshuo.toolbox.core.database.QrHistoryEntity
import com.cangshuo.toolbox.core.database.QrHistoryPreferenceEntity
import com.cangshuo.toolbox.feature.qr.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoomQrHistoryRepository(
    private val dao: QrHistoryDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : QrHistoryRepository {
    private val writes = Mutex()
    override fun observe() = combine(dao.observeEnabled(), dao.observeEntries()) { enabled, entries ->
        QrHistoryState(enabled == true, entries.mapNotNull { row ->
            val format = QrSymbology.entries.firstOrNull { it.name == row.symbology } ?: return@mapNotNull null
            val result = QrDecodeEntry(row.payload, format)
            if (!QrHistoryPolicy.mayStore(result) || QrHistoryPolicy.key(result) != row.key || row.scannedAt <= 0) null
            else QrHistoryEntry(row.key, result, row.scannedAt)
        })
    }.catch { error ->
        if (error is CancellationException) throw error
        throw QrHistoryException()
    }.flowOn(dispatcher)

    override suspend fun setEnabled(enabled: Boolean) = write { dao.setPreference(QrHistoryPreferenceEntity(enabled = enabled)) }
    override suspend fun record(entries: List<QrDecodeEntry>): QrHistoryRecordResult = write {
        if (dao.isEnabled() != true) return@write QrHistoryRecordResult.DISABLED
        if (entries.size > QrHistoryPolicy.MAX_BATCH) return@write QrHistoryRecordResult.SKIPPED
        val at = clock()
        if (at <= 0) return@write QrHistoryRecordResult.SKIPPED
        val rows = entries.filter(QrHistoryPolicy::mayStore).distinctBy(QrHistoryPolicy::key).map {
            QrHistoryEntity(QrHistoryPolicy.key(it), it.text, it.symbology.name, at)
        }
        if (rows.isEmpty()) return@write QrHistoryRecordResult.SKIPPED
        if (dao.recordIfEnabled(rows)) QrHistoryRecordResult.SAVED else QrHistoryRecordResult.DISABLED
    }
    override suspend fun remove(key: String) = write {
        if (!key.matches(Regex("[0-9a-f]{64}"))) throw QrHistoryException()
        dao.remove(key)
    }
    override suspend fun clear() = write { dao.clear() }

    private suspend fun <T> write(action: suspend () -> T): T = writes.withLock {
        withContext(dispatcher) {
            try { action() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw QrHistoryException() }
        }
    }
}
