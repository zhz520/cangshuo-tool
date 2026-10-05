# 部署配置

AI 文本使用兼容 OpenAI 的 Chat Completions 服务，默认 `AI_ENABLED=false`。启用前仅在 Server 私有环境配置 `AI_ENDPOINT`（完整 HTTPS `/chat/completions` 地址）、`AI_API_KEY`、`AI_MODEL` 和展示用 `AI_PROVIDER_NAME`；Compose 已透传。额度、令牌参数与超时见 [AI 文本助手](../docs/AI_TEXT.md)。Android 不接收密钥；真实上游尚未配置或验收。

账号接口新增必需环境变量 `JWT_SECRET`（随机 32 字节、64 位十六进制），仅供 Server；PowerShell/Shell 初始化脚本生成它。现有私有环境文件需补入独立随机值，不能复用数据库密码；缺失或格式错误时 API 启动失败。2026-10-05 本地私有 `.env` 已增补，Compose server 已重建运行，V18 用户表应用成功，见 [认证说明](../docs/AUTH.md)。

管理后台认证新增 `ADMIN_BOOTSTRAP_USERNAME`、`ADMIN_BOOTSTRAP_PASSWORD`（可选，仅在 `admin_user` 为空时创建首个超级管理员；账号 3–32 位、密码 12–72 位且至少包含一个字母和一个数字）和 `ADMIN_TOKEN_TTL_SECONDS`（默认 3600，允许 300–86400）。引导变量只放在私有环境文件或部署密钥管理中，不写入 Git；管理员已存在时启动会跳过创建。首个管理员删除后可用同一变量重新引导，见 [管理员认证](../docs/ADMIN.md)。

对象存储新增 `STORAGE_ENABLED`（默认 false）、`MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET`、`MINIO_PRESIGNED_EXPIRY_SECONDS`、`MINIO_MAX_OBJECT_BYTES` 与 `MINIO_ALLOWED_CONTENT_TYPES`；Compose 默认复用 `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD`。启用时必须同时启用 `storage` profile，生产环境要把 `MINIO_ENDPOINT` 配成客户端可解析的公开对象存储域名（预签名 URL 按该主机签名）。详见 [对象存储](../docs/STORAGE.md)。

使用 Docker Compose 编排 MySQL、Redis、Server 和 Admin；Admin 镜像同时提供 Nginx 网关及基础官网入口页。MinIO 放在开发用的可选 `storage` profile 中。镜像固定版本，凭据通过仓库根目录的私有环境文件配置。

本地与正式环境使用独立项目名、镜像名、网络和数据卷。2026-10-03 已使用 Docker Desktop 的 Linux Engine `29.8.1`、Compose `v5.5.1` 完成镜像构建和本地启动，四个核心服务及可选 MinIO 均健康。正式环境配置已准备，实际公网部署待完成。

## 文件与镜像

| 文件 | 用途 |
| --- | --- |
| `docker-compose.yml` | 共用服务、构建、健康检查、网络、数据卷与日志轮转 |
| `docker-compose.local.yml` | 本机端口及 `local` profile |
| `docker-compose.production.yml` | 两个域名的 HTTPS、证书挂载和正式 API 地址 |
| `nginx/` | 主配置、路由共用配置及环境模板 |
| `site/` | 基础官网入口页，明确显示开发状态 |
| `scripts/` | 随机生成环境凭据，保留已有文件 |
| `minio/Dockerfile` | 固定官方源码、提交和基础镜像摘要的 MinIO 开发镜像 |
| `../server/Dockerfile` | Maven Wrapper 构建与 Java 21 运行镜像 |
| `../admin/Dockerfile` | npm 锁文件构建与 Nginx 运行镜像 |

镜像基线：MySQL `8.4.11`、Redis `8.8.3-alpine3.23`、Temurin `21.0.12_8`（JDK/JRE Jammy）、Node `22.23.3-alpine3.24`、Nginx `1.30.5-alpine3.24`。MinIO 开发镜像使用官方源码 `RELEASE.2025-10-15T17-29-55Z`，Go `1.24.8-bookworm` 构建、Alpine `3.24` 运行，两个基础镜像均固定摘要。

Server 构建阶段安装 `unzip`，使 Maven Wrapper 使用配置中的 ZIP 分发包及其 SHA-256 校验值。没有 `unzip` 时，当前 Wrapper 会自动下载 tar 包，导致 ZIP 校验值不匹配。解压工具只存在于构建阶段；最终镜像使用 JRE。

Server 已配置 JDBC、Flyway 和 Spring Data Redis/Lettuce，应用启动时执行目录基础表和分类种子迁移，整体健康状态包含 MySQL、Redis 连接。Compose 等待 MySQL、Redis 及 API 的健康检查通过，再启动后台。缓存、会话及其他持久化业务按对应路线图任务实现；配置与规则见 [DATABASE.md](../docs/DATABASE.md)、[REDIS.md](../docs/REDIS.md)。

## 本地启动

需要 Docker Engine 的 Linux 容器环境和支持 `up --wait` 的 Compose。Windows 使用已启动的 Docker Desktop；Linux 使用 Docker Engine 和 Compose 插件。

在 `deploy/` 目录执行。首次使用时生成根目录 `.env`，Windows PowerShell：

```powershell
.\scripts\Initialize-Env.ps1
```

Linux / macOS（需要 OpenSSL）：

```sh
sh scripts/initialize-env.sh
```

本次开发已生成根目录 `.env`，已有文件时跳过初始化。脚本生成四组随机密码，不显示凭据，不覆盖已有文件。实际 `.env` 已被 Git 与 Docker 构建上下文忽略；提交的是 `.env.example`。

```sh
docker compose --env-file ../.env -f docker-compose.yml -f docker-compose.local.yml config --quiet
docker compose --env-file ../.env -f docker-compose.yml -f docker-compose.local.yml up -d --build --wait --wait-timeout 300
docker compose --env-file ../.env -f docker-compose.yml -f docker-compose.local.yml ps
```

| 入口 | 本地地址 |
| --- | --- |
| 官网入口 | `http://localhost:8088/` |
| 后台 | `http://localhost:8088/admin/` |
| 网页版工具（规划） | `http://localhost:8088/tools/<tool_code>/` |
| 经 Nginx 的 API | `http://localhost:8088/api/v1/health` |
| API 直连 | `http://localhost:8081/api/v1/health` |
| 本地 Swagger | `http://localhost:8081/swagger-ui/index.html` |
| MySQL | `127.0.0.1:3307` |
| Redis | `127.0.0.1:6380` |

本地端口只绑定 `127.0.0.1`，可通过 `.env` 的 `LOCAL_*_PORT` 修改。数据库用户名及库名默认均为 `toolbox`；密码读取自己的私有环境文件。Redis 密码使用十六进制字符串，初始化脚本会生成符合要求的值。

本地覆盖文件让 MySQL、Redis 和可选 MinIO 同时连接 `backend` 与 `local-access` 网络，使本机端口发布生效。生产配置使用内部 `backend` 网络，不加载这个开发访问网络。

查看日志及停止本地服务：

```sh
docker compose --env-file ../.env -f docker-compose.yml -f docker-compose.local.yml logs --tail 100 server admin
docker compose --env-file ../.env -f docker-compose.yml -f docker-compose.local.yml down
```

停止服务后数据卷保留。MySQL 密码用于首次创建数据库；已有数据卷的密码修改需要在数据库内执行对应操作。

### 可选 MinIO 开发环境

MinIO [官方社区仓库](https://github.com/minio/minio)已归档。实际拉取官方 Docker Hub/Quay 历史镜像失败，官方历史二进制下载返回 `410`，所以使用官方源码构建。锁定 [RELEASE.2025-10-15T17-29-55Z](https://github.com/minio/minio/releases/tag/RELEASE.2025-10-15T17-29-55Z) 及提交 `9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a`；该发布包含上一版本之后的安全修复。镜像携带原始许可证，并标记源码地址及版本。此镜像用于本机开发，生产对象存储方案在 Phase 6 确定。

启用时运行，首次源码编译需要下载 Go 依赖并等待构建，后续使用构建缓存：

```sh
docker compose --env-file ../.env -f docker-compose.yml -f docker-compose.local.yml --profile storage up -d --build --wait --wait-timeout 300 minio
```

本次本机 MinIO 已启动，开发 S3 地址为 `http://localhost:9000`，控制台为 `http://localhost:9001`；存活、就绪接口及控制台返回 `200`。Server 暂未接入对象存储，尚未验证文件业务。停止包含 MinIO 的环境时，使用同一组文件并加上 `--profile storage down`。

## 正式环境域名约定

域名由用户于 2026-10-03 确定，后台路径默认规划为 `/admin/`。

| 用途 | 正式地址 | 路由约定 |
| --- | --- | --- |
| 官网 | `https://tool.zhzgo.cn/` | 根路径提供官网 |
| 管理后台 | `https://tool.zhzgo.cn/admin/` | 后台页面及静态资源使用 `/admin/` 前缀 |
| API | `https://toolapi.zhzgo.cn/api/v1` | 保留 `/api/v1` 业务接口前缀 |
| 网页版工具（规划） | `https://tool.zhzgo.cn/tools/<tool_code>/` | 官网子路径，一个工具一个目录；静态资源使用相对路径 |

Android 和正式环境后台使用上述 API 基础地址。健康检查地址为 `https://toolapi.zhzgo.cn/api/v1/health`。

复杂或体积较大的工具可以把实现放在官网 `/tools/<tool_code>/`，由 App 内置 WebView（Chrome 内核）打开，规则见 `toolbox-vibe-spec/PROJECT_SPEC.md` Decision 019。上线具体工具时需在官网静态目录和 Nginx 增加 `/tools/` 路由、HTTPS、缓存与 MIME 配置；Android 基础地址由构建配置提供，工具路径由目录 `config_json` 下发，不在客户端写死。

## 正式环境启动

先在根目录生成独立的 `.env.production`，Windows 在 `deploy/` 运行：

```powershell
.\scripts\Initialize-Env.ps1 -FileName .env.production
```

Linux / macOS：

```sh
sh scripts/initialize-env.sh .env.production
```

生产项目名为 `cangshuo-toolbox-production`。需要完成两个域名的 DNS 解析并准备有效证书，文件布局如下，`certs/` 整体被 Git 和 Docker 构建上下文忽略：

```text
deploy/certs/
├── tool.zhzgo.cn/
│   ├── fullchain.pem
│   └── privkey.pem
└── toolapi.zhzgo.cn/
    ├── fullchain.pem
    └── privkey.pem
```

证书由现有证书工具或服务商申请和续期，包含对应域名。挂载目录必须真实存在；部署前完成证书准备。更新证书后，可使用同一组 Compose 参数执行 `exec admin nginx -t` 和 `exec admin nginx -s reload`。

```sh
docker compose --env-file ../.env.production -f docker-compose.yml -f docker-compose.production.yml config --quiet
docker compose --env-file ../.env.production -f docker-compose.yml -f docker-compose.production.yml up -d --build --wait --wait-timeout 300
docker compose --env-file ../.env.production -f docker-compose.yml -f docker-compose.production.yml ps
```

正式环境只映射 Nginx 的 `80/443`；API、MySQL 和 Redis 通过容器网络连接。基础官网已提供开发状态入口页，完整官网内容可在后续产品任务中扩展。

### 已实现的路由与配置

- Nginx 按两个域名分别提供官网/后台和 API，配置 HTTPS 及 HTTP 到 HTTPS 的跳转。
- 官网根路径与后台目录分别提供静态文件；`/admin` 跳转到 `/admin/`，后台页面路由回退到 `/admin/index.html`。
- 后台构建的 Vite `base` 使用 `/admin/`，Vue Router 沿用 `import.meta.env.BASE_URL`。开发环境使用根路径和本地代理。
- 正式环境后台构建配置 `VITE_API_BASE_URL=https://toolapi.zhzgo.cn/api/v1`。
- API 的 `CORS_ALLOWED_ORIGINS` 精确允许 `https://tool.zhzgo.cn`，允许常用业务请求方法和 `Authorization/Content-Type/Accept` 请求头，向浏览器暴露 `X-Trace-Id`。CORS 预检在认证之前处理；Origin 配置不包含 `/admin/`。
- 生产 Swagger / OpenAPI 沿用关闭策略。Nginx API 域名只代理 `/api/v1/`。

正式 API 地址在后台构建时写入产物；修改域名或地址后重新构建。Nginx 模板只替换 `WEB_DOMAIN/API_DOMAIN`，保留 `$uri` 等 Nginx 变量。网关访问日志记录方法、状态、耗时和上游 traceId，省略请求 URL、查询参数及请求头。

## 验证记录与待办

### 2026-10-05 本地真机接入

在 `android/` 构建 Debug 包时传入 `-PtoolboxDebugApiBaseUrl=http://127.0.0.1:8081/api/v1`；连接手机后执行 `adb reverse tcp:8081 tcp:8081` 和 `adb reverse tcp:8088 tcp:8088`。默认 API 地址仍是模拟器 10.0.2.2，参数只允许规定的本地 HTTP 主机/端口/路径，release 使用正式 HTTPS。

修改 `deploy/site/tools/` 或 Nginx 配置后，在 `deploy/` 用本地 Compose 参数执行 `build admin`、`up -d admin` 和 `exec -T admin nginx -t`。`/tools/` 返回 no-cache，App 禁止网页缓存，避免重新打开时仍执行旧逻辑。2026-10-05 镜像重建、配置检查及页面 200/no-cache 已通过。真机/浏览器覆盖和未验证范围见 [本轮记录](../docs/WEB_TOOL_ACCEPTANCE_2026-10-05.md)，本轮没有公网部署。

## 历史验证记录

2026-10-03：

- 使用官方 Compose `v5.6.0` 便携工具，核对 SHA-256 后通过本地配置（含 storage）与生产配置的 `config --quiet`。
- Admin 的本地及正式 API 构建均通过 TypeScript 检查和 Vite 构建；产物资源路径为 `/admin/`，正式产物包含约定的 API 地址。
- Server `package -Dmaven.test.skip=true` 成功；先解决已有预览进程占用 JAR 后恢复本地 API。
- Nginx `1.30.5` Windows 便携版校验两套配置通过。校验时替换容器路径、上游地址和端口，并使用临时证书；这些临时文件仅位于被忽略的 `tmp/`。
- 临时 Nginx 本机访问确认官网、后台和深层路由返回 `200`，`/admin` 跳转 `308`，JavaScript 与 favicon 正常，不存在的静态资源返回 `404`，API 代理与网关健康检查返回 `200`。临时 Nginx 已关闭。
- API 官网来源及预检返回 `200`；非法来源返回 `403/10005`，受保护接口仍返回 `401/10002`；业务响应 traceId 与响应头一致。
- 安装 Docker Desktop 后，使用 Linux Engine `29.8.1`、Compose `v5.5.1` 实际构建 API、官网/后台和 MinIO 镜像并启动编排；五个容器均通过健康检查。
- 修复服务端构建镜像缺少 `unzip` 导致的 Wrapper 校验失败，保留 Maven 分发包 SHA-256 校验；修复内部网络导致本机开发端口未发布的问题，以及本地 Nginx 相对路径跳转丢失映射端口的问题。
- 容器内 `nginx -t` 通过，MySQL 应用账号查询返回库名 `toolbox`、字符集 `utf8mb4`、排序规则 `utf8mb4_0900_ai_ci`，Redis 认证连接返回 `PONG`；本机 `3307/6380` 可连接。
- 经实际容器网关访问官网、`/admin/` 和深层路由返回 `200`，实际 JavaScript 与 favicon 返回 `200`，不存在的静态资源返回 `404`；`/admin`、`/api/v1` 返回 `308` 和正确的相对跳转路径。
- API 直连及代理健康检查返回 `200/0/UP`，CORS、认证错误及 traceId 契约正常；本地 Swagger/OpenAPI 返回 `200`。浏览器中的容器后台显示“平台服务运行正常”，控制台未记录警告或错误。
- MinIO 官方源码镜像版本/提交符合锁定值，容器健康，`/minio/health/live`、`/minio/health/ready` 及控制台返回 `200`。
- MySQL/Flyway 接入后重建并启动 Server 成功，执行目录基础表与 13 个分类迁移；再次启动校验两份迁移并报告 schema 版本 2、无需迁移。API 直连及 Nginx 代理健康检查返回 `200/0/UP`，五个容器仍健康；详细记录见 [数据库说明](../docs/DATABASE.md#运行记录)。
- Redis 客户端接入后重建并启动 Server 成功，容器与 Windows 本地 API 均已认证连接 Redis；API 三个入口返回 `200/0/UP`，五个容器健康。两套 Compose 配置校验通过，正式 Redis 端口保持未发布；详细记录见 [Redis 说明](../docs/REDIS.md#运行记录)。
- 未建立或运行自动化测试。

路线图中的 Docker Compose、MySQL/Flyway 和 Redis 客户端接入已完成。缓存、会话、限流及对象存储业务按后续任务开展，生产 DNS、真实证书及公网联调待实际部署。

GitHub Actions 使用公开占位值校验两套 Compose 配置及 Shell 语法，不启动环境。工作流及首次远程运行要求见 [CI 说明](../docs/CI.md)。

实现依据：[Docker Compose 启动顺序](https://docs.docker.com/compose/how-tos/startup-order/)、[Compose Profiles](https://docs.docker.com/compose/how-tos/profiles/)、[Vite 公共基础路径](https://vite.dev/guide/build.html#public-base-path)、[Spring Security 6.5 CORS](https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/cors.html)、[MinIO 官方镜像状态](https://hub.docker.com/r/minio/minio/tags?ordering=last_updated&page=1)。
