"""BaoTa-mode deployment drill on the local Compose stack.

Starts the stack with docker-compose.baota.yml (web container bound to
127.0.0.1:18080, HTTP-only edge template), then verifies Host-based routing for
both domains, relative redirects, the 444 default host, per-client rate-limit
identity through X-Real-IP, and the full release smoke suite with host
overrides. The drill stack and its volumes are removed afterwards.
"""
import http.client
import os
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEPLOY = ROOT / "deploy"
DRILL = ROOT / "temp" / "baota-deploy"
PROJECT = "cangshuo-toolbox-baota-drill"
PORT = 18080
WEB_DOMAIN = "tool.zhzgo.cn"
API_DOMAIN = "toolapi.zhzgo.cn"
CHECKS = 0


def check(condition, reason):
    global CHECKS
    assert condition, "BaoTa deployment check failed: " + reason
    CHECKS += 1


def posix(path):
    return str(path).replace("\\", "/")


def request(method, path, host, real_ip="", timeout=20):
    headers = {"Host": host, "Accept": "application/json"}
    if real_ip:
        headers["X-Real-IP"] = real_ip
    req = urllib.request.Request(f"http://127.0.0.1:{PORT}{path}", headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=timeout)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, dict(response.headers)


def main():
    allowed_root = (ROOT / "temp").resolve()
    if not str(DRILL.resolve()).startswith(str(allowed_root)):
        raise SystemExit("Refusing to touch a drill directory outside temp/")
    shutil.rmtree(DRILL, ignore_errors=True)
    DRILL.mkdir(parents=True)

    values = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key] = value
    values["COMPOSE_PROJECT_NAME"] = PROJECT
    values["BAOTA_WEB_PORT"] = str(PORT)
    env_file = DRILL / "baota.env"
    env_file.write_text("".join(f"{key}={value}\n" for key, value in values.items()), encoding="utf-8")

    env = os.environ.copy()
    env.update({
        "SERVER_IMAGE": "cangshuo-toolbox-local-api:0.1.0",
        "WEB_IMAGE": "cangshuo-toolbox-local-web:0.1.0",
    })
    compose = ["docker", "compose", "-p", PROJECT, "--env-file", posix(env_file),
               "-f", "docker-compose.yml", "-f", "docker-compose.baota.yml"]
    try:
        subprocess.run(compose + ["config", "--quiet"], cwd=DEPLOY, env=env, check=True)
        check(True, "compose configuration")
        resolved = subprocess.run(compose + ["config", "--format", "json"], cwd=DEPLOY, env=env,
                                  check=True, capture_output=True, text=True).stdout
        check('"VITE_API_BASE_URL": "https://toolapi.zhzgo.cn/api/v1"' in resolved.replace('\n', ' ')
              or '"VITE_API_BASE_URL":"https://toolapi.zhzgo.cn/api/v1"' in resolved,
              "admin built against the API domain")
        subprocess.run(compose + ["up", "-d", "--wait", "--wait-timeout", "300"],
                       cwd=DEPLOY, env=env, check=True, capture_output=True)
        check(True, "stack started")

        for path in ("/", "/privacy/", "/permissions/", "/account/delete/", "/admin/", "/tools/qr_studio/"):
            status, _ = request("GET", path, WEB_DOMAIN)
            check(status == 200, "web page " + path)

        connection = http.client.HTTPConnection("127.0.0.1", PORT, timeout=10)
        connection.request("GET", "/admin", headers={"Host": WEB_DOMAIN})
        redirect = connection.getresponse()
        check(redirect.status == 308 and redirect.getheader("Location") == "/admin/", "relative admin redirect")
        connection.close()

        status, _ = request("GET", "/api/v1/health", API_DOMAIN)
        check(status == 200, "api health on api host")
        status, _ = request("GET", "/api/v1/health", WEB_DOMAIN)
        check(status == 404, "api path separated from web host")

        try:
            request("GET", "/", "unknown.example")
            check(False, "default host should close the connection")
        except Exception:
            check(True, "default host rejected")

        _, headers_a = request("GET", "/api/v1/tools?page=1&pageSize=1", API_DOMAIN, "198.51.100.10")
        _, headers_a2 = request("GET", "/api/v1/tools?page=1&pageSize=1", API_DOMAIN, "198.51.100.10")
        _, headers_b = request("GET", "/api/v1/tools?page=1&pageSize=1", API_DOMAIN, "198.51.100.11")
        remaining_a = int(headers_a["X-RateLimit-Remaining"])
        remaining_a2 = int(headers_a2["X-RateLimit-Remaining"])
        remaining_b = int(headers_b["X-RateLimit-Remaining"])
        check(remaining_a2 == remaining_a - 1, "same client shares one bucket")
        check(remaining_b == remaining_a, "different clients get separate buckets")

        smoke = subprocess.run(
            [sys.executable, "../scripts/smoke_release.py",
             "--base-url", f"http://127.0.0.1:{PORT}/api/v1",
             "--web-url", f"http://127.0.0.1:{PORT}",
             "--api-host", API_DOMAIN, "--web-host", WEB_DOMAIN,
             "--origin", "https://tool.zhzgo.cn"],
            cwd=DEPLOY, env=env, capture_output=True, text=True)
        assert smoke.returncode == 0, smoke.stderr[-400:]
        check("checks passed" in smoke.stdout, "release smoke suite with host overrides")
    finally:
        subprocess.run(compose + ["down", "--volumes", "--remove-orphans"],
                       cwd=DEPLOY, env=env, capture_output=True)
        shutil.rmtree(DRILL, ignore_errors=True)
    print(f"BaoTa deployment drill: {CHECKS}/{CHECKS} checks passed; drill stack and volumes removed.")


if __name__ == "__main__":
    main()
