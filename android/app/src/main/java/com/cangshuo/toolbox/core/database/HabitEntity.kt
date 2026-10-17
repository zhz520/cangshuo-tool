package com.cangshuo.toolbox.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "habit",
    indices = [Index(value = ["archived"]), Index(value = ["position"])],
)
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "weekdays_mask") val weekdaysMask: Int,
    @ColumnInfo(name = "archived") val archived: Boolean,
    @ColumnInfo(name = "archived_day") val archivedDay: Long?,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "created_day") val createdDay: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
