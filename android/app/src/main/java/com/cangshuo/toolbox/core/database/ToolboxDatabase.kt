package com.cangshuo.toolbox.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FavoriteToolEntity::class, RecentToolEntity::class, CachedToolCatalogEntity::class,
    QrHistoryEntity::class, QrHistoryPreferenceEntity::class, SyncEntity::class, SyncPreferenceEntity::class,
    SyncDeviceEntity::class, TodoTaskEntity::class, HabitEntity::class, HabitEntryEntity::class,
    AnniversaryEventEntity::class], version = 10, exportSchema = true)
abstract class ToolboxDatabase : RoomDatabase() {
    abstract fun favoriteToolDao(): FavoriteToolDao
    abstract fun recentToolDao(): RecentToolDao
    abstract fun cachedToolCatalogDao(): CachedToolCatalogDao
    abstract fun qrHistoryDao(): QrHistoryDao
    abstract fun syncDao(): SyncDao
    abstract fun todoTaskDao(): TodoTaskDao
    abstract fun habitDao(): HabitDao
    abstract fun anniversaryEventDao(): AnniversaryEventDao
}
