package com.cangshuo.toolbox.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "period_cycle",
    indices = [
        Index(value = ["start_epoch_day"], unique = true),
        Index(value = ["updated_at"]),
    ],
)
data class PeriodCycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_epoch_day") val startEpochDay: Long,
    @ColumnInfo(name = "end_epoch_day") val endEpochDay: Long?,
    @ColumnInfo(name = "flow") val flow: Int,
    @ColumnInfo(name = "symptoms_mask") val symptomsMask: Int,
    @ColumnInfo(name = "mood") val mood: Int?,
    @ColumnInfo(name = "notes") val notes: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
