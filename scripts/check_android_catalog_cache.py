"""Verify the actual Kotlin Room migrations and bounded DAO query with host SQLite.

No files/databases are created. This supplements Room's KSP schema check; it does
not claim to execute Android Room or a device CursorWindow.
"""

import hashlib
import json
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / "android/app/schemas/com.cangshuo.toolbox.core.database.ToolboxDatabase"
MIGRATIONS = ROOT / "android/app/src/main/java/com/cangshuo/toolbox/core/database/ToolboxDatabaseMigrations.kt"
DAO = ROOT / "android/app/src/main/java/com/cangshuo/toolbox/core/database/CachedToolCatalogDao.kt"


def literals(expression):
    pattern = r'"(?:[^"\\]|\\.)*"'
    remaining = re.sub(pattern, "", expression)
    if not re.fullmatch(r"[\s+]*,?\s*", remaining):
        raise AssertionError("Unsupported Kotlin SQL expression; update this validator explicitly")
    return "".join(json.loads(item) for item in re.findall(pattern, expression))


def migration(version):
    source = MIGRATIONS.read_text(encoding="utf-8")
    expression = re.search(rf"val MIGRATION_{version}_{version + 1}\s*=.*?db\.execSQL\((.*?)\n\s*\)", source, re.S)
    assert expression, "Migration missing"
    return literals(expression.group(1))


def main():
    expected = json.loads((SCHEMAS / "3.json").read_text(encoding="utf-8"))["database"]
    query_source = DAO.read_text(encoding="utf-8")
    expression = re.search(r"@Query\((.*?)\)\s*suspend fun read", query_source, re.S)
    assert expression, "Cache read query missing"
    query = literals(expression.group(1))
    checks = 0
    source_key = "a" * 64
    for starting_version in (1, 2):
        schema = json.loads((SCHEMAS / f"{starting_version}.json").read_text(encoding="utf-8"))["database"]
        with sqlite3.connect(":memory:", isolation_level=None) as db:
            for entity in schema["entities"]:
                db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
            db.execute("INSERT INTO favorite_tool VALUES ('calculator', 123)")
            if starting_version == 2:
                db.execute("INSERT INTO recent_tool VALUES ('qr', 456, 2)")
            for version in range(starting_version, 3):
                db.execute(migration(version))
            assert db.execute("SELECT * FROM favorite_tool").fetchall() == [("calculator", 123)]
            assert db.execute("SELECT * FROM recent_tool").fetchall() == ([] if starting_version == 1 else [("qr", 456, 2)])
            checks += 2
            for entity in expected["entities"]:
                actual = {row[1]: (row[2], bool(row[3]), bool(row[5])) for row in db.execute(f'PRAGMA table_info("{entity["tableName"]}")')}
                columns = {field["columnName"]: (field["affinity"], field["notNull"], field["columnName"] in entity["primaryKey"]["columnNames"]) for field in entity["fields"]}
                assert actual == columns, f"Migration/schema mismatch: {entity['tableName']}"
                checks += 1

            def save(payload, version=1, key=source_key):
                checksum = hashlib.sha256(payload.encode("utf-8")).hexdigest()
                db.execute("INSERT OR REPLACE INTO cached_tool_catalog VALUES (1, ?, ?, 1000, ?, ?)", (version, key, checksum, payload))

            empty = '{"records":[]}'
            save(empty)
            assert db.execute(query, {"sourceKey": source_key}).fetchone()[-1] == empty
            assert db.execute(query, {"sourceKey": "b" * 64}).fetchone() is None
            checks += 2
            save(empty, version=2)
            assert db.execute(query, {"sourceKey": source_key}).fetchone() is None
            checks += 1
            exact = empty + " " * (1_000_000 - len(empty))
            save(exact)
            assert len(db.execute(query, {"sourceKey": source_key}).fetchone()[-1].encode("utf-8")) == 1_000_000
            save(exact + " ")
            assert db.execute(query, {"sourceKey": source_key}).fetchone() is None
            checks += 2
            save("中" * 333_334)
            assert db.execute(query, {"sourceKey": source_key}).fetchone() is None
            checks += 1
            save(empty)
            db.execute("BEGIN")
            save("changed")
            db.execute("ROLLBACK")
            assert db.execute(query, {"sourceKey": source_key}).fetchone()[-1] == empty
            checks += 1
    print(json.dumps({"sqlite_migration_checks": checks, "starting_versions": [1, 2], "target_version": 3, "result": "passed"}))


if __name__ == "__main__":
    main()
