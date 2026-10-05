"""Exercise the administrator object-storage API against the local MinIO profile.

Requires ADMIN_CHECK_USERNAME/ADMIN_CHECK_PASSWORD and a server started with STORAGE_ENABLED=true
(see deploy/README.md). Uploads one real PNG, verifies the server-computed SHA-256, downloads it
through a presigned URL and deletes it again; the audit rows created here are removed too.
"""
import base64
import hashlib
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
PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
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
        response = urllib.request.urlopen(req, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = json.load(response)
        assert body["traceId"] == response.headers["X-Trace-Id"]
        return response.status, body


def upload(token, filename, content_type, data):
    boundary = "----toolbox" + secrets.token_hex(8)
    body = b"".join([
        ("--" + boundary + "\r\n").encode(),
        ('Content-Disposition: form-data; name="file"; filename="%s"\r\n' % filename).encode(),
        ("Content-Type: %s\r\n\r\n" % content_type).encode(),
        data,
        ("\r\n--" + boundary + "--\r\n").encode(),
    ])
    req = urllib.request.Request("http://127.0.0.1:8081/api/v1/admin/storage/objects", data=body,
                                 method="POST", headers={"Accept": "application/json",
                                                         "Content-Type": "multipart/form-data; boundary=" + boundary,
                                                         "Authorization": "Bearer " + token})
    try:
        response = urllib.request.urlopen(req, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, json.load(response)


def fetch_in_network(url):
    """Presigned URLs use the compose-internal MinIO host, so the check downloads inside the network."""
    script = ('code=$(curl -s -o /tmp/storage-check.bin -w "%{http_code}" "$1") '
              '&& hash=$(sha256sum /tmp/storage-check.bin | cut -d" " -f1) '
              '&& rm -f /tmp/storage-check.bin && printf "%s\\n%s\\n" "$code" "$hash"')
    result = subprocess.run([
        "docker", "compose", "--env-file", "../.env", "-f", "docker-compose.yml", "-f",
        "docker-compose.local.yml", "exec", "-T", "server", "sh", "-c", script, "sh", url],
        cwd=ROOT / "deploy", capture_output=True, text=True, check=False)
    parts = result.stdout.strip().splitlines()
    if len(parts) != 2:
        return 0, ""
    return int(parts[0]), parts[1]


def check(condition):
    global CHECKS
    assert condition, "Object storage check failed (sensitive response omitted)"
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
        print("Set ADMIN_CHECK_USERNAME and ADMIN_CHECK_PASSWORD to run the storage checks")
        return
    status, body = request("admin/auth/login",
                           {"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD}, method="POST")
    if status != 200 or body["code"] != 0:
        raise AssertionError(f"Administrator login failed (status={status}, code={body.get('code')})")
    token = body["data"]["accessToken"]
    keys = []
    try:
        check(request("admin/storage")[0] == 401)
        status, storage = request("admin/storage", token=token)
        check(status == 200 and storage["data"]["enabled"] is True)
        check(storage["data"]["bucket"] == "toolbox-files")
        check(storage["data"]["maxObjectBytes"] >= len(PNG))
        check("image/png" in storage["data"]["allowedContentTypes"])

        status, rejected = upload(token, "bad.zip", "application/zip", b"zipdata")
        check(status == 415 and rejected["code"] == 40002)
        status, empty = upload(token, "empty.png", "image/png", b"")
        check(status == 400 and empty["code"] == 10001)
        status, mismatched = upload(token, "fake.pdf", "application/pdf", PNG)
        check(status == 415 and mismatched["code"] == 40002)
        status, binary = upload(token, "binary.txt", "text/plain", b"\x00\x01\x02\x03")
        check(status == 415 and binary["code"] == 40002)
        text_payload = "file security check\n".encode()
        status, text_upload = upload(token, "note.txt", "text/plain", text_payload)
        check(status == 201 and text_upload["data"]["contentType"] == "text/plain"
              and text_upload["data"]["sha256"] == hashlib.sha256(text_payload).hexdigest())
        keys.append(text_upload["data"]["key"])

        status, uploaded = upload(token, "check.png", "image/png", PNG)
        check(status == 201 and uploaded["code"] == 0)
        stored = uploaded["data"]
        key = stored["key"]
        keys.append(key)
        check(key.startswith("objects/") and key.endswith(".png"))
        check(stored["size"] == len(PNG))
        check(stored["sha256"] == hashlib.sha256(PNG).hexdigest())
        check(stored["contentType"] == "image/png")

        status, presigned = request(f"admin/storage/objects/presigned?key={key}", token=token)
        check(status == 200 and presigned["data"]["url"].startswith("http://minio:9000/"))
        check(presigned["data"]["expiresInSeconds"] == 900)
        download_status, downloaded_hash = fetch_in_network(presigned["data"]["url"])
        check(download_status == 200 and downloaded_hash == hashlib.sha256(PNG).hexdigest())

        status, body = request("admin/storage/objects/presigned?key=../evil", token=token)
        check(status == 400 and body["code"] == 10001)
        status, body = request("admin/storage/objects/presigned?key=objects/missing.png", token=token)
        check(status == 404 and body["code"] == 10006)
        status, body = request(f"admin/storage/objects/presigned?key={key}&expirySeconds=10", token=token)
        check(status == 400 and body["code"] == 10001)

        status, _ = request(f"admin/storage/objects?key={key}", token=token, method="DELETE")
        check(status == 200)
        status, body = request(f"admin/storage/objects?key={key}", token=token, method="DELETE")
        check(status == 404 and body["code"] == 10006)
        status, body = request(f"admin/storage/objects/presigned?key={key}", token=token)
        check(status == 404 and body["code"] == 10006)
        gone_status, _ = fetch_in_network(presigned["data"]["url"])
        check(gone_status == 404)
        keys.remove(key)
        status, snapshot = request("admin/storage", token=token)
        check(status == 200 and snapshot["data"]["quotaEnabled"] is True)
        limits = snapshot["data"]
        check(limits["ownerUsageObjects"] == 1 and limits["ownerUsageBytes"] == len(text_payload))
        max_bytes = limits["maxOwnerBytes"]
        max_objects = limits["maxOwnerObjects"]
        if max_bytes <= 10_000:
            status, denied = upload(token, "quota.txt", "text/plain",
                                    b"q" * (max_bytes - limits["ownerUsageBytes"] + 1))
            check(status == 413 and denied["code"] == 40004)
        else:
            print("quota byte-denial check skipped: run with STORAGE_MAX_OWNER_BYTES=100")
        if max_objects <= 10:
            while True:
                status, snapshot = request("admin/storage", token=token)
                usage = snapshot["data"]
                if usage["ownerUsageObjects"] >= max_objects:
                    break
                status, extra = upload(token, "extra.txt", "text/plain", b"extra\n")
                check(status == 201)
                keys.append(extra["data"]["key"])
            status, denied = upload(token, "over.txt", "text/plain", b"over\n")
            check(status == 413 and denied["code"] == 40004)
        else:
            print("quota object-denial check skipped: run with STORAGE_MAX_OWNER_OBJECTS=3")
        for leftover in list(keys):
            status, _ = request(f"admin/storage/objects?key={leftover}", token=token, method="DELETE")
            check(status == 200)
            keys.remove(leftover)
        status, snapshot = request("admin/storage", token=token)
        check(snapshot["data"]["ownerUsageObjects"] == 0 and snapshot["data"]["ownerUsageBytes"] == 0)
        check(sql("SELECT COUNT(*) FROM stored_object WHERE deleted_at IS NULL") == "0")
        check(int(sql("SELECT COUNT(*) FROM stored_object")) >= 2)
        check(int(sql("SELECT COUNT(*) FROM admin_operation_log WHERE module='storage' AND result='SUCCESS'")) >= 2)
    finally:
        for leftover in keys:
            request(f"admin/storage/objects?key={leftover}", token=token, method="DELETE")
        print("cleanup storage audit rows:", sql("DELETE FROM admin_operation_log WHERE module='storage'"))
    print(f"{CHECKS} object storage checks passed")


if __name__ == "__main__":
    main()
