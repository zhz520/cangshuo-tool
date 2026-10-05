package com.cangshuo.toolbox.feature.webtools.data

import com.cangshuo.toolbox.core.database.CachedToolCatalogDao
import com.cangshuo.toolbox.core.database.CachedToolCatalogEntity
import com.cangshuo.toolbox.core.network.CatalogSnapshotCodec
import com.cangshuo.toolbox.core.network.model.ToolCatalogDto
import com.cangshuo.toolbox.feature.webtools.domain.CatalogCacheException
import com.cangshuo.toolbox.feature.webtools.domain.WebToolCatalogCache
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class RoomWebToolCatalogCache(
    private val dao: CachedToolCatalogDao,
    baseUrl: String,
    private val now: () -> Long = System::currentTimeMillis,
) : WebToolCatalogCache {
    // Isolate debug/release/changed API origins without storing an address or credentials.
    private val sourceKey = CatalogSnapshotCodec.digest(URI(baseUrl).toASCIIString().trimEnd('/'))

    override suspend fun read(): List<ToolCatalogDto>? = withContext(Dispatchers.IO) {
        try {
            val snapshot = dao.read(sourceKey) ?: return@withContext null
            ensureActive()
            if (snapshot.singleton != 1 || snapshot.formatVersion != CatalogSnapshotCodec.VERSION ||
                snapshot.sourceKey != sourceKey || snapshot.writtenAt < 0 ||
                !Regex("[0-9a-f]{64}").matches(snapshot.payloadSha256)) return@withContext null
            val records = CatalogSnapshotCodec.decode(snapshot.payload) { ensureActive() }
            if (CatalogSnapshotCodec.digest(snapshot.payload) != snapshot.payloadSha256) return@withContext null
            ensureActive()
            records
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw CatalogCacheException()
        }
    }

    override suspend fun write(records: List<ToolCatalogDto>): Unit = withContext(Dispatchers.IO) {
        try {
            val payload = CatalogSnapshotCodec.encode(records) { ensureActive() }
            val timestamp = now()
            if (timestamp < 0) throw CatalogCacheException()
            ensureActive()
            dao.write(CachedToolCatalogEntity(
                formatVersion = CatalogSnapshotCodec.VERSION,
                sourceKey = sourceKey,
                writtenAt = timestamp,
                payloadSha256 = CatalogSnapshotCodec.digest(payload),
                payload = payload,
            ))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw CatalogCacheException()
        }
    }
}
