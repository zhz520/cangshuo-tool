# Internal media browser

An optional anonymous Douyin album fallback for `MediaParser`. The public page and Android WebView still use the existing API. Playwright 1.64.0 and its matching Chromium image are pinned together; no browser/runtime is added to the APK or downloaded by site visitors.

Enable `docker-compose.media.yml` alongside the normal deployment overlays. It creates a private `media` network, attaches the API, and runs a browser without host ports, CDP access, accounts or persistent volumes. The only internal operations are `GET /health` and `GET /douyin/<numeric-work-id>`; a caller cannot submit a URL or script.

```sh
docker compose --env-file ../.env.production -f docker-compose.yml -f docker-compose.baota.yml -f docker-compose.media.yml up -d --build --wait media-browser server admin
```

`MEDIA_BROWSER_URL` defaults to `http://media-browser:8080` in the overlay. Without the overlay/configuration the Java fallback is disabled. Continue including this overlay in subsequent updates. `MEDIA_ENABLED=false` still stops the public parser and downloads.

The browser uses a non-root user, read-only filesystem, tmpfs, dropped capabilities, no-new-privileges and the Playwright seccomp profile. A loopback-only CONNECT proxy validates both the platform hostname and all resolved public IPs, then connects to the checked IP. Only HTTPS/443 is allowed, media streams and WebSockets are blocked, and the browser network is separate from the database/Redis network. Java separately validates all extracted media/CDN addresses.

Chromium's process sandbox is enabled by default (`MEDIA_BROWSER_SANDBOX=true`). Hosts must support unprivileged user namespaces. The current deployment host has kernel `3.10.0-1160.119.1.el7.x86_64` and `user.max_user_namespaces=0`; `unshare -Ur` was rejected. On that host only, `.env.production` uses `MEDIA_BROWSER_SANDBOX=false` inside the isolated container. This reduces browser process isolation; the container restrictions and public-IP proxy stay enforced. No host sysctl or privileged capability is changed. Prefer enabling the process sandbox after upgrading the host. See the [official Playwright container guidance](https://playwright.dev/docs/docker).

Limits: one persistent anonymous context, two concurrent pages, 20 seconds per work, 50 images, 512 KB typed response, 1 GB RAM, one CPU, 256 PIDs and bounded logs. Cookies only live in process memory. Warmup visits the public homepage once to establish normal guest state. Extraction requires the targeted player React props and a complete ordered image list; recommended thumbnails or partial lazy DOM galleries never count as success. Login, captcha, removed/private content and platform restrictions remain errors; there is no captcha solver or rotating proxy pool.

The unchanged seccomp file comes from [microsoft/playwright v1.64.0](https://github.com/microsoft/playwright/blob/v1.64.0/utils/docker/seccomp_profile.json); `LICENSE.playwright.txt` retains its Apache-2.0 license. Browser extraction was independently implemented after reviewing [hiyufan/shizhen](https://github.com/hiyufan/shizhen/blob/fe6c698de4111a1511258d6054f9a5efe5efa48d/src/parse_video_py/parser/douyin.py) (MIT). Source details and real validation are recorded in [SHORT_VIDEO_PARSE.md](../../docs/SHORT_VIDEO_PARSE.md).
