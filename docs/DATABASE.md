# 数据库与迁移

## Android 本地目录缓存

2026-10-05，Android 应用私有 `toolbox.db` 为 Room v3：保留 favorite_tool/recent_tool，新增 cached_tool_catalog 单行快照；MIGRATION_2_3 仅建新表，旧版保留 1→2→3 路径，无破坏性回退。format_version/source_key/written_at/payload_sha256/payload 保存有界、经过校验的 WEB 元数据，字段及预算见 [目录缓存说明](TOOL_CATALOG_CACHE.md)。KSP 导出 schema 3.json；24 项主机 SQLite 检查确认 1/2→3 数据保留、结构与查询预算，Android 真机验证记录另行补充。本任务没有修改下方服务端 MySQL/Flyway 结构或已执行迁移。

数据库为 MySQL `8.4.11`，默认库名 `toolbox`。服务端通过 JDBC/HikariCP 连接，Flyway 在应用启动时校验并执行迁移；Connector/J `9.7.0`、HikariCP `6.3.3` 沿用 Spring Boot `3.5.16` BOM，Flyway 的 core/MySQL 模块统一固定 `11.20.3`。

Boot 默认的 Flyway `11.7.2` 实际迁移成功，但对 MySQL `8.4` 输出版本兼容提示。本次新增依赖选择同一大版本内的 `11.20.3`，其[官方 MySQL 版本检查](https://github.com/flyway/flyway/blob/flyway-11.20.3/flyway-database/flyway-mysql/src/main/java/org/flywaydb/database/mysql/MySQLDatabase.java)已覆盖到 `9.4`；已执行的 SQL 迁移保持原内容。

## 当前范围

Phase 0 建立工具目录的持久化基础，Phase 1 已增加 `GET /api/v1/tools` 的只读分页查询，复用已有表和索引；列表只包含启用且未软删除的工具及分类。接口实现与验证范围见 [目录接口说明](TOOL_CATALOG.md)。用户、登录设备、收藏、最近使用、设置、历史及后台运营表，在对应业务任务中通过新增 migration 建立。

| 迁移 | 内容 |
| --- | --- |
| `V1__create_tool_catalog.sql` | 创建 `tool_category` 和 `tool_definition`，包含唯一键、索引、外键和数据约束 |
| `V2__seed_tool_categories.sql` | 初始化项目规格中的 13 个稳定分类 |
| `V3__seed_calculator_tool.sql` | 登记已实现的本地计算器目录元数据，保留已有同编码记录 |
| `V4`–`V13` | 依次登记单位转换、时间戳、UUID、Base64、URL、Hash、JSON、文本、二维码和图片压缩的目录元数据 |
| `V14__normalize_tool_descriptions.sql` | 将 11 个工具仍等于原始种子的描述替换为 Android 中文功能说明，保留人工修改的描述与其他配置 |
| V15__allow_web_tool_mode.sql | 放宽 ck_tool_definition_mode，允许 Decision 019 的 WEB（官网网页承载）；只改约束，不动数据与 V1 |
| `V16__seed_web_qr_studio_tool.sql` | 登记首个 WEB 工具 `qr_studio`（二维码工作台、分类 QR、官网 /tools/qr_studio/ 承载、排序 211）；原生 `qr` 保持 LOCAL |
| `V17__correct_qr_studio_description.sql` | 按二进制精确匹配修正 V16 的 `qr_studio` 描述（“生成结果裁剪”→“尺寸与留白控制”），其他字段与人工编辑内容不变；2026-10-05 已在本机执行 |

迁移目录：`server/src/main/resources/db/migration/`。Flyway 使用 `flyway_schema_history` 记录版本、校验值、执行时间与结果。该表由 Flyway 管理。

## 通用约定

- 业务表使用 InnoDB、`utf8mb4_0900_ai_ci`、`BIGINT` 自增主键。
- 稳定编码及工具状态使用 `utf8mb4_0900_as_cs`，按大小写与重音精确区分；显示名称使用表的默认排序规则。
- 时间使用 `DATETIME(3)` 存储 UTC 值。JDBC 配置 `connectionTimeZone=UTC` 与 `forceConnectionTimeZoneToSession=true`，让连接会话使用 UTC。
- `created_at` 默认当前时间；`updated_at` 默认当前时间并在行变化时更新。
- `deleted_at` 支持软删除；编码唯一性跨软删除记录保留，避免工具标识被重新分配。后续目录查询必须排除已删除记录。
- JSON 字段使用 MySQL 原生 JSON；关键词必须为数组，配置必须为对象。
- V36 新增 `deleted_account(user_id BIGINT PK, deleted_at DATETIME(3))`，保留无原文的删除台账用于备份恢复抑制；删除 sys_user 级联移除会话、同步、反馈和 AI 使用计数，见 [数据删除](DATA_DELETION.md)。

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
| `mode` | VARCHAR(16)，LOCAL | LOCAL、SERVER、HYBRID、WEB；`WEB` 由 V15 放宽 CHECK 生效，表示官网 `/tools/<code>/` 承载、App 用内置 WebView 打开，仍要求唯一 code 与完整元数据 |
| `version` | INT，1 | 正整数元数据版本 |
| `status` | VARCHAR(16)，ENABLED | ENABLED、DISABLED、MAINTENANCE |
| `sort_order` | INT，0 | 升序排列；列表接口同序时按稳定工具编码排序，现有索引仍保留主键后缀 |
| `is_featured` | TINYINT，0 | 推荐标记，只允许 0/1 |
| `requires_login` | TINYINT，0 | 登录要求，只允许 0/1 |
| `config_json` | JSON，`{}` | 工具配置对象，禁止保存客户端可下载的秘密 |
| `created_at`、`updated_at` | DATETIME(3) | 创建、更新时间 |
| `deleted_at` | DATETIME(3)，可空 | 软删除时间 |

索引包含唯一 `tool_code`，以及分类/状态/排序、状态/排序、状态/推荐/排序组合。`mode`、`status`、版本、布尔值及 JSON 类型均有 CHECK 约束。新增 `WEB` 模式时必须先新增迁移修改 `ck_tool_definition_mode`，不修改已执行的 V1。

V3 初始化已实现的计算器记录：编码 `calculator`、分类 `CALC`、模式 `LOCAL`、排序 10、默认启用和推荐、不要求登录。已有同编码记录保留，避免覆盖人工配置、状态或软删除。名称和说明当前使用中文，关键词包含中英文；Android 内置工具界面由本机语言资源决定。分类名称和 JSON 编码使用 UTF-8。客户端执行范围与验证见 [计算器说明](CALCULATOR.md)。

V14 将 V3–V13 的默认描述统一为功能说明，移除“本地处理/离线/无需登录/保护隐私”等宣传措辞，并同步 Base64 严格 UTF-8/Hex、URL 三种类型和文本统计的新描述。每条更新同时匹配稳定 code 和原始描述；描述使用 `CAST(... AS BINARY)` 逐字节比较，避免默认不区分大小写的排序规则或尾空格让人工编辑内容被覆盖。仅更新 `description`，`updated_at` 由现有表规则自动刷新；工具版本、状态、软删除、排序、推荐、登录要求及配置不变。自定义描述由后续后台编辑任务按同一文案规范维护。

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
3. 修改结构或固定基础数据时新增 `V4__description.sql` 等递增文件。已执行的迁移保持原内容。

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

2026-10-04，V15 WEB 模式约束：

- 重建本地 server 镜像后 Flyway 校验 15 份迁移并执行 V15（00:00.133s），schema 升到 v15；flyway_schema_history 15 条全部 success=1。
- information_schema 读取的 ck_tool_definition_mode 已为 (mode in ('LOCAL','SERVER','HYBRID','WEB'))；目录仍 11 个工具、0 个 WEB。
- 直连 8081 与 Nginx 8088 均返回 total=11，所有 mode 落在四个允许值内；代理健康检查 UP，五个本地容器健康。
- 未执行自动化测试；V15 只放宽约束，不改数据与已有迁移。

2026-10-04，V16 首个 WEB 工具：

- Flyway 校验 16 份迁移并执行 V16，schema 升到 v16，历史 16 条全部 success=1。
- tool_definition 中 qr_studio 为 mode=WEB、status=ENABLED、sort_order=211，工具总数 12。
- 直连 8081 与 Nginx 8088 的 /api/v1/tools 均返回 total=12 且包含 qr_studio；/tools/qr_studio/ 页面返回 200；server 与 admin 容器健康。
- 未执行自动化测试；V16 只新增一条目录记录，不改结构与既有迁移。

2026-10-05，V17 描述修正：

- 重建本地 server 镜像后 Flyway 校验 17 份迁移并执行 V17（00:00.026s），schema 升到 v17，历史 17 条全部 success=1；旧迁移校验值未变。
- `qr_studio` 描述更新为“网页版二维码工作台：尺寸与留白控制、SVG/JPEG 导出、WPA3 与企业 Wi-Fi、vCard 多值联系人。”；含旧文案“生成结果裁剪”的记录计数为 0，工具总数仍 12、mode 仍 WEB。
- Nginx 代理 8088 的 /api/v1/tools 返回同一描述；五个本地容器健康。
- 本机 Android 中文资源同步该描述；未新增结构、索引、权限或其他迁移，V16 文件未修改。
## V18 用户账号

2026-10-05 新增 `sys_user`（InnoDB/utf8mb4，bigint 自增 id）：username（预留）、email、password_hash、nickname、avatar_url（预留）、status、last_login_at、created_at、updated_at。邮箱 ASCII 大小写不敏感且唯一，密码哈希 ASCII 二进制比较；status CHECK 只允许 0/1。服务端保存 BCrypt cost=12 哈希，注册与登录更新时间，UTC 毫秒精度。

本地 Flyway 已应用 V18，18 条迁移全部成功；真实注册/登录确认仅保存哈希，临时账号已删除。V1–V17 未修改。Refresh Session/同步表按后续任务新增。见 [认证说明](AUTH.md)。

## Android 本地数据库

## 运行记录（续）

2026-10-04 使用现有本地 MySQL 数据卷与重建后的 server 镜像：

- Docker Desktop Linux 引擎（29.8.1）启动后，五个本地容器全部健康；server 镜像由当前工作区重建，替换只包含 V1–V3 的旧镜像。
- Flyway 成功校验并执行 V4–V13 共 10 个迁移，执行时间 00:00.053s，schema 从版本 3 升到 v13；`flyway_schema_history` 13 条记录全部 `success=1`。
- `GET /api/v1/tools` 返回 11 个工具；DEV 分类 6 个；分页、非法 `pageSize=0`（HTTP 400）、Nginx 代理和健康检查均符合预期。接口与客户端字段核对见 [目录接口说明](TOOL_CATALOG.md)。
- 未模拟迁移失败、数据库中断或回滚；未运行自动化测试。

2026-10-04，V14 描述迁移：

- 本地 server 镜像构建成功，Maven package 为 BUILD SUCCESS（3.448 秒），沿用 Dockerfile 跳过测试的设置。
- Flyway 校验 14 份迁移并成功执行 V14（00:00.027s），schema v14；14 条历史全部 `success=1`，V1–V13 校验值与执行前一致。V14 校验值为 `-1342173067`。
- 11 条描述与 Android 中文资源逐字一致；数据库默认描述中的宣传措辞计数为 0。直连 API 与 Nginx 代理目录一致，其他公开元数据与迁移前一致，代理健康状态 UP，五个本地容器健康。
- 迁移前用会话临时表执行相同 SQL：9 条原始描述更新，自定义计算器描述和带尾空格的 UUID 描述保留；单位转换的关闭、软删除、版本、推荐和排序配置保持原值。临时表随会话结束清理，没有修改业务表的验收样例。
- 未新增持久化自动回归测试，未执行 Android 构建或设备验收；本轮只修改服务端数据迁移和文档。

Android 的本地收藏使用独立 SQLite/Room 初始数据库版本 1，表为 `favorite_tool`，schema 纳入版本控制；客户端后续结构变更使用 Room Migration。本文中的 MySQL 表、V1–V14 和 Flyway 规则继续针对服务端。字段及实现范围见 [本地收藏说明](LOCAL_FAVORITES.md#sqlite-结构)。

## 实现依据

- [Spring Boot 3.5 数据库初始化与 Flyway](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html)
- [Flyway MySQL 支持](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql)
- [Connector/J 时间处理](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html)
- [Connector/J TLS 配置](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-security.html)

## V19 Refresh Session

2026-10-06：V34 登记 pdf_studio/WEB/PDF 并在本地应用；V35 新增 ai_daily_usage（用户 + UTC 日期联合主键，原子预占每日尝试、用户删除级联）和 ai_text/SERVER/AI 工具。V1–V33 未修改。实际 V35 应用结果另行记录。

`auth_refresh_session` 保存唯一 session_code、user_id、expires_at、revoked_at、last_active_at。`auth_refresh_token` 保存唯一 SHA-256 token_hash、session_id、consumed_at；用户→会话→令牌级联删除。UTC 毫秒，刷新行锁事务；客户端不存密码，服务端不存明文刷新凭证。本地 V19 已应用，旧 V1–V18 文件保持不变。

## V20 / Room v5 云同步

V20 创建 user_sync_state 和 user_sync_entity，用户 FK 级联删除，账号维度 revision 唯一及实体复合主键；只存同步元数据。Android Room v5 新增账号实体、开关/游标和随机设备标识，4→5 migration 保留所有旧表，schema 已导出，主机 SQLite 12 项通过。见 CLOUD_SYNC.md。

Room v6/v7 分别为最近和设置增加独立开关/enrolled 标记，旧收藏、游标和全部旧表保留；4→5→6→7 实际 SQL/schema 与队列条件更新在主机 SQLite 30 项通过，schema 5/6/7 已导出。Server 复用 V20，不修改已应用迁移。
