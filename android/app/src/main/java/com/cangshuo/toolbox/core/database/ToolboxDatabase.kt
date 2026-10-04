package com.cangshuo.toolbox.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FavoriteToolEntity::class, RecentToolEntity::class], version = 2, exportSchema = true)
abstract class ToolboxDatabase : RoomDatabase() {
    abstract fun favoriteToolDao(): FavoriteToolDao
    abstract fun recentToolDao(): RecentToolDao
}
