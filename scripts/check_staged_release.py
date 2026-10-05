"""Staged-release drill against the local Compose stack.

Builds release-tagged images once, promotes them through the staging project,
simulates a second build by retagging those images, promotes again, then runs
rollback. Verifies the release state file and runs the real smoke suite after
every step. The staging project and temporary state are removed afterwards
and the production project is reset to its original images.
"""
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEPLOY = ROOT / "deploy"
DRILL = ROOT / "temp" / "staged-release"
PROD_PROJECT = "cangshuo-toolbox-local"
STAGING_PROJECT = "cangshuo-toolbox-staging"
RELEASE_REPO = "cangshuo-toolbox-release"
GIT_BASH_CANDIDATES = [Path(r"D:\codeStudy\Git\usr\bin\bash.exe"), Path(r"C:\Program Files\Git\usr\bin\bash.exe")]

CHECKS = 0


def check(condition, reason):
    global CHECKS
    assert condition, "Staged-release drill check failed: " + reason
    CHECKS += 1


def bash():
    for candidate in GIT_BASH_CANDIDATES:
        if candidate.is_file():
            return str(candidate)
    found = shutil.which("bash")
    assert found, "bash is required for the staged-release drill"
    return found


def posix(path):
    return str(path).replace("\\", "/")


def shell(env, command, expect=0):
    result = subprocess.run([bash(), "-lc", command], cwd=DEPLOY, env=env, capture_output=True, text=True)
    assert result.returncode == expect, f"command failed ({result.returncode}): {command}\n{result.stderr[-500:]}"
    return result.stdout


def read_state(state_file):
    values = {}
    for line in state_file.read_text(encoding="utf-8").splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
    return values


def write_staging_env(target):
    values = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key] = value
    values.update({
        "COMPOSE_PROJECT_NAME": STAGING_PROJECT,
        "LOCAL_API_PORT": "18081",
        "LOCAL_WEB_PORT": "18088",
        "LOCAL_MYSQL_PORT": "13307",
        "LOCAL_REDIS_PORT": "16380",
        "LOCAL_MINIO_PORT": "19000",
        "LOCAL_MINIO_CONSOLE_PORT": "19001",
    })
    target.write_text("".join(f"{key}={value}\n" for key, value in values.items()), encoding="utf-8")


def main():
    allowed_root = (ROOT / "temp").resolve()
    if not str(DRILL.resolve()).startswith(str(allowed_root)):
        raise SystemExit("Refusing to touch a drill directory outside temp/")
    shutil.rmtree(DRILL, ignore_errors=True)
    (DRILL / "tmp").mkdir(parents=True)
    staging_env = DRILL / "staging.env"
    write_staging_env(staging_env)
    state_file = DRILL / "state.env"

    env = os.environ.copy()
    for key in ("SERVER_IMAGE", "WEB_IMAGE", "RELEASE_TAG"):
        env.pop(key, None)
    env.update({
        "PROD_PROJECT": PROD_PROJECT,
        "STAGING_PROJECT": STAGING_PROJECT,
        "PROD_ENV_FILE": "../.env",
        "STAGING_ENV_FILE": posix(staging_env),
        "PROD_COMPOSE_FILES": "-f docker-compose.yml -f docker-compose.local.yml",
        "STAGING_COMPOSE_FILES": "-f docker-compose.yml -f docker-compose.local.yml",
        "RELEASE_REPO": RELEASE_REPO,
        "STATE_FILE": posix(state_file),
        "PYTHON": posix(sys.executable),
        "STAGING_API_URL": "http://127.0.0.1:18081/api/v1",
        "STAGING_WEB_URL": "http://127.0.0.1:18088",
        "PROD_API_URL": "http://127.0.0.1:8081/api/v1",
        "PROD_WEB_URL": "http://127.0.0.1:8088",
        "CORS_ORIGIN": "https://tool.zhzgo.cn",
        "TMPDIR": posix(DRILL / "tmp"),
    })
    staging_down = (f"docker compose -p {STAGING_PROJECT} --env-file {posix(staging_env)} "
                    "-f docker-compose.yml -f docker-compose.local.yml down --volumes --remove-orphans")
    reset_production = ("docker compose --env-file ../.env -f docker-compose.yml -f docker-compose.local.yml "
                        "up -d --wait --wait-timeout 300")
    try:
        out = shell(env, "sh ./release/release.sh preflight")
        check("preflight ok" in out, "preflight")

        out = shell(env, "sh ./release/release.sh build --tag drill-1")
        check("build ok" in out, "build drill-1")
        images = shell(env, f"docker image inspect {RELEASE_REPO}-api:drill-1 >/dev/null && echo present")
        check("present" in images, "drill-1 images exist")

        out = shell(env, "sh ./release/release.sh stage --tag drill-1")
        check("stage ok: current=drill-1" in out, "stage drill-1")
        check(read_state(state_file)["CURRENT_TAG"] == "drill-1", "state after drill-1")

        shell(env, f"docker tag {RELEASE_REPO}-api:drill-1 {RELEASE_REPO}-api:drill-2 && "
                   f"docker tag {RELEASE_REPO}-web:drill-1 {RELEASE_REPO}-web:drill-2")
        out = shell(env, "sh ./release/release.sh stage --tag drill-2")
        check("stage ok: current=drill-2 previous=drill-1" in out, "stage drill-2")
        state = read_state(state_file)
        check(state["CURRENT_TAG"] == "drill-2" and state["PREVIOUS_TAG"] == "drill-1", "state after drill-2")

        out = shell(env, "sh ./release/release.sh rollback")
        check("rollback ok: current=drill-1 previous=drill-2" in out, "rollback to drill-1")
        state = read_state(state_file)
        check(state["CURRENT_TAG"] == "drill-1" and state["PREVIOUS_TAG"] == "drill-2", "state after rollback")

        out = shell(env, "sh ./release/release.sh status")
        check("CURRENT_TAG=drill-1" in out and "server" in out, "status")

        out = shell(env, f"{posix(sys.executable)} ../scripts/smoke_release.py --base-url http://127.0.0.1:8081/api/v1 "
                         "--web-url http://127.0.0.1:8088 --origin https://tool.zhzgo.cn")
        check("checks passed" in out, "production smoke after rollback")
    finally:
        subprocess.run([bash(), "-lc", staging_down], cwd=DEPLOY, env=env, capture_output=True)
        subprocess.run([bash(), "-lc", reset_production], cwd=DEPLOY, env=env, capture_output=True)
        for tag in ("drill-1", "drill-2"):
            subprocess.run(["docker", "rmi", f"{RELEASE_REPO}-api:{tag}"], env=env, capture_output=True)
            subprocess.run(["docker", "rmi", f"{RELEASE_REPO}-web:{tag}"], env=env, capture_output=True)
        shutil.rmtree(DRILL, ignore_errors=True)
    print(f"Staged-release drill: {CHECKS}/{CHECKS} checks passed; staging project and temporary state cleaned.")


if __name__ == "__main__":
    main()
