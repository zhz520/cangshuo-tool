package com.cangshuo.toolbox.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qr_scan_history")
data class QrHistoryEntity(
    @PrimaryKey @ColumnInfo(name = "entry_key") val key: String,
    val payload: String,
    val symbology: String,
    @ColumnInfo(name = "scanned_at") val scannedAt: Long,
)

@Entity(tableName = "qr_history_preference")
data class QrHistoryPreferenceEntity(@PrimaryKey val singleton: Int = 1, val enabled: Boolean)
