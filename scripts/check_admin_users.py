"""Exercise administrator user management against the local stack; never print credentials or tokens.

Requires ADMIN_CHECK_USERNAME/ADMIN_CHECK_PASSWORD. Registers one temporary account, then verifies
search/detail/sessions/sync, immediate session revocation on disable, and login restoring on enable.
The temporary account and its audit rows are removed at the end.
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
    assert condition, "Administrator user check failed (sensitive response omitted)"
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
        print("Set ADMIN_CHECK_USERNAME and ADMIN_CHECK_PASSWORD to run the administrator user checks")
        return
    status, body = request("admin/auth/login",
                           {"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD}, method="POST")
    if status != 200 or body["code"] != 0:
        raise AssertionError(f"Administrator login failed (status={status}, code={body.get('code')})")
    admin_token = body["data"]["accessToken"]
    email = "user-check-" + secrets.token_hex(10) + "@example.invalid"
    password = secrets.token_urlsafe(16)
    user_id = None
    try:
        status, registered = request("auth/register", {"email": email, "password": password, "nickname": "用户校验"})
        check(status == 201)
        user = registered["data"]["user"]
        user_id = user["id"]
        user_token = registered["data"]["accessToken"]
        refresh_token = registered["data"]["refreshToken"]

        check(request("admin/users")[0] == 401)
        status, page = request(f"admin/users?pageSize=50&keyword={email}", token=admin_token)
        check(status == 200 and page["data"]["total"] == 1
              and page["data"]["records"][0]["email"] == email)
        status, detail = request(f"admin/users/{user_id}", token=admin_token)
        check(status == 200 and detail["data"]["enabled"] is True and detail["data"]["favoriteCount"] == 0)
        status, session_list = request(f"admin/users/{user_id}/sessions", token=admin_token)
        check(status == 200 and len(session_list["data"]) >= 1
              and all(item["revokedAt"] is None for item in session_list["data"]))
        check(all(len(item["sessionCodeMasked"]) <= 9 for item in session_list["data"]))
        status, sync_list = request(f"admin/users/{user_id}/sync?limit=10", token=admin_token)
        check(status == 200 and isinstance(sync_list["data"], list))
        status, body = request(f"admin/users/{user_id}/sync?limit=0", token=admin_token)
        check(status == 400 and body["code"] == 10001)
        status, body = request("admin/users/abc", token=admin_token)
        check(status == 400 and body["code"] == 10001)
        status, body = request("admin/users/999999999", token=admin_token)
        check(status == 404 and body["code"] == 10006)

        status, disabled = request(f"admin/users/{user_id}/status", {"enabled": False}, token=admin_token,
                                   method="PATCH")
        check(status == 200 and disabled["data"]["enabled"] is False)
        check(request("auth/me", token=user_token)[0] == 401)
        status, body = request("auth/refresh", {"refreshToken": refresh_token}, method="POST")
        check(status == 401)
        status, body = request("auth/login", {"email": email, "password": password}, method="POST")
        check(status == 401 and body["code"] == 20001)
        status, session_list = request(f"admin/users/{user_id}/sessions", token=admin_token)
        check(status == 200 and all(item["revokedAt"] is not None for item in session_list["data"]))

        status, enabled = request(f"admin/users/{user_id}/status", {"enabled": True}, token=admin_token,
                                  method="PATCH")
        check(status == 200 and enabled["data"]["enabled"] is True)
        status, body = request("auth/login", {"email": email, "password": password}, method="POST")
        check(status == 200 and body["code"] == 0)
        status, body = request(f"admin/users/{user_id}/status", {"enabled": True}, token=admin_token,
                               method="PATCH")
        check(status == 200)
        check(int(sql("SELECT COUNT(*) FROM admin_operation_log WHERE module='user' AND result='SUCCESS'")) >= 2)
    finally:
        if user_id is not None:
            print("cleanup temp user:", sql(f"DELETE FROM sys_user WHERE id={user_id}"))
        print("cleanup user audit rows:", sql("DELETE FROM admin_operation_log WHERE module='user'"))
        print("cleanup leftovers:", sql("DELETE FROM sys_user WHERE email LIKE 'user-check-%@example.invalid'"))
    print(f"{CHECKS} administrator user checks passed")


if __name__ == "__main__":
    main()
