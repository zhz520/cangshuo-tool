package com.cangshuo.toolbox.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration

/** App-scoped Room provider shared by Compose screens and background reminder receivers. */
object ToolboxDatabaseProvider {
    @Volatile
    private var instance: ToolboxDatabase? = null

    private val migrations = arrayOf<Migration>(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
    )

    fun get(context: Context): ToolboxDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            ToolboxDatabase::class.java,
            "toolbox.db",
        ).addMigrations(*migrations).build().also { instance = it }
    }
}
