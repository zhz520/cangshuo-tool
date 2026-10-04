package com.cangshuo.toolbox.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 2 adds local recent tools; existing favorites are preserved. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS recent_tool " +
                "(tool_code TEXT NOT NULL, last_used_at INTEGER NOT NULL, use_count INTEGER NOT NULL, " +
                "PRIMARY KEY(tool_code))",
        )
    }
}
