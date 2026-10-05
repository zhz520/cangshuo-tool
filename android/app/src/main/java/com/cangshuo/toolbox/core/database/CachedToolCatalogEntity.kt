package com.cangshuo.toolbox.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One atomic snapshot, including the valid empty catalog. Never stores user tool inputs. */
@Entity(tableName = "cached_tool_catalog")
data class CachedToolCatalogEntity(
    @PrimaryKey val singleton: Int = 1,
    @ColumnInfo(name = "format_version") val formatVersion: Int,
    @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "written_at") val writtenAt: Long,
    @ColumnInfo(name = "payload_sha256") val payloadSha256: String,
    val payload: String,
)
