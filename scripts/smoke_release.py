"""Release smoke checks for one deployed API base URL (and optional website).

Read-only checks plus one disposable account that is registered with a
random address and deleted again at the end. Safe to run against staging and
production after a deploy; never prints passwords, tokens or raw responses.
"""
import argparse
import json
import secrets
import sys
import urllib.error
import urllib.request

CHECKS = 0


def check(condition, reason):
    global CHECKS
    assert condition, "Smoke check failed: " + reason
    CHECKS += 1


def call(base, path, method="GET", payload=None, token=None, origin=None, timeout=20):
    headers = {"Accept": "application/json"}
    if payload is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    if origin:
        headers["Origin"] = origin
    request = urllib.request.Request(
        base.rstrip("/") + path,
        data=None if payload is None else json.dumps(payload).encode(),
        headers=headers, method=method)
    try:
        response = urllib.request.urlopen(request, timeout=timeout)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read()
        try:
            body = json.loads(raw) if raw else None
        except ValueError:
            body = None
        return response.status, dict(response.headers), body


def page(url, timeout=20):
    request = urllib.request.Request(url, headers={"Accept": "text/html"})
    try:
        response = urllib.request.urlopen(request, timeout=timeout)
    except urllib.error.HTTPError as error:
        return error.status
    with response:
        return response.status


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--web-url", default="")
    parser.add_argument("--origin", default="")
    parser.add_argument("--timeout", type=int, default=20)
    args = parser.parse_args()

    base = args.base_url.rstrip("/")
    email = "smoke-" + secrets.token_hex(10) + "@example.invalid"
    password = secrets.token_urlsafe(24)
    token = None

    try:
        status, _, body = call(base, "/health", timeout=args.timeout)
        check(status == 200 and body and body.get("code") == 0 and body["data"]["status"] == "UP", "health")

        status, headers, body = call(base, "/tools?page=1&pageSize=1", timeout=args.timeout)
        check(status == 200 and body and body.get("code") == 0 and body["data"]["records"]
              and body["data"].get("total", 0) >= 1, "tool catalog")
        check("X-RateLimit-Limit" in headers and "X-RateLimit-Remaining" in headers, "rate limit headers")

        status, _, body = call(base, "/home/recommendations", timeout=args.timeout)
        check(status == 200 and body and body.get("code") == 0, "recommendations")
        status, _, body = call(base, "/home/announcements", timeout=args.timeout)
        check(status == 200 and body and body.get("code") == 0, "announcements")

        if args.origin:
            _, headers, _ = call(base, "/tools?page=1&pageSize=1", origin=args.origin, timeout=args.timeout)
            check(headers.get("Access-Control-Allow-Origin") == args.origin, "CORS origin")

        status, _, body = call(base, "/auth/register", "POST",
                               {"email": email, "password": password, "nickname": "smoke"}, timeout=args.timeout)
        check(status == 201 and body and body.get("code") == 0, "register")
        token = body["data"]["accessToken"]
        refresh = body["data"]["refreshToken"]

        status, _, body = call(base, "/auth/me", token=token, timeout=args.timeout)
        check(status == 200 and body and body["data"]["email"] == email, "me")

        status, _, body = call(base, "/auth/refresh", "POST", {"refreshToken": refresh}, timeout=args.timeout)
        check(status == 200 and body and body.get("code") == 0, "refresh")
        token = body["data"]["accessToken"]
        refresh = body["data"]["refreshToken"]

        status, _, _ = call(base, "/auth/logout", "POST", {"refreshToken": refresh}, timeout=args.timeout)
        check(status == 200, "logout")

        status, _, body = call(base, "/auth/login", "POST", {"email": email, "password": password}, timeout=args.timeout)
        check(status == 200 and body and body.get("code") == 0, "login")
        token = body["data"]["accessToken"]
        status, _, body = call(base, "/auth/me", token=token, timeout=args.timeout)
        check(status == 200 and body and body["data"]["email"] == email, "me after login")

        status, _, _ = call(base, "/auth/me", "DELETE", {"email": email, "password": password},
                            token=token, timeout=args.timeout)
        check(status == 200, "account deletion")
        token = None

        status, _, _ = call(base, "/auth/login", "POST", {"email": email, "password": password}, timeout=args.timeout)
        check(status == 401, "deleted account rejected")

        if args.web_url:
            web = args.web_url.rstrip("/")
            for path in ("/", "/privacy/", "/permissions/", "/account/delete/"):
                check(page(web + path, timeout=args.timeout) == 200, "web page " + path)
    finally:
        if token:
            try:
                call(base, "/auth/me", "DELETE", {"email": email, "password": password},
                     token=token, timeout=args.timeout)
            except Exception:
                pass
    print("Smoke release: {0}/{0} checks passed against {1}".format(CHECKS, base))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("Smoke release failed: " + (str(error) if str(error) else error.__class__.__name__), file=sys.stderr)
        sys.exit(1)
