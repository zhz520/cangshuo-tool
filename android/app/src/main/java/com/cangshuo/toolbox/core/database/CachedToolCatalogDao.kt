package com.cangshuo.toolbox.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CachedToolCatalogDao {
    // Bound at SQLite, before Android allocates a CursorWindow/String for the payload.
    @Query("SELECT singleton, format_version, source_key, written_at, substr(payload_sha256, 1, 65) AS payload_sha256, " +
        "CAST(substr(CAST(payload AS BLOB), 1, 1000001) AS TEXT) AS payload FROM cached_tool_catalog " +
        "WHERE singleton = 1 AND format_version = 1 AND source_key = :sourceKey " +
        "AND length(CAST(payload AS BLOB)) <= 1000000")
    suspend fun read(sourceKey: String): CachedToolCatalogEntity?

    @Upsert
    suspend fun write(snapshot: CachedToolCatalogEntity)
}
