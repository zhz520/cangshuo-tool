"""Exercise the API rate limiter; never print credentials or tokens.

The script discovers the configured limits from the X-RateLimit-Limit response header, exhausts the
anonymous bucket for the caller address and then the stricter auth bucket, and asserts the unified
429 envelope plus Retry-After. Run it last in a verification batch: it intentionally rate-limits the
calling address for the configured window (set RATE_LIMIT_MAX_ANONYMOUS/AUTH_ATTEMPTS small to keep it fast).
"""
import json
import urllib.error
import urllib.request

CHECKS = 0


def request(path, payload=None, method=None, port=8081):
    headers = {"Accept": "application/json"}
    if payload is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(f"http://127.0.0.1:{port}/api/v1/{path}",
                                 data=None if payload is None else json.dumps(payload).encode(),
                                 headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = json.load(response)
        return response.status, response.headers, body


def check(condition):
    global CHECKS
    assert condition, "Rate limit check failed (sensitive response omitted)"
    CHECKS += 1


def main():
    status, headers, _ = request("tools?pageSize=1")
    check(status == 200 and headers["X-RateLimit-Limit"].isdigit())
    limit = int(headers["X-RateLimit-Limit"])
    check(limit >= 2 and headers["X-RateLimit-Remaining"] == str(limit - 1))
    for _ in range(limit - 1):
        status, _, _ = request("tools?pageSize=1")
        check(status == 200)
    status, headers, body = request("tools?pageSize=1")
    check(status == 429 and body["code"] == 10007 and int(headers["Retry-After"]) >= 1)
    check(headers["X-RateLimit-Remaining"] == "0")

    # Health stays exempt even while the caller address is limited.
    check(request("health")[0] == 200)

    status, headers, _ = request("auth/login", {"email": "rate-check@example.invalid",
                                                "password": "wrongpassword"}, method="POST")
    check(status == 401 and headers["X-RateLimit-Limit"].isdigit())
    auth_limit = int(headers["X-RateLimit-Limit"])
    check(auth_limit >= 2 and auth_limit < limit)
    for _ in range(auth_limit - 1):
        status, _, _ = request("auth/login", {"email": "rate-check@example.invalid",
                                             "password": "wrongpassword"}, method="POST")
        check(status == 401)
    status, headers, body = request("auth/login", {"email": "rate-check@example.invalid",
                                                   "password": "wrongpassword"}, method="POST")
    check(status == 429 and body["code"] == 10007 and int(headers["Retry-After"]) >= 1)
    print(f"{CHECKS} rate limit checks passed (anonymous limit {limit}, auth limit {auth_limit})")


if __name__ == "__main__":
    main()
