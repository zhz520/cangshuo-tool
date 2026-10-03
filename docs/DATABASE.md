# 数据库与迁移

数据库为 MySQL `8.4.11`，默认库名 `toolbox`。服务端通过 JDBC/HikariCP 连接，Flyway 在应用启动时校验并执行迁移；Connector/J `9.7.0`、HikariCP `6.3.3` 沿用 Spring Boot `3.5.16` BOM，Flyway 的 core/MySQL 模块统一固定 `11.20.3`。

Boot 默认的 Flyway `11.7.2` 实际迁移成功，但对 MySQL `8.4` 输出版本兼容提示。本次新增依赖选择同一大版本内的 `11.20.3`，其[官方 MySQL 版本检查](https://github.com/flyway/flyway/blob/flyway-11.20.3/flyway-database/flyway-mysql/src/main/java/org/flywaydb/database/mysql/MySQLDatabase.java)已覆盖到 `9.4`；已执行的 SQL 迁移保持原内容。

## 当前范围

本次 Phase 0 建立工具目录的持久化基础。用户、登录设备、收藏、最近使用、设置、历史及后台运营表，在对应业务任务中通过新增 migration 建立。当前业务 API 仍只有健康检查，目录查询在 Phase 1 实现。

| 迁移 | 内容 |
| --- | --- |
| `V1__create_tool_catalog.sql` | 创建 `tool_category` 和 `tool_definition`，包含唯一键、索引、外键和数据约束 |
| `V2__seed_tool_categories.sql` | 初始化项目规格中的 13 个稳定分类 |

迁移目录：`server/src/main/resources/db/migration/`。Flyway 使用 `flyway_schema_history` 记录版本、校验值、执行时间与结果。该表由 Flyway 管理。

## 通用约定

- 业务表使用 InnoDB、`utf8mb4_0900_ai_ci`、`BIGINT` 自增主键。
- 稳定编码及工具状态使用 `utf8mb4_0900_as_cs`，按大小写与重音精确区分；显示名称使用表的默认排序规则。
- 时间使用 `DATETIME(3)` 存储 UTC 值。JDBC 配置 `connectionTimeZone=UTC` 与 `forceConnectionTimeZoneToSession=true`，让连接会话使用 UTC。
- `created_at` 默认当前时间；`updated_at` 默认当前时间并在行变化时更新。
- `deleted_at` 支持软删除；编码唯一性跨软删除记录保留，避免工具标识被重新分配。后续目录查询必须排除已删除记录。
- JSON 字段使用 MySQL 原生 JSON；关键词必须为数组，配置必须为对象。

## tool_category

| 字段 | 类型/默认值 | 说明 |
| --- | --- | --- |
| `id` | BIGINT，自增 | 主键 |
| `code` | VARCHAR(32) | 唯一且稳定的分类编码 |
| `name` | VARCHAR(64) | 分类显示名称 |
| `icon` | VARCHAR(128)，可空 | 客户端可识别的资源名 |
| `description` | VARCHAR(500)，空字符串 | 分类说明 |
| `sort_order` | INT，0 | 升序排列 |
| `status` | TINYINT，1 | 1 启用、0 关闭，数据库约束限制取值 |
| `created_at`、`updated_at` | DATETIME(3) | 创建、更新时间 |
| `deleted_at` | DATETIME(3)，可空 | 软删除时间 |

索引：唯一 `code`；按 `status, sort_order, id` 排列的索引。分类启用只表示可用于目录配置，不表示该分类已有可执行工具。

初始化顺序及编码与项目规格一致：`CALC`、`CONVERT`、`TEXT`、`DEV`、`QR`、`IMAGE`、`PDF`、`DEVICE`、`SENSOR`、`NETWORK`、`LIFE`、`AI`、`OTHER`，排序值从 10 开始，每次增加 10。

## tool_definition

| 字段 | 类型/默认值 | 说明 |
| --- | --- | --- |
| `id` | BIGINT，自增 | 主键 |
| `tool_code` | VARCHAR(64) | 唯一且稳定的工具编码，映射 API 的 `code` |
| `name` | VARCHAR(128) | 显示名称 |
| `description` | VARCHAR(500)，空字符串 | 简短说明 |
| `category_id` | BIGINT | 指向分类的外键，禁止删除仍被引用的分类 |
| `icon` | VARCHAR(128)，可空 | 客户端资源名 |
| `keywords_json` | JSON，`[]` | 搜索关键词数组，映射 API 的 `keywords` |
| `mode` | VARCHAR(16)，LOCAL | LOCAL、SERVER、HYBRID |
| `version` | INT，1 | 正整数元数据版本 |
| `status` | VARCHAR(16)，ENABLED | ENABLED、DISABLED、MAINTENANCE |
| `sort_order` | INT，0 | 升序排列，同序时按主键保持稳定 |
| `is_featured` | TINYINT，0 | 推荐标记，只允许 0/1 |
| `requires_login` | TINYINT，0 | 登录要求，只允许 0/1 |
| `config_json` | JSON，`{}` | 工具配置对象，禁止保存客户端可下载的秘密 |
| `created_at`、`updated_at` | DATETIME(3) | 创建、更新时间 |
| `deleted_at` | DATETIME(3)，可空 | 软删除时间 |

索引包含唯一 `tool_code`，以及分类/状态/排序、状态/排序、状态/推荐/排序组合。`mode`、`status`、版本、布尔值及 JSON 类型均有 CHECK 约束。

当前未初始化工具记录，执行工具及其元数据按 ToolDefinition/ToolRegistry 任务建立。分类名称和 JSON 编码使用 UTF-8。

## 连接配置

| 变量 | 默认/来源 | 用途 |
| --- | --- | --- |
| `DB_HOST` | `127.0.0.1`；Compose 设置为 `mysql` | 数据库地址 |
| `DB_PORT` | `LOCAL_MYSQL_PORT` 或 3306；Compose 设置为 3306 | 连接端口 |
| `DB_NAME` | `MYSQL_DATABASE` 或 `toolbox` | 数据库名 |
| `DB_USERNAME` | `MYSQL_USER` 或 `toolbox` | 数据库账号 |
| `DB_PASSWORD` | 必需，可读取 `MYSQL_PASSWORD` | 账号密码，无内置密码 |
| `DB_SSL_MODE` | `REQUIRED` | 要求 TLS；外部生产数据库可配置 VERIFY_IDENTITY 与可信 CA |

HikariCP 使用最多 10 个连接、最少 2 个空闲连接；取连接超时 3 秒，连接验证超时 1 秒。JDBC 建连超时 3 秒、Socket 读取超时 5 秒。数据库未连接或 migration 失败时，API 启动失败。

显式 `local` profile 会从进程工作目录的 `../.env` 导入配置，按 Spring Boot properties 解析。因此本机启动需在 `server/` 目录执行，可复用根目录初始化脚本生成的私有 `.env`。环境变量优先于文件；Compose 使用已有的 `DB_*` 环境变量。默认、staging、production 不自动导入该文件。

## 运行与迁移规则

1. 使用部署初始化脚本生成私有凭据，并启动 MySQL。Compose 等待 MySQL 应用账号查询成功后启动 API。
2. API 自动校验现有迁移，再执行新版本；不用单独安装 Flyway CLI。
3. 修改结构或固定基础数据时新增 `V3__description.sql` 等递增文件。已执行的迁移保持原内容。

配置明确禁止 Flyway `clean`，关闭自动 baseline 和乱序迁移，开启迁移命名与校验值检查。没有并行的 `schema.sql/data.sql` 初始化。如果库中已有未知业务表而没有迁移记录，启动会失败；不要以开启自动 baseline 的方式绕过。

MySQL 的 DDL 不能保证整份迁移事务回滚。迁移失败时先核对记录与已创建的结构，再处理原因；保留现有数据卷。生产迁移与备份按发布任务完成。

只读取迁移状态，在 `deploy/` 执行：

```sh
docker compose --env-file ../.env -f docker-compose.yml -f docker-compose.local.yml exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 -u "$MYSQL_USER" --database="$MYSQL_DATABASE" --execute="SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank; SELECT COUNT(*) AS category_count FROM tool_category;"'
```

## 健康检查

Actuator 自动将主数据源连接状态加入整体健康检查。`/actuator/health` 只公开整体状态；`/api/v1/health` 继续使用既有封装，正常返回 `200/0/UP`，数据库运行期间不可用时返回 `503/10008/data=null`。组件明细、连接账号和凭据不通过接口公开。健康检查只读，不修改目录数据。

Redis 接入后也参与整体健康状态，连接配置及运行记录见 [Redis 说明](REDIS.md)。

## 运行记录

2026-10-03 使用现有本地 MySQL 数据卷：

- Docker Maven 构建及 Windows JDK `21.0.9` 的 `package -Dmaven.test.skip=true` 均成功。
- 首次启动成功执行 `V1`、`V2`；两张业务表及 Flyway 历史表均为 InnoDB、`utf8mb4_0900_ai_ci`，唯一键、外键和 CHECK 约束已从数据库读取确认。
- 迁移记录均为 `success=1`，V1 校验值为 `1774131352`、V2 为 `1378438159`。13 个分类的中文名称、编码及排序值正确；工具记录为 0。
- 固定 Flyway `11.20.3` 后，容器重启与 Windows 本地启动都成功校验两份已有迁移，报告 schema 版本 2、无需迁移；分类数量及校验值保持不变，日志未出现 MySQL 版本兼容提示。
- Windows 进程在 `server/` 下使用 `local` profile，自动读取私有 `.env` 并连接 `127.0.0.1:3307`；命令行没有传入密码。两种运行方式均使用 `sslMode=REQUIRED`。
- 本机 `8080`、容器直连 `8081` 和 Nginx 代理 `8088` 的健康接口均返回 `200/0/UP`，traceId 与响应头一致。Actuator 只返回整体状态；本地 OpenAPI 包含数据库健康检查说明。
- 未建立或运行自动化测试，未模拟数据库中断或迁移失败；上述失败路径的响应约定不等于已完成故障演练。

## 实现依据

- [Spring Boot 3.5 数据库初始化与 Flyway](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html)
- [Flyway MySQL 支持](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql)
- [Connector/J 时间处理](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html)
- [Connector/J TLS 配置](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-security.html)
