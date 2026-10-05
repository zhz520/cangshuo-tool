"""Exercise local account APIs using an ephemeral account; never print credentials or tokens."""
import json
from pathlib import Path
import secrets
import subprocess
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
EMAIL = "auth-check-" + secrets.token_hex(12) + "@example.invalid"
PASSWORD = secrets.token_urlsafe(24)
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
    assert condition, "Account API check failed (sensitive response omitted)"
    CHECKS += 1


def sql(statement):
    # Values below are generated hexadecimal test identifiers, never human input.
    result = subprocess.run([
        "docker", "compose", "--env-file", "../.env", "-f", "docker-compose.yml", "-f",
        "docker-compose.local.yml", "exec", "-T", "mysql", "sh", "-c",
        'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 '
        '-u "$MYSQL_USER" --database="$MYSQL_DATABASE" --batch --skip-column-names --execute="$1"',
        "sh", statement], cwd=ROOT / "deploy", capture_output=True, text=True, check=True)
    return result.stdout.strip()


def main():
    try:
        check(request("auth/me")[0] == 401)
        body = {"email": EMAIL.upper(), "password": PASSWORD, "nickname": "测试账号"}
        status, registered = request("auth/register", body)
        check(status == 201 and registered["code"] == 0)
        data = registered["data"]
        check(data["user"]["email"] == EMAIL and data["expiresIn"] == 900 and data["tokenType"] == "Bearer")
        check("passwordHash" not in data["user"])
        check(len(data["refreshToken"]) == 76)
        import hashlib
        check(sql(f"SELECT COUNT(*) FROM auth_refresh_token WHERE token_hash='{hashlib.sha256(data['refreshToken'].encode()).hexdigest()}'") == "1")
        status, duplicate = request("auth/register", body)
        check(status == 409 and duplicate["code"] == 20002)
        status, wrong = request("auth/login", {"email": EMAIL, "password": "incorrect-password"})
        check(status == 401 and wrong["code"] == 20001 and wrong["data"] is None)
        status, unknown = request("auth/login", {"email": "unknown-" + EMAIL, "password": PASSWORD})
        check(status == 401 and unknown["code"] == wrong["code"])
        status, logged = request("auth/login", {"email": EMAIL, "password": PASSWORD})
        check(status == 200 and logged["data"]["user"] == data["user"])
        for port in (8081, 8088):
            status, me = request("auth/me", token=logged["data"]["accessToken"], port=port)
            check(status == 200 and me["data"] == data["user"])
        check(request("auth/me", token="invalid")[0] == 401)
        check(request("auth/me", {"nickname":"new"}, method="PUT")[0] == 401)
        status, edited = request("auth/me", {"nickname":" 新昵称 ","id":999}, logged["data"]["accessToken"], method="PUT")
        check(status == 200 and edited["data"]["id"] == data["user"]["id"] and edited["data"]["nickname"] == "新昵称")
        check(request("auth/me",token=data["accessToken"],port=8088)[1]["data"] == edited["data"])
        check(request("auth/me", {"nickname":"bad\nname"}, logged["data"]["accessToken"], method="PUT")[0] == 400)
        check(sql(f"SELECT HEX(nickname)='{'新昵称'.encode('utf-8').hex().upper()}' FROM sys_user WHERE email='{EMAIL}'") == "1")
        original = logged["data"]
        status, rotated = request("auth/refresh", {"refreshToken": original["refreshToken"]})
        check(status == 200 and rotated["data"]["refreshToken"] != original["refreshToken"])
        check(rotated["data"]["refreshExpiresAt"] == original["refreshExpiresAt"])
        check(request("auth/me", token=rotated["data"]["accessToken"])[0] == 200)
        status, replay = request("auth/refresh", {"refreshToken": original["refreshToken"]})
        check(status == 401 and replay["code"] == 10002)
        check(request("auth/me", token=rotated["data"]["accessToken"])[0] == 401)
        check(request("auth/refresh", {"refreshToken": rotated["data"]["refreshToken"]})[0] == 401)
        check(request("auth/me", token=data["accessToken"])[0] == 200)
        check(request("auth/logout", {"refreshToken": data["refreshToken"]})[0] == 200)
        check(request("auth/logout", {"refreshToken": data["refreshToken"]})[0] == 200)
        check(request("auth/me", token=data["accessToken"])[0] == 401)
        status, fresh = request("auth/login", {"email": EMAIL, "password": PASSWORD})
        check(status == 200)
        logged = fresh
        status, invalid = request("auth/register", {"email": EMAIL, "password": "密" * 25, "nickname": "u"})
        check(status == 400 and invalid["code"] == 10001)
        check(sql(f"SELECT password_hash LIKE '$2%' AND password_hash <> '{PASSWORD}' FROM sys_user WHERE email='{EMAIL}'") == "1")
        sql(f"UPDATE sys_user SET status=0 WHERE email='{EMAIL}'")
        check(request("auth/me", token=logged["data"]["accessToken"])[0] == 401)
        status, disabled = request("auth/login", {"email": EMAIL, "password": PASSWORD})
        check(status == 401 and disabled["code"] == 20001)
        check(request("tools")[1]["data"]["total"] >= 12)
        check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='18' AND success=1") == "1")
    finally:
        sql(f"DELETE FROM sys_user WHERE email='{EMAIL}'")
    print(f"Account API: {CHECKS}/{CHECKS} checks passed; temporary account deleted; no credentials displayed.")


if __name__ == "__main__":
    main()
