"""Encrypted backup/restore drill against the local Compose stack.

Creates disposable accounts, takes one encrypted full backup, then mutates
state (renames one account, deletes another, adds a new account), exports a
newer independent deletion ledger and restores. Verifies that the backup
state returns, that every deleted account stays suppressed even when deleted
after the backup, and that the optional MinIO volume archive round-trips.
All disposable data and files are removed afterwards.
"""
import os
import secrets
import shlex
import shutil
import subprocess
from pathlib import Path

from check_auth_api import request, sql

ROOT = Path(__file__).resolve().parents[1]
DEPLOY = ROOT / "deploy"
DRILL_DIR = ROOT / "temp" / "backup-drill"
GIT_BASH_CANDIDATES = [Path(r"D:\codeStudy\Git\usr\bin\bash.exe"), Path(r"C:\Program Files\Git\usr\bin\bash.exe")]

CHECKS = 0


def check(condition):
    global CHECKS
    assert condition, "Backup/restore drill check failed (details omitted)"
    CHECKS += 1


def bash():
    for candidate in GIT_BASH_CANDIDATES:
        if candidate.is_file():
            return str(candidate)
    found = shutil.which("bash")
    assert found, "bash is required for the backup drill"
    return found


def posix(path):
    return str(path).replace("\\", "/")


def run_script(env, script, *args):
    command = "exec ./backup/" + script + " " + " ".join(shlex.quote(arg) for arg in args)
    result = subprocess.run([bash(), "-lc", command], cwd=DEPLOY, env=env, capture_output=True, text=True)
    assert result.returncode == 0, f"{script} failed: {result.stderr[-400:]}"
    return result.stdout


def docker(env, *args, stdin=None):
    return subprocess.run(["docker", *args], env=env, input=stdin, capture_output=True, check=True)


def first_image(env, *candidates):
    for image in candidates:
        if image and subprocess.run(["docker", "image", "inspect", image], env=env, capture_output=True).returncode == 0:
            return image
    return candidates[0]


def main():
    allowed_root = (ROOT / "temp").resolve()
    if not str(DRILL_DIR.resolve()).startswith(str(allowed_root)):
        raise SystemExit("Refusing to touch a drill directory outside temp/")
    shutil.rmtree(DRILL_DIR, ignore_errors=True)
    backups = DRILL_DIR / "backups"
    tmp = DRILL_DIR / "tmp"
    tmp.mkdir(parents=True)
    passphrase = DRILL_DIR / "pass.txt"
    passphrase.write_text(secrets.token_urlsafe(32) + "\n", encoding="utf-8")

    minio_volume = "cangshuo-toolbox-local_minio-data"
    marker = "drill-marker-v1"

    env = os.environ.copy()
    env.update({
        "COMPOSE_ENV_FILE": "../.env",
        "COMPOSE_FILES": "-f docker-compose.yml -f docker-compose.local.yml",
        "BACKUP_DIR": posix(backups),
        "BACKUP_PASSPHRASE_FILE": posix(passphrase),
        "INCLUDE_MINIO": "true",
        "MINIO_VOLUME": minio_volume,
        "TMPDIR": posix(tmp),
    })
    helper_image = first_image(
        env,
        os.environ.get("BACKUP_DRILL_HELPER_IMAGE", ""),
        "nginx:1.30.5-alpine3.24",
        "cangshuo-toolbox-local-minio:RELEASE.2025-10-15T17-29-55Z",
        "alpine:3.24",
        "mysql:8.4.11",
    )
    env["MINIO_HELPER_IMAGE"] = helper_image

    suffix = secrets.token_hex(6)
    emails = {name: f"qa-backup-{name}-{suffix}@example.com" for name in ("keep", "gone", "late", "new")}
    password = secrets.token_urlsafe(24)
    ids = {}
    seeds = []
    try:
        def register(name, nickname):
            status, body = request("auth/register", dict(email=emails[name], password=password, nickname=nickname))
            check(status == 201)
            ids[name] = int(body["data"]["user"]["id"])
            return body["data"]["accessToken"]

        keep_token = register("keep", "drill-before")
        gone_token = register("gone", "drill-gone")
        late_token = register("late", "drill-late")
        seeds.append((emails["gone"], password, gone_token))

        docker(env, "volume", "create", minio_volume)
        docker(env, "run", "--rm", "--entrypoint", "sh", "-v", f"{minio_volume}:/data", helper_image,
               "-ec", f"printf %s {marker} > /data/.drill-marker")

        status, _ = request("auth/me", dict(email=emails["gone"], password=password), gone_token, method="DELETE")
        check(status == 200)

        out = run_script(env, "backup.sh")
        check("backup finished" in out)
        archives = sorted(backups.glob("toolbox-*.tar.gz.enc"))
        check(len(archives) == 1 and archives[0].stat().st_size > 0)
        check(Path(str(archives[0]) + ".sha256").is_file())
        ledger_files = sorted((backups / "ledger").glob("ledger-*.tsv.enc"))
        check(len(ledger_files) == 1)

        status, _ = request("auth/me", dict(nickname="drill-after"), keep_token, method="PUT")
        check(status == 200)
        status, _ = request("auth/me", dict(email=emails["late"], password=password), late_token, method="DELETE")
        check(status == 200)
        register("new", "drill-new")

        out = run_script(env, "backup.sh", "--ledger-only")
        check("backup finished" in out)

        docker(env, "run", "--rm", "--entrypoint", "sh", "-v", f"{minio_volume}:/data", helper_image,
               "-ec", "rm -f /data/.drill-marker")
        before = docker(env, "run", "--rm", "--entrypoint", "sh", "-v", f"{minio_volume}:/data", helper_image,
                        "-ec", "test ! -e /data/.drill-marker && echo absent")
        check(before.stdout.strip() == b"absent")

        out = run_script(env, "restore.sh", "--backup", posix(archives[0]), "--confirm", "--minio")
        check("restore finished" in out)

        status, body = request("auth/login", dict(email=emails["keep"], password=password))
        check(status == 200 and body["data"]["user"]["nickname"] == "drill-before")
        for name in ("gone", "late", "new"):
            status, _ = request("auth/login", dict(email=emails[name], password=password))
            check(status == 401)
        check(sql(f"SELECT COUNT(*) FROM sys_user WHERE id={ids['late']}") == "0")
        check(sql(f"SELECT COUNT(*) FROM deleted_account WHERE user_id IN ({ids['gone']},{ids['late']})") == "2")
        check(sql(f"SELECT COUNT(*) FROM deleted_account WHERE user_id={ids['keep']}") == "0")

        restored = docker(env, "run", "--rm", "--entrypoint", "sh", "-v", f"{minio_volume}:/data", helper_image,
                          "-ec", "cat /data/.drill-marker")
        check(restored.stdout.decode().strip() == marker)
    finally:
        for email in emails.values():
            sql(f"DELETE FROM sys_user WHERE email='{email}'")
        if "gone" in ids and "late" in ids:
            sql(f"DELETE FROM deleted_account WHERE user_id IN ({ids['gone']},{ids['late']})")
        subprocess.run(["docker", "run", "--rm", "--entrypoint", "sh", "-v", f"{minio_volume}:/data", helper_image,
                        "-ec", "rm -f /data/.drill-marker"], env=env, capture_output=True, check=False)
        shutil.rmtree(DRILL_DIR, ignore_errors=True)
    print(f"Backup/restore drill: {CHECKS}/{CHECKS} checks passed; disposable data and files cleaned.")


if __name__ == "__main__":
    main()
