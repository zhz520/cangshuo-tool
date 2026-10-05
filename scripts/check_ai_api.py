"""Default-off AI endpoint and real MySQL quota checks. Only disposable QA data is used."""
import concurrent.futures
import json
import secrets
import subprocess
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EMAIL = f"qa-ai-{secrets.token_hex(12)}@example.com"
PASSWORD = secrets.token_urlsafe(24)
CHECKS = 0

def check(condition):
    global CHECKS
    assert condition, "AI API check failed (details omitted)"
    CHECKS += 1

def request(path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request("http://127.0.0.1:8081/api/v1/" + path,
                                 json.dumps(body).encode() if body is not None else None, headers)
    try:
        response = urllib.request.urlopen(req, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, json.load(response)

def sql(statement):
    result = subprocess.run(["docker", "compose", "--env-file", "../.env", "-f", "docker-compose.yml",
        "-f", "docker-compose.local.yml", "exec", "-T", "mysql", "sh", "-c",
        'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 -u "$MYSQL_USER" '
        '--database="$MYSQL_DATABASE" --batch --skip-column-names --execute="$1"', "sh", statement],
        cwd=ROOT / "deploy", capture_output=True, text=True, check=True)
    return result.stdout.strip()

def main():
    user = None
    try:
        check(request("tools/ai-text/status")[0] == 401)
        code, envelope = request("auth/register", {"email": EMAIL, "password": PASSWORD, "nickname": "AI QA"})
        check(code == 201)
        user = int(envelope["data"]["user"]["id"])
        token = envelope["data"]["accessToken"]
        code, status = request("tools/ai-text/status", token=token)
        check(code == 200 and status["data"]["enabled"] is False)
        check(set(status["data"]) == {"enabled", "providerName", "model", "dailyLimit", "usedToday", "maxInputChars"})
        code, disabled = request("tools/ai-text", {"task": "REWRITE", "text": "QA text"}, token)
        check(code == 503 and disabled["code"] == 10008)
        check(sql(f"SELECT COUNT(*) FROM ai_daily_usage WHERE user_id={user}") == "0")
        check(request("tools/ai-text", {"task": "BAD", "text": "QA"}, token)[0] == 400)
        check(request("tools/ai-text", {"task": "REWRITE", "text": "x" * 70000}, token)[0] == 413)
        sql(f"INSERT INTO ai_daily_usage VALUES ({user},UTC_DATE(),0)")
        def reserve(_):
            return int(sql(f"UPDATE ai_daily_usage SET attempts=attempts+1 WHERE user_id={user} AND usage_date=UTC_DATE() AND attempts<12; SELECT ROW_COUNT()"))
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            check(sum(pool.map(reserve, range(24))) == 12)
        check(sql(f"SELECT attempts FROM ai_daily_usage WHERE user_id={user}") == "12")
        code, catalog = request("tools?pageSize=100")
        check(code == 200 and any(item["code"] == "ai_text" and item["requiresLogin"] for item in catalog["data"]["records"]))
    finally:
        sql(f"DELETE FROM sys_user WHERE email='{EMAIL}'")
        if user is not None:
            check(sql(f"SELECT COUNT(*) FROM ai_daily_usage WHERE user_id={user}") == "0")
    print(f"AI API/MySQL: {CHECKS}/{CHECKS} checks passed; paid provider not configured.")

if __name__ == "__main__":
    main()
