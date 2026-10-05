"""Exercise administrator authentication APIs; never print credentials or tokens.

Negative checks always run. Set ADMIN_CHECK_USERNAME and ADMIN_CHECK_PASSWORD to a
bootstrapped administrator to also verify login, profile, logout and lockout. The lockout
checks throttle the calling address for fifteen minutes; restart the local server container
to clear the in-memory throttle afterwards.
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
    assert condition, "Administrator API check failed (sensitive response omitted)"
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
    try:
        run_checks()
    finally:
        # The ephemeral account created below is removed even when a check fails.
        print("cleanup ephemeral users:", sql("DELETE FROM sys_user WHERE email LIKE 'admin-check-%@example.invalid'"))


def run_checks():
    check(request("admin/auth/me")[0] == 401)
    status, body = request("admin/auth/login", {"username": "ab", "password": "short"}, method="POST")
    check(status == 400 and body["code"] == 10001)
    status, body = request("admin/auth/login",
                           {"username": "no-such-admin", "password": secrets.token_urlsafe(12)}, method="POST")
    check(status == 401 and body["code"] == 20004)

    email = "admin-check-" + secrets.token_hex(10) + "@example.invalid"
    status, registered = request("auth/register",
                                 {"email": email, "password": secrets.token_urlsafe(16), "nickname": "检查账号"})
    check(status == 201)
    user_token = registered["data"]["accessToken"]
    check(request("admin/auth/me", token=user_token)[0] == 403)
    check(request("admin/auth/logout", token=user_token, method="POST")[0] == 403)

    if ADMIN_USERNAME and ADMIN_PASSWORD:
        admin_rows = int(sql("SELECT COUNT(*) FROM admin_user"))
        if admin_rows == 0:
            print("No administrator row exists yet; start the server once with ADMIN_BOOTSTRAP_USERNAME and ADMIN_BOOTSTRAP_PASSWORD")
        status, body = request("admin/auth/login",
                               {"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD}, method="POST")
        if not (status == 200 and body["code"] == 0):
            raise AssertionError(f"Administrator login check failed (status={status}, code={body.get('code')}, admins={admin_rows})")
        check(True)
        session = body["data"]
        check(session["tokenType"] == "Bearer" and 300 <= session["expiresIn"] <= 86400)
        check(session["admin"]["username"] == ADMIN_USERNAME.strip().lower())
        check("passwordHash" not in session["admin"] and "accessToken" not in session["admin"])
        admin_token = session["accessToken"]
        status, me = request("admin/auth/me", token=admin_token)
        check(status == 200 and me["data"]["id"] == session["admin"]["id"])
        check(request("admin/auth/logout", token=admin_token, method="POST")[0] == 200)
        check(int(sql("SELECT COUNT(*) FROM admin_operation_log WHERE module='admin' AND result='SUCCESS'")) >= 2)

        locked_user = "lock-" + secrets.token_hex(4)
        for _ in range(5):
            status, body = request("admin/auth/login",
                                   {"username": locked_user, "password": secrets.token_urlsafe(12)}, method="POST")
            check(status == 401 and body["code"] == 20004)
        status, body = request("admin/auth/login",
                               {"username": locked_user, "password": secrets.token_urlsafe(12)}, method="POST")
        check(status == 429 and body["code"] == 10007)
        check(int(sql("SELECT COUNT(*) FROM admin_operation_log WHERE result='FAILED'")) >= 5)
        print("Administrator API checks passed (positive path); restart the local server container to clear the throttle")
    else:
        print("Administrator API negative-path checks passed; set ADMIN_CHECK_USERNAME/ADMIN_CHECK_PASSWORD for the full flow")
    print(f"{CHECKS} administrator API checks passed")


if __name__ == "__main__":
    main()
