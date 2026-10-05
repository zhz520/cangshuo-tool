"""Check the actual Room 3->4 SQL, data preservation and bounded history query.

Uses only an in-memory host SQLite database; this is not Android device QA.
"""
import json
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DB = ROOT / "android/app/src/main/java/com/cangshuo/toolbox/core/database"
SCHEMA = ROOT / "android/app/schemas/com.cangshuo.toolbox.core.database.ToolboxDatabase"


def main():
    source = (DB / "ToolboxDatabaseMigrations.kt").read_text(encoding="utf-8")
    section = source.split("val MIGRATION_3_4 =", 1)[1].split("val MIGRATION_4_5 =",1)[0]
    statements = [json.loads(value) for value in re.findall(r'db\.execSQL\(("(?:[^"\\]|\\.)*")\)', section)]
    assert len(statements) == 2
    query_source = (DB / "QrHistoryDao.kt").read_text(encoding="utf-8")
    query = json.loads(re.search(r'@Query\(("(?:[^"\\]|\\.)*")\)\s*fun observeEntries', query_source).group(1))
    expected = json.loads((SCHEMA / "4.json").read_text(encoding="utf-8"))["database"]
    checks = 0
    with sqlite3.connect(":memory:") as db:
        old = json.loads((SCHEMA / "3.json").read_text(encoding="utf-8"))["database"]
        for entity in old["entities"]:
            db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        db.execute("INSERT INTO favorite_tool VALUES ('qr', 100)")
        db.execute("INSERT INTO recent_tool VALUES ('qr', 200, 3)")
        db.execute("INSERT INTO cached_tool_catalog VALUES (1, 1, ?, 300, ?, '{}')", ("a" * 64, "b" * 64))
        old_cache = db.execute("SELECT * FROM cached_tool_catalog").fetchall()
        for sql in statements:
            db.execute(sql)
        assert db.execute("SELECT * FROM favorite_tool").fetchall() == [("qr", 100)]
        assert db.execute("SELECT * FROM recent_tool").fetchall() == [("qr", 200, 3)]
        assert db.execute("SELECT * FROM cached_tool_catalog").fetchall() == old_cache
        checks += 3
        for entity in expected["entities"]:
            actual = {r[1]: (r[2], bool(r[3]), bool(r[5])) for r in db.execute(f'PRAGMA table_info("{entity["tableName"]}")')}
            columns = {f["columnName"]: (f["affinity"], f["notNull"], f["columnName"] in entity["primaryKey"]["columnNames"]) for f in entity["fields"]}
            assert actual == columns
            checks += 1
        assert db.execute("SELECT enabled FROM qr_history_preference WHERE singleton = 1").fetchone() is None
        checks += 1
        for index in range(105):
            db.execute("INSERT INTO qr_scan_history VALUES (?, ?, 'QR_CODE', ?)", (f"{index:064x}", f"record-{index}", index + 1))
        rows = db.execute(query).fetchall()
        assert len(rows) == 100 and rows[0][1] == "record-104" and rows[-1][1] == "record-5"
        checks += 1
        db.execute("DELETE FROM qr_scan_history")
        for index, payload in enumerate(("x" * 16000, "x" * 16001, "中" * 5334, "", "ok")):
            db.execute("INSERT INTO qr_scan_history VALUES (?, ?, 'QR_CODE', ?)", (f"{index:064x}", payload, index + 1))
        assert [row[1] for row in db.execute(query)] == ["ok", "x" * 16000]
        checks += 1
        db.execute("INSERT INTO qr_history_preference VALUES (1, 1)")
        db.execute("DELETE FROM qr_scan_history")
        assert db.execute("SELECT enabled FROM qr_history_preference").fetchone() == (1,)
        checks += 1
    print(json.dumps({"qr_history_sql_checks": checks, "migration": "3->4", "result": "passed"}))


if __name__ == "__main__":
    main()
