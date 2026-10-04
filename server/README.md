# Server API

服务端使用 Java 21、Spring Boot 3.5.16 和 Maven Wrapper 3.9.11。Springdoc 2.8.17 生成 OpenAPI；依赖版本由 Spring Boot BOM 管理。Flyway core/MySQL 模块固定 11.20.3，版本选择依据见数据库文档。

## 当前能力

- `GET /api/v1/health`：统一响应的健康检查，汇总 Actuator 状态。
- `GET /api/v1/tools`：匿名分页目录，支持分类筛选，过滤关闭、维护或软删除的工具及关闭/删除的分类；页大小上限 100，按排序值与编码稳定排序。实现及验证范围见 [目录接口说明](../docs/TOOL_CATALOG.md)。
- `GET /actuator/health`：基础设施健康检查，使用原生格式，只公开状态。
- 统一 JSON 响应与异常处理，参数错误、认证错误和容器错误沿用 API 契约。
- 每次请求生成 traceId，写入响应头、业务响应及 MDC；日志使用 ECS JSON 格式。
- Spring Security 无状态访问控制；其他路径默认要求身份认证，JWT 按 Auth & Sync 阶段接入。
- Swagger / OpenAPI 仅在显式启用 `local` 环境时开放。
- CORS 按环境变量中的精确来源白名单开放，预检先于认证处理；拒绝跨域访问时沿用 `403/10005` JSON 与 traceId。
- JDBC/HikariCP 连接 MySQL，启动时由 Flyway 校验并执行迁移；连接状态参与整体健康检查。
- Spring Data Redis/Lettuce 连接 Redis，使用环境中的凭据和明确超时；Redis 状态参与整体健康检查。

当前迁移创建工具分类与目录基础表，初始化 13 个分类，并通过 V3 登记已实现的本地计算器元数据；运算由 Android 执行。用户及其他业务表随对应功能新增迁移。Redis 客户端已配置；目录缓存、登录会话及限流按对应业务任务实现。结构及连接参数见 [DATABASE.md](../docs/DATABASE.md)，缓存约定见 [REDIS.md](../docs/REDIS.md)。

## 构建与启动

需要 JDK 21 和首次下载依赖的网络连接。设置 `JAVA_HOME` 指向 JDK；本机可使用 Android Studio 自带的 `D:\Android\Android Studio\jbr`。无需单独安装 Maven，Wrapper 会下载并校验固定版本。

先按 [部署说明](../deploy/README.md#本地启动)生成根目录 `.env`，并在 `deploy/` 启动 MySQL 和 Redis：

```sh
docker compose --env-file ../.env -f docker-compose.yml -f docker-compose.local.yml up -d --wait mysql redis
```

随后在 `server/` 目录运行。`local` profile 自动导入 `../.env`，复用 MySQL、Redis 凭据及本机端口；不需要把密码放进命令行。Windows：

```powershell
.\mvnw.cmd -B package
java -jar target/toolbox-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

macOS / Linux：

```sh
sh mvnw -B package
java -jar target/toolbox-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

默认端口为 `8080`，可通过 `SERVER_PORT` 环境变量或 `--server.port=8081` 参数覆盖。前台运行时按 `Ctrl+C` 停止服务。数据库不可用或迁移校验失败时，服务启动失败。Redis 使用按需连接，进程启动不代表 Redis 已就绪；数据库或 Redis 不可用时健康检查返回 `503`，Compose 的健康门禁会阻止编排就绪。

- 健康检查：[localhost:8080/api/v1/health](http://localhost:8080/api/v1/health)
- Swagger：[localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- OpenAPI：[localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

运行默认、`staging` 或 `production` 环境时，文档端点关闭。当前 API 契约见 [API.md](../docs/API.md)。

浏览器跨域访问配置 `CORS_ALLOWED_ORIGINS`，例如 `https://tool.zhzgo.cn`。多个来源用逗号分隔，填写包含协议的 Origin，省略路径和末尾斜杠；不接受通配符。默认空白名单，开发前端通过本地代理访问。当前使用 Bearer 认证规划，CORS 不允许 Cookie 凭据。Docker 构建和启动见 [部署说明](../deploy/README.md)。

## 包结构

```text
com.cangshuo.toolbox
├── common
│   ├── config       OpenAPI 元信息
│   ├── exception    统一错误及容器错误入口
│   ├── logging      请求 traceId 与 MDC
│   ├── response     统一响应模型
│   └── security     访问控制及 JSON 认证错误
├── health
│   ├── controller
│   ├── model
│   └── service
└── tool
    ├── controller
    ├── model
    ├── repository
    └── service
```

健康检查使用 Controller → Service 分层；目录使用 Controller → Service → Repository，通过参数化 JDBC 查询持久化数据，并在只读事务内读取分页数量和记录。列表 DTO 不暴露配置 JSON 或数据库主键，Android 对应传输模型已同步；详情、搜索、推荐、分类专用接口、客户端 JSON/网络接入和缓存按后续任务实现。

GitHub Actions 的 Server 任务使用 Maven Wrapper 打包并保存 JAR，当前显式跳过测试。工作流及验证范围见 [CI 说明](../docs/CI.md)。

## 本机构建与运行记录

2026-10-03 使用 JDK 21.0.9 和 Maven 3.9.11：

- `package -Dmaven.test.skip=true`：`BUILD SUCCESS`，生成可运行 JAR。
- `local` 环境启动成功；健康检查和 Actuator 返回 HTTP `200`、`UP`。
- OpenAPI 3.1 和 Swagger UI 可访问；错误响应采用统一 JSON，traceId 与响应头一致。
- HTTP 方法不支持返回 `405/10001`，匿名访问受保护路径返回 `401/10002`，不存在的公开资源返回 `404/10006`。
- 本次未建立或运行自动化测试。
- 新增 CORS 后重新打包与启动通过；官网来源及预检返回 `200`，非法来源返回 `403/10005`，受保护接口仍返回 `401/10002`，业务响应 traceId 与响应头一致。
- 接入 JDBC/Flyway 后，Docker 和 Windows 重新打包与启动通过；两份迁移成功执行，建立两张目录基础表及 13 个分类。随后重启校验成功、无需迁移；本机从私有 `.env` 读取配置并通过 TLS 连接 MySQL，健康检查返回 `200/0/UP`。详情见 [数据库运行记录](../docs/DATABASE.md#运行记录)。
- 接入 Redis 后，Docker 和 Windows 重新打包、启动及健康检查通过；Redis 显示两种运行方式的认证应用连接，本机、容器直连及 Nginx 代理均返回 `200/0/UP`。没有业务 Key 写入，数据库迁移及分类数据保持不变。详情见 [Redis 运行记录](../docs/REDIS.md#运行记录)。

本次本地预览在后台运行，PID 和日志位于 `target/run/local.pid`、`target/run/local.stdout.log`、`target/run/local.stderr.log`。需要退出预览或重新打包时，可在本目录执行：

```powershell
$apiProcessId = [int](Get-Content -LiteralPath 'target/run/local.pid')
$apiProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$apiProcessId"
$apiJar = (Resolve-Path -LiteralPath 'target/toolbox-api-0.1.0-SNAPSHOT.jar').Path
if ($apiProcess -and $apiProcess.CommandLine.Contains($apiJar)) {
    Stop-Process -Id $apiProcessId
}
```

2026-10-04 目录列表任务重新执行 Windows Maven 打包，编译和生成 JAR 成功，显式跳过测试。本机 local 预览已更新并启动，现有两份 Flyway 迁移校验通过，数据库版本仍为 2。Docker server 镜像也已重建并通过启动健康门禁；目录接口的分页、筛选、JSON 与错误分支尚未运行验证，完整记录见 [目录接口说明](../docs/TOOL_CATALOG.md#验证记录)。

同日计算器任务新增 V3 目录元数据迁移，Windows Maven 打包成功并显式跳过测试。本机 local 启动成功执行 V3；随后 Docker server 重建并通过启动健康门禁，日志确认三份迁移校验通过、数据库版本为 3。已有同编码记录的保留分支与目录响应尚未运行验证，详见 [计算器说明](../docs/CALCULATOR.md#验证记录)。

## 版本依据

- [Spring Boot 3.5.16 发行说明](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)
- [Springdoc 兼容矩阵](https://springdoc.org/v2/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot-)
- [Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/)

Spring Boot 3.5.x 沿用项目规格；官方已结束该版本线的开源支持，上线前需要单独评估支持策略或版本迁移。
