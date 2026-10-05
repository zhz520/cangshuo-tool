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

/** Version 3 adds a cache snapshot without altering favorites or recent history. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS cached_tool_catalog " +
                "(singleton INTEGER NOT NULL, format_version INTEGER NOT NULL, source_key TEXT NOT NULL, " +
                "written_at INTEGER NOT NULL, payload_sha256 TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(singleton))",
        )
    }
}

/** Version 4 adds opt-in scan history; all previous tables are retained. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS qr_scan_history (entry_key TEXT NOT NULL, payload TEXT NOT NULL, symbology TEXT NOT NULL, scanned_at INTEGER NOT NULL, PRIMARY KEY(entry_key))")
        db.execSQL("CREATE TABLE IF NOT EXISTS qr_history_preference (singleton INTEGER NOT NULL, enabled INTEGER NOT NULL, PRIMARY KEY(singleton))")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_entry (user_id INTEGER NOT NULL,entity_type TEXT NOT NULL,entity_key TEXT NOT NULL,updated_at INTEGER NOT NULL,device_id TEXT NOT NULL,deleted INTEGER NOT NULL,last_used_at INTEGER,use_count INTEGER,value TEXT,dirty INTEGER NOT NULL,PRIMARY KEY(user_id,entity_type,entity_key))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_preference (user_id INTEGER NOT NULL,enabled INTEGER NOT NULL,enrolled INTEGER NOT NULL,cursor TEXT NOT NULL,PRIMARY KEY(user_id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_device (singleton INTEGER NOT NULL,device_id TEXT NOT NULL,PRIMARY KEY(singleton))")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sync_preference ADD COLUMN recent_enabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sync_preference ADD COLUMN recent_enrolled INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sync_preference ADD COLUMN settings_enabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sync_preference ADD COLUMN settings_enrolled INTEGER NOT NULL DEFAULT 0")
    }
}
