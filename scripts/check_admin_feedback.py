"""Exercise user feedback submission and administrator triage; never print credentials or tokens.

Requires ADMIN_CHECK_USERNAME/ADMIN_CHECK_PASSWORD. Registers one temporary user, submits one item,
then drives PENDING -> PROCESSING -> RESOLVED with a reply and checks the user-visible projection.
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
    assert condition, "Feedback check failed (sensitive response omitted)"
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
        print("Set ADMIN_CHECK_USERNAME and ADMIN_CHECK_PASSWORD to run the feedback checks")
        return
    status, body = request("admin/auth/login",
                           {"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD}, method="POST")
    if status != 200 or body["code"] != 0:
        raise AssertionError(f"Administrator login failed (status={status}, code={body.get('code')})")
    admin_token = body["data"]["accessToken"]
    email = "feedback-check-" + secrets.token_hex(10) + "@example.invalid"
    password = secrets.token_urlsafe(16)
    user_id = None
    feedback_id = None
    try:
        status, registered = request("auth/register", {"email": email, "password": password, "nickname": "反馈用户"})
        check(status == 201)
        user_id = registered["data"]["user"]["id"]
        user_token = registered["data"]["accessToken"]
        check(request("feedback/my")[0] == 401)
        status, body = request("feedback", {"type": "LOUD", "content": "内容"}, token=user_token, method="POST")
        check(status == 400 and body["code"] == 10001)
        status, body = request("feedback", {"type": "BUG", "content": " "}, token=user_token, method="POST")
        check(status == 400 and body["code"] == 10001)
        status, body = request("feedback", {"type": "BUG", "content": "x" * 2001}, token=user_token, method="POST")
        check(status == 400 and body["code"] == 10001)
        status, submitted = request("feedback",
                                    {"type": "bug", "content": "第一行\n第二行", "contact": "qa@example.invalid"},
                                    token=user_token, method="POST")
        check(status == 201 and submitted["data"]["status"] == "PENDING" and submitted["data"]["type"] == "BUG")
        feedback_id = submitted["data"]["id"]
        status, mine = request("feedback/my", token=user_token)
        check(status == 200 and any(item["id"] == feedback_id for item in mine["data"]))
        check(request("admin/feedback")[0] == 401)
        status, page = request(f"admin/feedback?pageSize=50&keyword={email}", token=admin_token)
        check(status == 200 and page["data"]["total"] == 1
              and page["data"]["records"][0]["userEmail"] == email)
        status, filtered = request("admin/feedback?pageSize=50&status=PENDING", token=admin_token)
        check(status == 200 and any(item["id"] == feedback_id for item in filtered["data"]["records"]))
        status, body = request("admin/feedback?status=DONE", token=admin_token)
        check(status == 400 and body["code"] == 10001)
        status, processing = request(f"admin/feedback/{feedback_id}", {"status": "processing"},
                                     token=admin_token, method="PATCH")
        check(status == 200 and processing["data"]["status"] == "PROCESSING")
        status, body = request(f"admin/feedback/{feedback_id}", {"status": "RESOLVED", "reply": "bad\u0000reply"},
                               token=admin_token, method="PATCH")
        check(status == 400 and body["code"] == 10001)
        status, body = request("admin/feedback/999999999", {"status": "RESOLVED"}, token=admin_token, method="PATCH")
        check(status == 404 and body["code"] == 10006)
        status, resolved = request(f"admin/feedback/{feedback_id}",
                                   {"status": "RESOLVED", "reply": "已修复，请更新版本"}, token=admin_token, method="PATCH")
        check(status == 200 and resolved["data"]["status"] == "RESOLVED"
              and resolved["data"]["reply"] == "已修复，请更新版本" and resolved["data"]["repliedAt"])
        status, mine = request("feedback/my", token=user_token)
        entry = next(item for item in mine["data"] if item["id"] == feedback_id)
        check(status == 200 and entry["status"] == "RESOLVED" and entry["reply"] == "已修复，请更新版本")
        check(int(sql("SELECT COUNT(*) FROM admin_operation_log WHERE module='feedback' AND result='SUCCESS'")) >= 2)
    finally:
        if feedback_id is not None:
            print("cleanup temp feedback:", sql(f"DELETE FROM user_feedback WHERE id={feedback_id}"))
        print("cleanup feedback audit rows:", sql("DELETE FROM admin_operation_log WHERE module='feedback'"))
        if user_id is not None:
            print("cleanup temp user:", sql(f"DELETE FROM sys_user WHERE id={user_id}"))
        print("cleanup leftovers:", sql("DELETE FROM sys_user WHERE email LIKE 'feedback-check-%@example.invalid'"))
    print(f"{CHECKS} feedback checks passed")


if __name__ == "__main__":
    main()
