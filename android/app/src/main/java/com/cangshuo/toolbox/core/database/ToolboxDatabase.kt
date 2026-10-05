package com.cangshuo.toolbox.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FavoriteToolEntity::class, RecentToolEntity::class, CachedToolCatalogEntity::class,
    QrHistoryEntity::class, QrHistoryPreferenceEntity::class, SyncEntity::class, SyncPreferenceEntity::class,
    SyncDeviceEntity::class], version = 7, exportSchema = true)
abstract class ToolboxDatabase : RoomDatabase() {
    abstract fun favoriteToolDao(): FavoriteToolDao
    abstract fun recentToolDao(): RecentToolDao
    abstract fun cachedToolCatalogDao(): CachedToolCatalogDao
    abstract fun qrHistoryDao(): QrHistoryDao
    abstract fun syncDao(): SyncDao
}
