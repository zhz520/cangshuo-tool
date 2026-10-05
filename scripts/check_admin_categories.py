"""Exercise administrator category CRUD against the local stack; never print credentials or tokens.

Requires ADMIN_CHECK_USERNAME and ADMIN_CHECK_PASSWORD for a bootstrapped administrator. Creates one
temporary category and one temporary tool to prove the in-use guard and the public-catalog linkage,
then removes both rows and their audit entries.
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
    assert condition, "Administrator category check failed (sensitive response omitted)"
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
        print("Set ADMIN_CHECK_USERNAME and ADMIN_CHECK_PASSWORD to run the administrator category checks")
        return
    status, body = request("admin/auth/login",
                           {"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD}, method="POST")
    if status != 200 or body["code"] != 0:
        raise AssertionError(f"Administrator login failed (status={status}, code={body.get('code')})")
    token = body["data"]["accessToken"]
    category = "TMPCAT_" + secrets.token_hex(3).upper()
    tool = "tmp_cat_tool_" + secrets.token_hex(3)
    payload = {"name": "临时分类", "description": "自动化校验", "icon": "tools", "sortOrder": 995,
               "enabled": True}
    try:
        check(request("admin/categories")[0] == 401)
        status, listed = request("admin/categories", token=token)
        check(status == 200 and len(listed["data"]) >= 10)
        check(all("toolCount" in item and "enabled" in item for item in listed["data"]))
        status, created = request("admin/categories", {**payload, "code": category}, token=token, method="POST")
        check(status == 201 and created["data"]["code"] == category and created["data"]["toolCount"] == 0)
        status, body = request("admin/categories", {**payload, "code": category}, token=token, method="POST")
        check(status == 409 and body["code"] == 30006)
        status, body = request("admin/categories", {**payload, "code": "1BAD"}, token=token, method="POST")
        check(status == 400 and body["code"] == 10001)
        status, updated = request(f"admin/categories/{category}",
                                  {**payload, "name": "临时分类改名", "sortOrder": 996}, token=token, method="PUT")
        check(status == 200 and updated["data"]["name"] == "临时分类改名" and updated["data"]["sortOrder"] == 996)
        status, body = request(f"admin/categories/{category}/status", {"enabled": True}, token=token, method="PATCH")
        check(status == 200 and body["data"]["enabled"] is True)
        status, body = request(f"admin/categories/{category}", {**payload, "name": "x", "sortOrder": -1},
                               token=token, method="PUT")
        check(status == 400 and body["code"] == 10001)

        tool_payload = {"name": "校验工具", "description": "分类联动校验", "categoryCode": category,
                        "icon": "tools", "keywords": ["check"], "mode": "LOCAL", "requiresLogin": False,
                        "status": "ENABLED", "version": 1, "sortOrder": 998, "featured": False,
                        "configJson": "{}"}
        status, _ = request("admin/tools", {**tool_payload, "code": tool}, token=token, method="POST")
        check(status == 201)
        check(tool in catalog_codes())
        status, body = request(f"admin/categories/{category}", token=token, method="DELETE")
        check(status == 409 and body["code"] == 30007)
        status, _ = request(f"admin/categories/{category}/status", {"enabled": False}, token=token, method="PATCH")
        check(status == 200)
        check(tool not in catalog_codes())
        status, _ = request(f"admin/categories/{category}/status", {"enabled": True}, token=token, method="PATCH")
        check(status == 200)
        check(tool in catalog_codes())
        status, _ = request(f"admin/tools/{tool}", token=token, method="DELETE")
        check(status == 200)
        status, _ = request(f"admin/categories/{category}", token=token, method="DELETE")
        check(status == 200)
        status, listed = request("admin/categories", token=token)
        check(category not in {item["code"] for item in listed["data"]})
        check(sql(f"SELECT COUNT(*) FROM tool_category WHERE code='{category}' AND deleted_at IS NOT NULL") == "1")
        check(status == 200 and request(f"admin/categories/{category}", token=token, method="DELETE")[0] == 404)
        check(int(sql("SELECT COUNT(*) FROM admin_operation_log WHERE module='category' AND result='SUCCESS'")) >= 4)
    finally:
        print("cleanup temp tool row:", sql(f"DELETE FROM tool_definition WHERE tool_code='{tool}'"))
        print("cleanup temp category row:", sql(f"DELETE FROM tool_category WHERE code='{category}'"))
        print("cleanup tool audit rows:", sql("DELETE FROM admin_operation_log WHERE module='tool'"))
        print("cleanup category audit rows:", sql("DELETE FROM admin_operation_log WHERE module='category'"))
    print(f"{CHECKS} administrator category checks passed")


if __name__ == "__main__":
    main()
