# 宝塔服务器正式部署教程（沧烁工具箱）

2026-10-06。适用场景：已有宝塔面板的 Linux 云服务器，部署官网（tool.zhzgo.cn）、管理后台（/admin/）和 API（toolapi.zhzgo.cn）。

## 0. 先回答两个常见疑问

**127.0.0.1 是哪台机器？** 是服务器自己。宝塔 nginx 和 Docker 容器都跑在同一台服务器上，`127.0.0.1:18080` 只是「同一台服务器内部」的地址，不是你的个人电脑。个人电脑关机、断网都不影响线上服务。

**服务器重启了怎么办？** 不需要人工操作：容器使用 `restart: unless-stopped`，Docker 与宝塔 nginx 都是 systemd 开机自启，数据在 Docker 数据卷里持久化。重启后等 1–2 分钟，按 §8.2 自检即可。

## 1. 部署架构与两种模式

模式 A（本教程主线，推荐宝塔用户）的数据流：

```
访客 / Android App
        │ HTTPS 443
        ▼
宝塔 nginx（边缘：证书、强制 HTTPS、反向代理）
        │ http://127.0.0.1:18080（仅本机可达）
        ▼
web 容器 nginx（官网 / 管理后台 / /tools/ 工具页，并转发 /api/v1）
        │ http://server:8080（Docker 内部网络）
        ▼
API 容器（Spring Boot）─── mysql / redis（仅 Docker 内网）
```

| | 模式 A：宝塔反代（推荐） | 模式 B：容器直接占 80/443 |
| --- | --- | --- |
| 边缘 | 宝塔 nginx，SSL 一键申请与自动续期 | 仓库自带 nginx 容器 |
| 是否要停宝塔 nginx | 不用 | 要（否则端口冲突） |
| 证书 | 宝塔管理 | 手动放进 `deploy/certs/`，自行续期 |
| 启动文件对 | `docker-compose.yml` + `docker-compose.baota.yml` | `docker-compose.yml` + `docker-compose.production.yml` |
| 适合 | 想继续用宝塔站点/SSL/防火墙 | 想要最短链路，不用宝塔网站功能 |

模式 B 见 §11。两种模式的容器、数据库、备份完全一致，只换边缘。

## 2. 服务器、系统与域名准备

- 配置：最低 2 核 4G（构建阶段建议临时加 2G swap），推荐 4 核 8G；系统盘 ≥ 40G。
- 只有 2G 内存也能装上，但首次启动和日常运行会明显吃紧，整机可能变慢甚至像“卡死”；建议至少 4G，坚持 2G 时按 §5 限制 JVM 堆并保留 swap。
- 系统：Ubuntu 22.04/24.04、Debian 12、AlmaLinux 9 等宝塔支持版本均可。
- 域名解析（在域名服务商处配置 A 记录到服务器公网 IP）：
  - `tool.zhzgo.cn`（官网）
  - `toolapi.zhzgo.cn`（API，Android App 也连这个）
- 生效检查：`dig +short tool.zhzgo.cn` 返回你的服务器 IP。
- 时区：`timedatectl set-timezone Asia/Shanghai`（容器日志本身是 UTC，属正常）。

## 3. 安装 Docker、配置镜像加速与放行端口

3.1 安装 Docker（二选一）

- 宝塔「软件商店」安装 **Docker管理器**，在管理器内安装 Docker 引擎与 Compose；或
- SSH 执行官方脚本：

```sh
curl -fsSL https://get.docker.com | bash -s docker --mirror Aliyun
systemctl enable --now docker
```

3.2 校验版本（脚本用到 `up --wait`，需要 Compose ≥ 2.17，建议 ≥ 2.20）

```sh
docker --version
docker compose version
```

3.3 镜像加速（国内服务器强烈建议）：宝塔 Docker 管理器 → 设置 → 镜像加速，或编辑 `/etc/docker/daemon.json` 后 `systemctl restart docker`。首次构建需要拉取 `eclipse-temurin`、`node`、`nginx`、`mysql`、`redis` 等基础镜像，慢或超时基本都是这里的问题。

注意：不要照抄云厂商内网镜像地址（例如腾讯云的 `mirror.ccs.tencentyun.com` 只在腾讯云内网可用，在阿里云等其他云上会 DNS 解析失败、所有镜像都拉不动）；请使用实测可用的公共镜像源，或控制台里提供给你的专属加速地址。

3.4 放行端口（**宝塔「安全」页和云厂商安全组都要**）：`80`、`443`、你的宝塔面板端口、SSH 端口。

不要放行 `18080`、`3306`、`6379`：18080 只绑定在 `127.0.0.1`，MySQL/Redis 只在 Docker 内网，从公网本来就访问不到，这是有意设计。

## 4. 拉取代码并生成配置

```sh
mkdir -p /www/wwwroot && cd /www/wwwroot
git clone https://github.com/zhz520/cangshuo-tool.git cangshuo-toolbox
cd cangshuo-toolbox
sh deploy/scripts/initialize-env.sh .env.production
```

脚本基于 `.env.example` 生成 `.env.production`，并随机生成 MySQL/Redis/MinIO 密码与 `JWT_SECRET`，同时把 `COMPOSE_PROJECT_NAME` 设为 `cangshuo-toolbox-production`。已存在同名文件时脚本会拒绝覆盖，避免弄丢旧密钥。

用宝塔文件管理器或 `vim` 编辑 `.env.production`，重点项：

| 配置 | 建议 |
| --- | --- |
| `WEB_DOMAIN` / `API_DOMAIN` | `tool.zhzgo.cn` / `toolapi.zhzgo.cn`（默认值即正确） |
| `CORS_ALLOWED_ORIGINS` | `https://tool.zhzgo.cn`（默认值即正确） |
| `ADMIN_BOOTSTRAP_USERNAME` / `ADMIN_BOOTSTRAP_PASSWORD` | 首次启动创建管理员，两个必须同时设置 |
| `AI_ENABLED` / `STORAGE_ENABLED` | 先保持 `false`，需要时再开 |
| MySQL/Redis/MinIO 密码、`JWT_SECRET` | 保持脚本生成的随机值 |

```sh
chmod 600 .env.production
sed -i 's/\r$//' .env.production
```

这个文件包含全部密钥：不进 Git、权限 600、单独备份到安全位置。

## 5. 构建镜像

```sh
cd /www/wwwroot/cangshuo-toolbox/deploy
docker compose --env-file ../.env.production -f docker-compose.yml -f docker-compose.baota.yml build server admin
```

- 首次构建会拉取基础镜像并编译 Server（Maven）与 Admin（npm），2 核 4G 大约 10–25 分钟；内存不足先加 swap：

```sh
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

- 小内存机器（2G）：在 `.env.production` 追加一行 `JAVA_TOOL_OPTIONS=-Xmx512m -Dfile.encoding=UTF-8` 限制 API 堆内存（默认是容器内存的 75%），并在宝塔软件商店停掉用不到的 MySQL/PHP 等服务；4G 及以上不需要改。
- 官网静态页与 nginx 配置都打包在 web 镜像里，所以改动它们后要重建 `admin` 镜像；只改 `deploy/nginx/templates/baota.conf.template` 或 `deploy/nginx/common/api-locations-baota.conf`（这两个文件以挂载方式使用）时，`restart admin` 即可生效。
- 宝塔模式下 Admin 前端会按 `.env.production` 的 `API_DOMAIN` 构建成 `https://<API_DOMAIN>/api/v1`，请确保该值与实际域名一致。

## 6. 启动整套服务

```sh
docker compose --env-file ../.env.production -f docker-compose.yml -f docker-compose.baota.yml up -d --wait --wait-timeout 300
```

- 会启动 `mysql`、`redis`、`server`（API）、`admin`（web + 官网）；首次启动初始化 MySQL 并执行 Flyway 迁移，通常 1–3 分钟。
- 还没配宝塔反代时，可先在服务器本机自检：

```sh
curl -sS -H 'Host: toolapi.zhzgo.cn' http://127.0.0.1:18080/api/v1/health
curl -sS -o /dev/null -w '%{http_code}\n' -H 'Host: tool.zhzgo.cn' http://127.0.0.1:18080/
```

期望：health 返回 `{"code":0,...,"data":{"status":"UP"}}`；官网返回 `200`。（`curl` 加 `-H 'Host: ...'` 是模拟宝塔转发过来的域名。）

状态与日志：

```sh
docker compose --env-file ../.env.production -f docker-compose.yml -f docker-compose.baota.yml ps
docker compose --env-file ../.env.production -f docker-compose.yml -f docker-compose.baota.yml logs -f --tail 100 server
```

## 7. 宝塔站点与反向代理（模式 A 核心步骤）

两个域名各做一遍，以 `tool.zhzgo.cn` 为例：

1. 宝塔 →「网站」→「添加站点」：域名 `tool.zhzgo.cn`；根目录任意（如 `/www/wwwroot/sites/tool.zhzgo.cn`）；PHP 版本选「纯静态」；不要创建数据库（我们的数据在 Docker 里）。
2. 进入该站点 →「反向代理」→「添加反向代理」：
   - 代理名称：`toolbox`
   - 目标 URL：`http://127.0.0.1:18080`
   - **发送域名：`$host`（关键）**，容器按域名分流，不能改写成 `127.0.0.1`
   - 缓存：关闭
3. 同站点 →「SSL」→ 申请 Let's Encrypt 证书（或 DNS 验证），然后开启「强制 HTTPS」。
4. 对 `toolapi.zhzgo.cn` 重复 1–3 步。
5. 打开站点「反向代理 → 配置文件」，确认包含：

```nginx
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
```

- `Host` 不对 → 容器返回 444/404；
- 缺 `X-Real-IP` → 所有访客共用一个限流身份，容易被误判为 429。

宝塔默认反代模板已包含这四行；若你的版本缺失，手工补齐保存即可（宝塔会自动 reload nginx）。

6. 若安装过「Nginx 防火墙 / 网站防火墙」类插件：请对这两个站点关闭防护，或把 `/api/v1` 加入白名单，避免 POST/DELETE 被拦截。
7. 外网验证：

```sh
curl -sS https://toolapi.zhzgo.cn/api/v1/health
```

浏览器打开：https://tool.zhzgo.cn/ 、https://tool.zhzgo.cn/privacy/ 、https://tool.zhzgo.cn/admin/ 。

## 8. 验收与日常运维

### 8.1 完整发布冒烟（18 项）

```sh
cd /www/wwwroot/cangshuo-toolbox
python3 scripts/smoke_release.py --base-url https://toolapi.zhzgo.cn/api/v1 --web-url https://tool.zhzgo.cn --origin https://tool.zhzgo.cn
```

期望输出 `Smoke release: 18/18 checks passed`。冒烟会注册一个随机一次性账号，走完注册→登录→刷新→登出→注销，并检查官网页面、限流响应头与 CORS，因此也能验证证书和反代链路。服务器没有 `python3` 时先安装（Ubuntu/Debian：`apt install -y python3`；CentOS/Alma：`dnf install -y python3`）。

DNS/证书还没生效时，可以先绑定 Host 头对本机 18080 做同样验证：

```sh
python3 scripts/smoke_release.py --base-url http://127.0.0.1:18080/api/v1 --web-url http://127.0.0.1:18080 --api-host toolapi.zhzgo.cn --web-host tool.zhzgo.cn --origin https://tool.zhzgo.cn
```

### 8.2 服务器重启后的自检

```sh
cd /www/wwwroot/cangshuo-toolbox/deploy
docker compose --env-file ../.env.production -f docker-compose.yml -f docker-compose.baota.yml ps
curl -sS -H 'Host: toolapi.zhzgo.cn' http://127.0.0.1:18080/api/v1/health
```

四个容器都应为 `healthy`；如果 `admin` 是 `unhealthy`，看 `logs admin`（最常见是模板文件缺失或端口被占用）。

### 8.3 更新版本（快速路径）

```sh
cd /www/wwwroot/cangshuo-toolbox && git pull
cd deploy
docker compose --env-file ../.env.production -f docker-compose.yml -f docker-compose.baota.yml build server admin
docker compose --env-file ../.env.production -f docker-compose.yml -f docker-compose.baota.yml up -d --wait --wait-timeout 300
python3 ../scripts/smoke_release.py --base-url https://toolapi.zhzgo.cn/api/v1 --web-url https://tool.zhzgo.cn --origin https://tool.zhzgo.cn
```

Flyway 迁移只向前执行：如果新版本包含不兼容的数据库变更，出问题要配合备份恢复（§9），不要手改 schema 回退。

### 8.4 带预发验证的灰度发布（可选，需额外内存）

```sh
cd /www/wwwroot/cangshuo-toolbox/deploy
export PROD_PROJECT=cangshuo-toolbox-production
export STAGING_PROJECT=cangshuo-toolbox-staging
export PROD_ENV_FILE=../.env.production
export STAGING_ENV_FILE=/www/wwwroot/cangshuo-toolbox/deploy/staging.env
export PROD_COMPOSE_FILES='-f docker-compose.yml -f docker-compose.baota.yml'
export STAGING_COMPOSE_FILES='-f docker-compose.yml -f docker-compose.local.yml'
export PROD_API_URL=https://toolapi.zhzgo.cn/api/v1
export PROD_WEB_URL=https://tool.zhzgo.cn
export STAGING_API_URL=http://127.0.0.1:18081/api/v1
export STAGING_WEB_URL=http://127.0.0.1:18088
sh ./release/release.sh build --tag 0.2.0
sh ./release/release.sh stage --tag 0.2.0
sh ./release/release.sh rollback
```

`stage` 会先用独立数据卷的预发栈跑冒烟，通过后才切换正式；正式冒烟失败会尝试自动回滚，`rollback` 是手动回滚到上一版本。`staging.env` 由 `.env.production` 复制并修改端口即可（`LOCAL_API_PORT=18081`、`LOCAL_WEB_PORT=18088`、`LOCAL_MYSQL_PORT=13307`、`LOCAL_REDIS_PORT=16380`）。资源不足时跳过此节，用 8.3；机制细节见 [灰度发布与回滚](STAGED_RELEASE.md)。

### 8.5 停止与启动

```sh
docker compose --env-file ../.env.production -f docker-compose.yml -f docker-compose.baota.yml stop
docker compose --env-file ../.env.production -f docker-compose.yml -f docker-compose.baota.yml up -d --wait
docker compose --env-file ../.env.production -f docker-compose.yml -f docker-compose.baota.yml down
```

**不要用 `down -v`**，那会删除 MySQL/Redis 数据卷。

## 9. 定时备份

准备备份目录与口令文件（口令单独保管，不要和备份放在一起）：

```sh
mkdir -p /www/backup/cangshuo-toolbox /www/wwwroot/cangshuo-toolbox/secrets
printf '%s' '替换成一条足够长的随机口令' > /www/wwwroot/cangshuo-toolbox/secrets/backup.pass
chmod 600 /www/wwwroot/cangshuo-toolbox/secrets/backup.pass
```

宝塔「计划任务」→ Shell 脚本，每日 02:17 全量备份：

```sh
cd /www/wwwroot/cangshuo-toolbox/deploy && COMPOSE_ENV_FILE=../.env.production COMPOSE_FILES='-f docker-compose.yml -f docker-compose.baota.yml' BACKUP_DIR=/www/backup/cangshuo-toolbox BACKUP_PASSPHRASE_FILE=/www/wwwroot/cangshuo-toolbox/secrets/backup.pass sh ./backup/backup.sh >> /www/backup/cangshuo-backup.log 2>&1
```

每 15 分钟导出一次删除台账（很小，但决定恢复后账号是否会被错误复活）：

```sh
cd /www/wwwroot/cangshuo-toolbox/deploy && COMPOSE_ENV_FILE=../.env.production COMPOSE_FILES='-f docker-compose.yml -f docker-compose.baota.yml' BACKUP_DIR=/www/backup/cangshuo-toolbox BACKUP_PASSPHRASE_FILE=/www/wwwroot/cangshuo-toolbox/secrets/backup.pass sh ./backup/backup.sh --ledger-only >> /www/backup/cangshuo-backup.log 2>&1

注意：宝塔模式必须带上 `COMPOSE_FILES='-f docker-compose.yml -f docker-compose.baota.yml'`；不带的话备份/恢复脚本会按默认的 `production` 组合执行，恢复时会尝试占用 80/443 并与宝塔 nginx 冲突。
```

- 备份目录必须放在网站不可访问的位置（不要放进 `/www/wwwroot/sites/...`）。
- 再把 `/www/backup/cangshuo-toolbox` 同步到另一台机器或对象存储（宝塔计划任务里加 rsync 命令即可）。
- 恢复（会短暂停 API 并回滚到备份时刻，操作前先读 [生产备份与恢复](BACKUP.md)）：

```sh
cd /www/wwwroot/cangshuo-toolbox/deploy
COMPOSE_ENV_FILE=../.env.production COMPOSE_FILES='-f docker-compose.yml -f docker-compose.baota.yml' BACKUP_PASSPHRASE_FILE=/www/wwwroot/cangshuo-toolbox/secrets/backup.pass sh ./backup/restore.sh --backup /www/backup/cangshuo-toolbox/toolbox-XXXXXXXXTXXXXXXZ.tar.gz.enc --confirm
```

## 10. 安全清单

- 宝塔面板：改默认端口、强密码、开启面板 SSL、限制访问 IP、开启二次验证；不需要的软件（PHP 等）可以不装或卸载。
- SSH：密钥登录、改端口、必要时禁用密码登录，配合 fail2ban。
- 公网只开 80/443（面板/SSH 限来源 IP）；Docker 不要暴露 `2375` 端口。
- `.env.production`、`secrets/backup.pass` 权限 600；密钥不贴聊天/工单、不进 Git。
- 定期更新：宝塔面板、系统补丁、`git pull` + 重建镜像。

## 11. 模式 B：容器直接占用 80/443（不用宝塔反代）

1. 让出端口（宝塔面板可能尝试恢复 nginx，请在面板设置里同时关闭 nginx 的自动启动/守护）：

```sh
systemctl stop nginx && systemctl disable nginx
```

2. 放置证书（宝塔申请过的话，证书在 `/www/server/panel/vhost/cert/<站点>/`，复制过来即可）：

```
deploy/certs/tool.zhzgo.cn/fullchain.pem
deploy/certs/tool.zhzgo.cn/privkey.pem
deploy/certs/toolapi.zhzgo.cn/fullchain.pem
deploy/certs/toolapi.zhzgo.cn/privkey.pem
```

3. 启动：

```sh
cd /www/wwwroot/cangshuo-toolbox/deploy
docker compose --env-file ../.env.production -f docker-compose.yml -f docker-compose.production.yml up -d --wait --wait-timeout 300
```

4. 该模式下证书续期需要自己把新证书复制进 `deploy/certs/` 并执行 `docker compose ... exec admin nginx -s reload`；`release.sh` 的证书预检也只在这种模式下检查仓库证书。

## 12. 常见问题排查

| 现象 | 排查 |
| --- | --- |
| 宝塔 502 Bad Gateway | 容器没起或健康检查失败：`docker compose ... ps`、`... logs --tail 100 admin server`；确认 `ss -lntp` 里 18080 有监听 |
| 访问返回 444 或 404 | 宝塔反代没保留域名：发送域名改为 `$host` |
| 大量访客 429 | 反代缺 `X-Real-IP`（容器用它做限流身份），按 §7 第 5 步补齐 |
| 页面一直跳 http / 跳转循环 | 检查站点已开强制 HTTPS，反代带 `X-Forwarded-Proto $scheme`；模式 A 容器本身不做跳转 |
| 构建拉镜像慢/失败 | 配置镜像加速；重试；或本地 `docker save` 后上传 `docker load` |
| 构建内存不足 / 被杀 | 按 §5 加 swap，或换 4G+ 内存机器 |
| MySQL 首次启动失败 | `... logs mysql`；确认磁盘空间、`.env.production` 密码非空；首次初始化要 30–60 秒 |
| 证书续期后异常 | 模式 A 由宝塔自动 reload；模式 B 需手动替换证书并 reload |
| `docker compose` 命令找不到 | 安装 Compose 插件（宝塔 Docker 管理器或官方 docker-compose-plugin） |
| admin 容器一直 `unhealthy`，日志出现 `pwrite() ... failed (1: Operation not permitted)` | 宿主机 seccomp 策略拦截 nginx 写 pid 文件。`docker-compose.baota.yml` 已给 admin 容器加 `security_opt: seccomp=unconfined`；确认该行存在后执行 `up -d --force-recreate admin` |
| 服务器整体卡顿 / SSH 很慢 / VNC 控制台像卡死 | 多半是内存或磁盘吃满：`free -h`、`df -h /`、`uptime`、`dmesg -T \| grep -iE 'oom\|killed process'`；按 §5 加 swap、限制 JVM、升级内存；磁盘满时先 `docker system df` 再 `docker builder prune -f` |
| 被云控制台强制重启后 | 容器 `restart: unless-stopped` 会自动恢复；`docker compose ... up -d --wait --wait-timeout 300` 再确认一次即可，不需要重新 build |

## 13. 本机验证记录（2026-10-06）

开发机用与生产相同的文件对真实启动宝塔模式栈（绑定 `127.0.0.1:18080`、独立数据卷）并通过 16/16 项检查：

- 配置解析：Compose 配置有效，Admin 构建参数解析为 `https://toolapi.zhzgo.cn/api/v1`；
- 域名分流：官网 `/`、`/privacy/`、`/permissions/`、`/account/delete/`、`/admin/`、`/tools/qr_studio/` 均 200；API 域名 `/api/v1/health` 200；同路径在官网域名下返回 404（域名分离）；
- 未知 Host 返回 444；`/admin` → 308 `/admin/`（相对跳转，不泄露内部端口）；
- 限流身份：同一 `X-Real-IP` 连续请求剩余额度递减 1，不同 IP 各自独立（验证反代场景不会把所有人合并成一个身份）；
- 完整发布冒烟 18/18（注册→登录→刷新→登出→注销一次性账号、官网页面、限流头、CORS）。

未在本机验证、需要你在服务器上执行的部分：真实 DNS 解析、宝塔面板 UI 操作与 Let's Encrypt 签发、云安全组、外网 HTTPS 端到端。执行 §7 和 §8.1 即可覆盖这些。

相关文档：[发布构建](RELEASE.md)、[灰度发布与回滚](STAGED_RELEASE.md)、[生产备份与恢复](BACKUP.md)、[隐私政策](PRIVACY_POLICY.md)、[权限说明](PERMISSIONS.md)。
