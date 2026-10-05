"""Exercise the administrator audit read API against the local stack; never print credentials or tokens.

Requires ADMIN_CHECK_USERNAME/ADMIN_CHECK_PASSWORD. Creates and deletes one temporary category so the
audit trail gains deterministic CREATE and DELETE rows, then validates filters and pagination.
"""
import json
import os
import secrets
import subprocess
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADMIN_USERNAME = os.environ.get("ADMIN_CHECK_USERNAME", "")
ADMIN_PASSWORD = os.environ.get("ADMIN_CHECK_PASSWORD", "")
CHECKS = 0


def request(path, payload=None, token=None, port=8081, method=None):
    headers = {"Accept": "application/json"}
    if payload is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(
        f"http://127.0.0.1:{port}/api/v1/{path}",
        data=None if payload is None else json.dumps(payload).encode(), headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = json.load(response)
        assert body["traceId"] == response.headers["X-Trace-Id"]
        return response.status, body


def check(condition):
    global CHECKS
    assert condition, "Administrator log check failed (sensitive response omitted)"
    CHECKS += 1


def sql(statement):
    result = subprocess.run([
        "docker", "compose", "--env-file", "../.env", "-f", "docker-compose.yml", "-f",
        "docker-compose.local.yml", "exec", "-T", "mysql", "sh", "-c",
        'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 '
        '-u "$MYSQL_USER" --database="$MYSQL_DATABASE" --batch --skip-column-names --execute="$1"',
        "sh", statement], cwd=ROOT / "deploy", capture_output=True, text=True, check=True)
    return result.stdout.strip()


def main():
    if not (ADMIN_USERNAME and ADMIN_PASSWORD):
        print("Set ADMIN_CHECK_USERNAME and ADMIN_CHECK_PASSWORD to run the administrator log checks")
        return
    status, body = request("admin/auth/login",
                           {"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD}, method="POST")
    if status != 200 or body["code"] != 0:
        raise AssertionError(f"Administrator login failed (status={status}, code={body.get('code')})")
    token = body["data"]["accessToken"]
    category = "TMPLOG_" + secrets.token_hex(3).upper()
    try:
        check(request("admin/logs")[0] == 401)
        status, created = request("admin/categories",
                                  {"code": category, "name": "日志校验", "description": "", "icon": None,
                                   "sortOrder": 997, "enabled": True}, token=token, method="POST")
        check(status == 201)
        status, page = request("admin/logs?module=CATEGORY&pageSize=50", token=token)
        check(status == 200 and page["data"]["total"] >= 1)
        newest = page["data"]["records"][0]
        check(newest["module"].upper() == "CATEGORY" and newest["operation"] == "CREATE"
              and newest["result"] == "SUCCESS" and newest["requestMethod"] == "POST")
        check(newest["adminUsername"] == ADMIN_USERNAME.strip().lower())
        check(newest["requestUri"] == "/api/v1/admin/categories" and newest["ip"])
        status, filtered = request("admin/logs?module=category&result=success&pageSize=50", token=token)
        check(status == 200 and all(item["module"].upper() == "CATEGORY" and item["result"] == "SUCCESS"
                                    for item in filtered["data"]["records"]))
        status, keyworded = request("admin/logs?keyword=admin/categories&pageSize=50", token=token)
        check(status == 200 and all("admin/categories" in item["requestUri"] for item in keyworded["data"]["records"]))
        status, empty = request("admin/logs?page=999&pageSize=50", token=token)
        check(status == 200 and empty["data"]["records"] == [] and empty["data"]["total"] >= 1)
        status, body = request("admin/logs?module=bad%20module", token=token)
        check(status == 400 and body["code"] == 10001)
        status, body = request("admin/logs?pageSize=0", token=token)
        check(status == 400 and body["code"] == 10001)
        status, _ = request(f"admin/categories/{category}", token=token, method="DELETE")
        check(status == 200)
        status, page = request("admin/logs?module=CATEGORY&pageSize=50", token=token)
        check(status == 200 and page["data"]["records"][0]["operation"] == "DELETE"
              and page["data"]["records"][0]["requestMethod"] == "DELETE")
    finally:
        print("cleanup temp category:", sql(f"DELETE FROM tool_category WHERE code='{category}'"))
        print("cleanup category audit rows:", sql("DELETE FROM admin_operation_log WHERE module='category'"))
    print(f"{CHECKS} administrator log checks passed")


if __name__ == "__main__":
    main()
