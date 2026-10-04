package com.cangshuo.toolbox.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_tool")
data class RecentToolEntity(
    @PrimaryKey @ColumnInfo(name = "tool_code") val toolCode: String,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long,
    @ColumnInfo(name = "use_count") val useCount: Int,
)
