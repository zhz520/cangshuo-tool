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

/** Version 8 adds the local-only task list without touching existing data. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS todo_task (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "title TEXT NOT NULL, notes TEXT NOT NULL, due_date INTEGER, priority INTEGER NOT NULL, " +
                "completed INTEGER NOT NULL, completed_at INTEGER, created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_task_completed ON todo_task (completed)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_task_due_date ON todo_task (due_date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_task_created_at ON todo_task (created_at)")
    }
}

/** Version 9 adds habit definitions and daily check-ins without touching existing data. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS habit (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, description TEXT NOT NULL, weekdays_mask INTEGER NOT NULL, " +
                "archived INTEGER NOT NULL, archived_day INTEGER, position INTEGER NOT NULL, created_day INTEGER NOT NULL, " +
                "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_archived ON habit (archived)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_position ON habit (position)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS habit_entry (habit_id INTEGER NOT NULL, " +
                "date_epoch_day INTEGER NOT NULL, checked_at INTEGER NOT NULL, " +
                "PRIMARY KEY(habit_id, date_epoch_day), " +
                "FOREIGN KEY(habit_id) REFERENCES habit(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_entry_date_epoch_day ON habit_entry (date_epoch_day)")
    }
}

/** Version 10 adds local anniversary events without touching existing data. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS anniversary_event (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "title TEXT NOT NULL, notes TEXT NOT NULL, date_epoch_day INTEGER NOT NULL, " +
                "repeat_rule INTEGER NOT NULL, category INTEGER NOT NULL, pinned INTEGER NOT NULL, " +
                "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_anniversary_event_pinned ON anniversary_event (pinned)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_anniversary_event_date_epoch_day ON anniversary_event (date_epoch_day)")
    }
}

/** Version 11 adds local Pomodoro session history without touching existing data. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS pomodoro_session (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "label TEXT NOT NULL, phase INTEGER NOT NULL, planned_seconds INTEGER NOT NULL, " +
                "elapsed_seconds INTEGER NOT NULL, completed INTEGER NOT NULL, started_at INTEGER NOT NULL, " +
                "ended_at INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pomodoro_session_ended_at ON pomodoro_session (ended_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pomodoro_session_phase ON pomodoro_session (phase)")
    }
}

/** Version 12 adds the local inventory ledger without touching existing tables. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS inventory_item (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, notes TEXT NOT NULL, category INTEGER NOT NULL, location TEXT NOT NULL, " +
                "quantity INTEGER NOT NULL, unit TEXT NOT NULL, unit_price_cents INTEGER, " +
                "low_stock_threshold INTEGER NOT NULL, purchase_epoch_day INTEGER, expiry_epoch_day INTEGER, " +
                "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_item_category ON inventory_item (category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_item_expiry_epoch_day ON inventory_item (expiry_epoch_day)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_item_updated_at ON inventory_item (updated_at)")
    }
}
