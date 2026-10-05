"""Exercise administrator tool CRUD against the local stack; never print credentials or tokens.

Requires ADMIN_CHECK_USERNAME and ADMIN_CHECK_PASSWORD for a bootstrapped administrator. The script
creates one temporary catalog entry, drives it through enable/disable/maintenance/update/delete and
removes the temporary row at the end; audit rows are deleted as well.
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
    assert condition, "Administrator tool check failed (sensitive response omitted)"
    CHECKS += 1


def sql(statement):
    result = subprocess.run([
        "docker", "compose", "--env-file", "../.env", "-f", "docker-compose.yml", "-f",
        "docker-compose.local.yml", "exec", "-T", "mysql", "sh", "-c",
        'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 '
        '-u "$MYSQL_USER" --database="$MYSQL_DATABASE" --batch --skip-column-names --execute="$1"',
        "sh", statement], cwd=ROOT / "deploy", capture_output=True, text=True, check=True)
    return result.stdout.strip()


def catalog_codes():
    return {record["code"] for record in request("tools?pageSize=100")[1]["data"]["records"]}


def main():
    if not (ADMIN_USERNAME and ADMIN_PASSWORD):
        print("Set ADMIN_CHECK_USERNAME and ADMIN_CHECK_PASSWORD to run the administrator tool checks")
        return
    status, body = request("admin/auth/login",
                           {"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD}, method="POST")
    if status != 200 or body["code"] != 0:
        raise AssertionError(f"Administrator login failed (status={status}, code={body.get('code')})")
    token = body["data"]["accessToken"]
    code = "tmp_check_" + secrets.token_hex(4)
    payload = {"name": "校验工具", "description": "自动化校验创建", "categoryCode": "DEV", "icon": "tools",
               "keywords": ["校验", "check"], "mode": "LOCAL", "requiresLogin": False, "status": "ENABLED",
               "version": 1, "sortOrder": 990, "featured": False, "configJson": "{\"source\":\"check\"}"}
    try:
        check(request("admin/tools")[0] == 401)
        status, categories = request("admin/tools/categories", token=token)
        check(status == 200 and len(categories["data"]) > 0)
        status, page = request("admin/tools?pageSize=100", token=token)
        if not (status == 200 and page.get("data") and page["data"].get("total", 0) >= 18):
            raise AssertionError(f"Admin tool list failed (status={status}, code={page.get('code')})")
        check(page["data"]["records"][0]["code"])
        status, body = request("admin/tools", {**payload, "code": code, "categoryCode": "NOPE"},
                               token=token, method="POST")
        check(status == 404 and body["code"] == 30005)
        status, created = request("admin/tools", {**payload, "code": code}, token=token, method="POST")
        check(status == 201 and created["data"]["code"] == code and created["data"]["categoryName"])
        status, body = request("admin/tools", {**payload, "code": code}, token=token, method="POST")
        check(status == 409 and body["code"] == 30004)
        check(code in catalog_codes())
        status, detail = request(f"admin/tools/{code}", token=token)
        check(status == 200 and detail["data"]["name"] == payload["name"] and detail["data"]["keywords"] == ["校验", "check"])
        status, updated = request(f"admin/tools/{code}", {**payload, "name": "校验工具改名", "sortOrder": 991},
                                  token=token, method="PUT")
        check(status == 200 and updated["data"]["name"] == "校验工具改名" and updated["data"]["sortOrder"] == 991)
        status, disabled = request(f"admin/tools/{code}/status", {"status": "DISABLED"}, token=token, method="PATCH")
        check(status == 200 and disabled["data"]["status"] == "DISABLED")
        check(code not in catalog_codes())
        status, maintenance = request(f"admin/tools/{code}/status", {"status": "MAINTENANCE"}, token=token, method="PATCH")
        check(status == 200 and maintenance["data"]["status"] == "MAINTENANCE")
        status, body = request(f"admin/tools/{code}/status", {"status": "RUNNING"}, token=token, method="PATCH")
        check(status == 400 and body["code"] == 10001)
        status, body = request(f"admin/tools/{code}", {**payload, "configJson": "[1]"}, token=token, method="PUT")
        check(status == 400 and body["code"] == 10001)
        check(request("admin/tools/BAD%20CODE", token=token)[0] == 400)
        status, _ = request(f"admin/tools/{code}", token=token, method="DELETE")
        check(status == 200)
        check(request(f"admin/tools/{code}", token=token)[0] == 404)
        check(code not in catalog_codes())
        check(sql(f"SELECT COUNT(*) FROM tool_definition WHERE tool_code='{code}' AND deleted_at IS NOT NULL") == "1")
        check(int(sql("SELECT COUNT(*) FROM admin_operation_log WHERE module='tool' AND result='SUCCESS'")) >= 4)
    finally:
        print("cleanup temporary tool row:", sql(f"DELETE FROM tool_definition WHERE tool_code='{code}'"))
        print("cleanup temporary tool audit rows:", sql("DELETE FROM admin_operation_log WHERE module='tool'"))
    print(f"{CHECKS} administrator tool checks passed")


if __name__ == "__main__":
    main()
