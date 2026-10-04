package com.cangshuo.toolbox.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_tool")
data class FavoriteToolEntity(
    @PrimaryKey @ColumnInfo(name = "tool_code") val toolCode: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)
