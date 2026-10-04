package com.cangshuo.toolbox.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FavoriteToolEntity::class], version = 1, exportSchema = true)
abstract class ToolboxDatabase : RoomDatabase() {
    abstract fun favoriteToolDao(): FavoriteToolDao
}
