"""Exercise administrator recommendation slots against the local stack; never print credentials or tokens.

Requires ADMIN_CHECK_USERNAME/ADMIN_CHECK_PASSWORD. Creates two temporary slots (tool target and link
target with a future window), verifies the public /home/recommendations projection, then removes the
rows and their audit entries.
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
    assert condition, "Administrator recommendation check failed (sensitive response omitted)"
    CHECKS += 1


def sql(statement):
    result = subprocess.run([
        "docker", "compose", "--env-file", "../.env", "-f", "docker-compose.yml", "-f",
        "docker-compose.local.yml", "exec", "-T", "mysql", "sh", "-c",
        'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 '
        '-u "$MYSQL_USER" --database="$MYSQL_DATABASE" --batch --skip-column-names --execute="$1"',
        "sh", statement], cwd=ROOT / "deploy", capture_output=True, text=True, check=True)
    return result.stdout.strip()


def public_slots():
    return {item["slotCode"] for item in request("home/recommendations")[1]["data"]}


def main():
    if not (ADMIN_USERNAME and ADMIN_PASSWORD):
        print("Set ADMIN_CHECK_USERNAME and ADMIN_CHECK_PASSWORD to run the recommendation checks")
        return
    status, body = request("admin/auth/login",
                           {"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD}, method="POST")
    if status != 200 or body["code"] != 0:
        raise AssertionError(f"Administrator login failed (status={status}, code={body.get('code')})")
    token = body["data"]["accessToken"]
    tool_slot = "tmp_slot_" + secrets.token_hex(3)
    link_slot = "tmp_link_" + secrets.token_hex(3)
    tool_payload = {"title": "校验推荐", "subtitle": "工具推荐", "toolCode": "calculator", "linkUrl": None,
                    "imageUrl": None, "sortOrder": 993, "enabled": True, "startAt": None, "endAt": None}
    link_payload = {"title": "校验链接", "subtitle": "链接推荐", "toolCode": None,
                    "linkUrl": "https://example.com/activity", "imageUrl": None, "sortOrder": 994,
                    "enabled": True, "startAt": "2099-01-01T00:00:00Z", "endAt": None}
    try:
        check(request("home/recommendations")[0] == 200)
        check(request("admin/recommendations")[0] == 401)
        status, listed = request("admin/recommendations", token=token)
        check(status == 200 and isinstance(listed["data"], list))
        status, body = request("admin/recommendations",
                               {**tool_payload, "slotCode": tool_slot, "linkUrl": "https://example.com"},
                               token=token, method="POST")
        check(status == 400 and body["code"] == 10001)
        status, body = request("admin/recommendations",
                               {**tool_payload, "slotCode": tool_slot, "toolCode": None}, token=token, method="POST")
        check(status == 400 and body["code"] == 10001)
        status, body = request("admin/recommendations",
                               {**tool_payload, "slotCode": tool_slot, "toolCode": "no_such_tool"},
                               token=token, method="POST")
        check(status == 404 and body["code"] == 30009)
        status, created = request("admin/recommendations", {**tool_payload, "slotCode": tool_slot},
                                  token=token, method="POST")
        check(status == 201 and created["data"]["slotCode"] == tool_slot and created["data"]["active"] is True)
        status, body = request("admin/recommendations", {**tool_payload, "slotCode": tool_slot},
                               token=token, method="POST")
        check(status == 409 and body["code"] == 30008)
        check(tool_slot in public_slots())
        status, future = request("admin/recommendations", {**link_payload, "slotCode": link_slot},
                                 token=token, method="POST")
        check(status == 201 and future["data"]["active"] is False)
        check(link_slot not in public_slots())
        status, body = request(f"admin/recommendations/{link_slot}",
                               {**link_payload, "startAt": "not-a-date"}, token=token, method="PUT")
        check(status == 400 and body["code"] == 10001)
        status, body = request(f"admin/recommendations/{link_slot}",
                               {**link_payload, "startAt": "2026-11-05T00:00:00Z", "endAt": "2026-10-05T00:00:00Z"},
                               token=token, method="PUT")
        check(status == 400 and body["code"] == 10001)
        status, opened = request(f"admin/recommendations/{link_slot}",
                                 {**link_payload, "startAt": None}, token=token, method="PUT")
        check(status == 200 and opened["data"]["active"] is True)
        check(link_slot in public_slots())
        status, _ = request(f"admin/recommendations/{link_slot}/status", {"enabled": False}, token=token, method="PATCH")
        check(status == 200)
        check(link_slot not in public_slots())
        status, _ = request(f"admin/recommendations/{link_slot}/status", {"enabled": True}, token=token, method="PATCH")
        check(status == 200)
        check(link_slot in public_slots())
        status, body = request(f"admin/recommendations/{link_slot}", {**link_payload, "linkUrl": "http://example.com"},
                               token=token, method="PUT")
        check(status == 400 and body["code"] == 10001)
        status, _ = request(f"admin/recommendations/{tool_slot}", token=token, method="DELETE")
        check(status == 200)
        status, _ = request(f"admin/recommendations/{link_slot}", token=token, method="DELETE")
        check(status == 200)
        check(tool_slot not in public_slots() and link_slot not in public_slots())
        check(request(f"admin/recommendations/{tool_slot}", token=token, method="DELETE")[0] == 404)
        check(sql(f"SELECT COUNT(*) FROM home_recommendation WHERE slot_code IN ('{tool_slot}','{link_slot}') "
                  "AND deleted_at IS NOT NULL") == "2")
        check(int(sql("SELECT COUNT(*) FROM admin_operation_log WHERE module='recommendation' AND result='SUCCESS'")) >= 4)
    finally:
        print("cleanup temp slots:", sql(
            f"DELETE FROM home_recommendation WHERE slot_code IN ('{tool_slot}','{link_slot}')"))
        print("cleanup recommendation audit rows:", sql("DELETE FROM admin_operation_log WHERE module='recommendation'"))
    print(f"{CHECKS} administrator recommendation checks passed")


if __name__ == "__main__":
    main()
