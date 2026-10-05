"""Exercise administrator announcements against the local stack; never print credentials or tokens.

Requires ADMIN_CHECK_USERNAME/ADMIN_CHECK_PASSWORD. Creates one draft, drives publish/unpublish and the
UTC window through the anonymous projection, then removes the row and its audit entries.
"""
import json
import os
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
    assert condition, "Administrator announcement check failed (sensitive response omitted)"
    CHECKS += 1


def sql(statement):
    result = subprocess.run([
        "docker", "compose", "--env-file", "../.env", "-f", "docker-compose.yml", "-f",
        "docker-compose.local.yml", "exec", "-T", "mysql", "sh", "-c",
        'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 '
        '-u "$MYSQL_USER" --database="$MYSQL_DATABASE" --batch --skip-column-names --execute="$1"',
        "sh", statement], cwd=ROOT / "deploy", capture_output=True, text=True, check=True)
    return result.stdout.strip()


def public_ids():
    return {item["id"] for item in request("home/announcements")[1]["data"]}


def main():
    if not (ADMIN_USERNAME and ADMIN_PASSWORD):
        print("Set ADMIN_CHECK_USERNAME and ADMIN_CHECK_PASSWORD to run the announcement checks")
        return
    status, body = request("admin/auth/login",
                           {"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD}, method="POST")
    if status != 200 or body["code"] != 0:
        raise AssertionError(f"Administrator login failed (status={status}, code={body.get('code')})")
    token = body["data"]["accessToken"]
    announcement_id = None
    payload = {"title": "校验公告", "body": "第一行\n第二行", "level": "WARNING", "enabled": False,
               "startAt": None, "endAt": None}
    try:
        check(request("home/announcements")[0] == 200)
        check(request("admin/announcements")[0] == 401)
        status, listed = request("admin/announcements", token=token)
        check(status == 200 and isinstance(listed["data"], list))
        status, created = request("admin/announcements", payload, token=token, method="POST")
        check(status == 201 and created["data"]["published"] is False
              and created["data"]["active"] is False and created["data"]["level"] == "WARNING")
        announcement_id = created["data"]["id"]
        check(announcement_id not in public_ids())
        status, body = request("admin/announcements", {**payload, "level": "LOUD"}, token=token, method="POST")
        check(status == 400 and body["code"] == 10001)
        status, body = request("admin/announcements", {**payload, "title": "两\n行"}, token=token, method="POST")
        check(status == 400 and body["code"] == 10001)
        status, body = request("admin/announcements", {**payload, "body": " "}, token=token, method="POST")
        check(status == 400 and body["code"] == 10001)
        status, body = request(f"admin/announcements/{announcement_id}",
                               {**payload, "startAt": "2026-11-05T00:00:00Z", "endAt": "2026-10-05T00:00:00Z"},
                               token=token, method="PUT")
        check(status == 400 and body["code"] == 10001)
        status, body = request(f"admin/announcements/{announcement_id}",
                               {**payload, "startAt": "bad-date"}, token=token, method="PUT")
        check(status == 400 and body["code"] == 10001)
        status, published = request(f"admin/announcements/{announcement_id}/status", {"enabled": True},
                                    token=token, method="PATCH")
        check(status == 200 and published["data"]["published"] is True and published["data"]["active"] is True)
        check(announcement_id in public_ids())
        status, scheduled = request(f"admin/announcements/{announcement_id}",
                                    {**payload, "enabled": True, "startAt": "2099-01-01T00:00:00Z"},
                                    token=token, method="PUT")
        check(status == 200 and scheduled["data"]["active"] is False)
        check(announcement_id not in public_ids())
        status, opened = request(f"admin/announcements/{announcement_id}", {**payload, "enabled": True},
                                 token=token, method="PUT")
        check(status == 200 and opened["data"]["active"] is True)
        check(announcement_id in public_ids())
        status, _ = request(f"admin/announcements/{announcement_id}/status", {"enabled": False}, token=token,
                            method="PATCH")
        check(status == 200)
        check(announcement_id not in public_ids())
        status, body = request("admin/announcements/abc", token=token, method="DELETE")
        check(status == 400 and body["code"] == 10001)
        status, body = request("admin/announcements/999999999", {**payload, "enabled": True}, token=token, method="PUT")
        check(status == 404 and body["code"] == 10006)
        status, _ = request(f"admin/announcements/{announcement_id}", token=token, method="DELETE")
        check(status == 200)
        check(request(f"admin/announcements/{announcement_id}", token=token, method="DELETE")[0] == 404)
        check(announcement_id not in public_ids())
        check(sql(f"SELECT COUNT(*) FROM announcement WHERE id={announcement_id} AND deleted_at IS NOT NULL") == "1")
        check(int(sql("SELECT COUNT(*) FROM admin_operation_log WHERE module='announcement' AND result='SUCCESS'")) >= 4)
    finally:
        if announcement_id is not None:
            print("cleanup temp announcement:", sql(f"DELETE FROM announcement WHERE id={announcement_id}"))
        print("cleanup announcement audit rows:", sql("DELETE FROM admin_operation_log WHERE module='announcement'"))
        print("cleanup leftovers:", sql("DELETE FROM announcement WHERE title='校验公告'"))
    print(f"{CHECKS} administrator announcement checks passed")


if __name__ == "__main__":
    main()
