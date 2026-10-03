# Redis 连接与缓存约定

Phase 0 接入 Spring Data Redis `3.5.13` 和 Lettuce `6.6.0.RELEASE`，依赖版本由 Spring Boot `3.5.16` BOM 管理。Redis 容器沿用固定镜像 `8.8.3-alpine3.23`。

## 当前范围

服务端配置认证连接、超时和健康检查，后续 Repository 可注入自动配置的 `StringRedisTemplate`。当前没有业务缓存写入、缓存管理 API、登录会话、验证码或限流实现；这些能力随对应路线图任务开发。MySQL 继续保存业务主数据，Redis 不作为最终业务数据源。

本次没有数据库结构变更，也没有新增 Flyway migration。健康接口响应字段保持原有契约，Android/Admin 继续消费同一健康状态。

## 连接配置

| 变量 | 默认/来源 | 用途 |
| --- | --- | --- |
| `REDIS_HOST` | `127.0.0.1`；Compose 设置为 `redis` | 单节点地址 |
| `REDIS_PORT` | `LOCAL_REDIS_PORT` 或 6379；Compose 设置为 6379 | 连接端口 |
| `REDIS_USERNAME` | `default` | Redis ACL 用户；当前容器使用默认用户 |
| `REDIS_PASSWORD` | 必需，无内置值 | 从私有环境文件或环境变量读取 |
| `REDIS_DATABASE` | `0` | 单节点逻辑库编号 |
| `REDIS_SSL_ENABLED` | `false` | 当前容器内部网络连接；外部 TLS Redis 配置为 true |

本机在 `server/` 工作目录启用 `local` profile，自动导入根目录私有 `.env`，使用 `LOCAL_REDIS_PORT=6380`。Compose 注入 `REDIS_*` 环境变量并使用容器内部端口。环境变量优先于文件；默认及正式 profile 不导入开发文件。

连接超时和命令超时均为 2 秒，Lettuce 关闭超时为 100 毫秒。客户端名为 `cangshuo-toolbox-api`，便于识别应用连接。使用 Lettuce 共享连接，关闭连接池和 Redis Repository 自动扫描；当前没有 Redis 实体或额外连接池依赖。

现有 Compose Redis 启用认证及 AOF，数据位于独立命名卷。本机端口只发布到 `127.0.0.1:6380`，正式环境不发布 Redis 端口。容器初始化脚本要求密码为非空十六进制值，现有环境初始化脚本已生成符合要求的随机凭据。服务端不会打印凭据，也不使用携带密码的连接 URL。

## 健康检查

Actuator 自动检查 Redis 连接并加入整体状态；检查不写业务 Key。`/actuator/health` 只公开整体状态，`/api/v1/health` 沿用统一响应：

- 全部组件正常：HTTP `200`、业务码 `0`、`data.status=UP`。
- MySQL、Redis 或其他健康组件异常：HTTP `503`、业务码 `10008`、`data=null`，包含 traceId。

Redis 按需连接，API 进程启动不代表 Redis 已就绪。Compose 等待 API 的 Actuator 健康检查通过后才完成就绪门禁；本机开发也应以健康响应为准。依赖恢复后，后续健康请求重新检查实际连接状态。

## 业务接入约定

业务 Controller 通过 Service 调用 Repository；Redis 操作集中在相应 Repository。使用 `StringRedisTemplate` 存储 UTF-8 字符串，结构化值由项目的 ObjectMapper 按明确 DTO 转为 JSON；不使用 Java 原生对象序列化或允许任意类名的反序列化。

所有 Key 使用 `toolbox:` 前缀。以下是规格约定的未来 Key，当前没有创建这些业务数据：

| Key | 用途 |
| --- | --- |
| `toolbox:tool:list`、`toolbox:tool:{code}` | 工具目录及详情缓存 |
| `toolbox:category:list` | 分类缓存 |
| `toolbox:home:featured`、`toolbox:banner:active` | 首页推荐及活动配置 |
| `toolbox:user:session:{sessionId}`、`toolbox:refresh:{sessionId}` | 会话与刷新状态 |
| `toolbox:login:rate:{ip}`、`toolbox:login:rate:{account}` | 登录限制，标识处理规则在认证任务确定 |
| `toolbox:rate:{userId}:{api}` | 业务限流窗口 |

临时 Key 必须有明确、正数 TTL；写值与设置过期时间应为同一条命令或原子脚本，不能先 SET 再 EXPIRE。缓存失效、TTL、并发更新和失败处理随实际业务一起实现，当前没有全局默认 TTL。Redis 中不保存密码、明文长期 Token 或用户敏感原文；日志不记录业务 Key 中的敏感标识或值。

## 本地运行

在 `deploy/` 初始化私有环境文件后，启动完整环境：

```sh
docker compose --env-file ../.env -f docker-compose.yml -f docker-compose.local.yml up -d --build --wait --wait-timeout 300
```

只读检查 Redis 连通性及健康接口：

```sh
docker compose --env-file ../.env -f docker-compose.yml -f docker-compose.local.yml exec -T redis redis-cli ping
curl --fail http://localhost:8081/api/v1/health
curl --fail http://localhost:8088/api/v1/health
```

容器内 `redis-cli` 从 `REDISCLI_AUTH` 环境变量读取凭据，命令行没有密码参数。停机时保留命名数据卷，业务配置不使用 `FLUSHDB/FLUSHALL` 清理共享环境。

## 运行记录

2026-10-03 使用现有 Docker Desktop 环境：

- API 的 Docker 镜像构建及 Windows JDK `21.0.9` 的 `package -Dmaven.test.skip=true` 均成功；产物包含上述 Spring Data Redis/Lettuce 版本，没有额外连接池依赖。
- 容器 API 和 Windows `local` 进程启动成功。Redis 的只读 CLIENT LIST 显示两条 `cangshuo-toolbox-api` 应用连接，均已认证为 `default` 用户并选择数据库 0。
- 本机 `8080`、容器直连 `8081` 与 Nginx 代理 `8088` 的健康接口均返回 `200/0/UP`，业务 traceId 与响应头一致。Actuator 只返回整体状态，本地 OpenAPI 已包含 Redis 检查说明。
- API 启动日志没有 WARN/ERROR；五个现有容器均健康。本地及正式 Compose 的 `config --quiet` 通过，正式环境不发布 Redis 端口。
- Redis 的 DBSIZE 为 0，本次没有写入业务 Key。MySQL 两份迁移校验值仍为 `1774131352`、`1378438159`，分类为 13 条，工具记录为 0。
- 未建立或运行自动化测试，也未模拟 Redis 中断或认证失败；业务缓存读写、过期及并发行为待实际业务实现后验证。

## 实现依据

- [Spring Boot 3.5 Redis 自动配置](https://docs.spring.io/spring-boot/3.5/reference/data/nosql.html#data.nosql.redis)
- [Spring Boot Redis 配置项](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html)
- [Spring Boot 3.5.16 Redis 健康检查实现](https://github.com/spring-projects/spring-boot/blob/v3.5.16/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/data/redis/RedisHealthIndicator.java)
