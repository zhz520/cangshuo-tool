"""Check actual Room sync migrations, schema and version-conditional acknowledgements in host SQLite."""
import json
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DB = ROOT / "android/app/src/main/java/com/cangshuo/toolbox/core/database"
SCHEMA = ROOT / "android/app/schemas/com.cangshuo.toolbox.core.database.ToolboxDatabase"


def main():
    source = (DB / "ToolboxDatabaseMigrations.kt").read_text(encoding="utf-8")
    section = source.split("val MIGRATION_4_5 =",1)[1].split("val MIGRATION_5_6 =",1)[0]
    sql = [json.loads(s) for s in re.findall(r'db\.execSQL\(("(?:[^"\\]|\\.)*")\)',section)]
    assert len(sql)==3
    checks=0
    with sqlite3.connect(":memory:") as db:
        before=json.loads((SCHEMA/"4.json").read_text(encoding="utf-8"))["database"]
        after=json.loads((SCHEMA/"5.json").read_text(encoding="utf-8"))["database"]
        for e in before["entities"]:
            db.execute(e["createSql"].replace("${TABLE_NAME}",e["tableName"]))
        db.execute("INSERT INTO favorite_tool VALUES ('calculator',123)")
        db.execute("INSERT INTO recent_tool VALUES ('qr',456,2)")
        for s in sql: db.execute(s)
        assert db.execute("SELECT * FROM favorite_tool").fetchall()==[("calculator",123)]
        assert db.execute("SELECT * FROM recent_tool").fetchall()==[("qr",456,2)]
        checks+=2
        for e in after["entities"]:
            actual={r[1]:(r[2],bool(r[3]),bool(r[5])) for r in db.execute(f'PRAGMA table_info("{e["tableName"]}")')}
            expected={f["columnName"]:(f["affinity"],f.get("notNull",False),f["columnName"] in e["primaryKey"]["columnNames"]) for f in e["fields"]}
            assert actual==expected,e["tableName"]
            checks+=1
        db.execute("INSERT INTO sync_entry VALUES (1,'FAVORITE','calculator',1000,'a',0,NULL,NULL,NULL,1)")
        db.execute("INSERT INTO sync_entry VALUES (2,'FAVORITE','calculator',1000,'a',0,NULL,NULL,NULL,1)")
        db.execute("UPDATE sync_entry SET updated_at=1001,deleted=1 WHERE user_id=1")
        dao=(DB/"SyncDao.kt").read_text(encoding="utf-8")
        ack=json.loads(re.search(r'@Query\(("UPDATE sync_entry.*?")\)',dao).group(1))
        db.execute(ack,dict(user=1,type="FAVORITE",key="calculator",at=1000,device="a"))
        assert db.execute("SELECT dirty FROM sync_entry WHERE user_id=1").fetchone()==(1,)
        checks+=1
        db.execute(ack,dict(user=1,type="FAVORITE",key="calculator",at=1001,device="a"))
        assert db.execute("SELECT dirty FROM sync_entry ORDER BY user_id").fetchall()==[(0,),(1,)]
        checks+=1
        migration6=source.split("val MIGRATION_5_6 =",1)[1].split("val MIGRATION_6_7 =",1)[0]
        sql6=[json.loads(s) for s in re.findall(r'db\.execSQL\(("(?:[^"\\]|\\.)*")\)',migration6)]
        db.execute("INSERT INTO sync_preference VALUES (1,1,1,'cursor')")
        for statement in sql6: db.execute(statement)
        assert db.execute("SELECT * FROM sync_preference").fetchone()==(1,1,1,'cursor',0,0)
        checks+=1
        expected6=json.loads((SCHEMA/"6.json").read_text(encoding="utf-8"))["database"]
        for e in expected6["entities"]:
            actual={r[1]:(r[2],bool(r[3]),bool(r[5])) for r in db.execute(f'PRAGMA table_info("{e["tableName"]}")')}
            expected={f["columnName"]:(f["affinity"],f.get("notNull",False),f["columnName"] in e["primaryKey"]["columnNames"]) for f in e["fields"]}
            assert actual==expected,e["tableName"]
            checks+=1
        migration7=source.split("val MIGRATION_6_7 =",1)[1]
        sql7=[json.loads(s) for s in re.findall(r'db\.execSQL\(("(?:[^"\\]|\\.)*")\)',migration7)]
        for statement in sql7: db.execute(statement)
        assert db.execute("SELECT * FROM sync_preference").fetchone()==(1,1,1,'cursor',0,0,0,0)
        checks+=1
        expected7=json.loads((SCHEMA/"7.json").read_text(encoding="utf-8"))["database"]
        for e in expected7["entities"]:
            actual={r[1]:(r[2],bool(r[3]),bool(r[5])) for r in db.execute(f'PRAGMA table_info("{e["tableName"]}")')}
            expected={f["columnName"]:(f["affinity"],f.get("notNull",False),f["columnName"] in e["primaryKey"]["columnNames"]) for f in e["fields"]}
            assert actual==expected,e["tableName"]
            checks+=1
    print(f"Sync SQLite: {checks}/{checks} checks passed (no device claims).")


if __name__=="__main__": main()
