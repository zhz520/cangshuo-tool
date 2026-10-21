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

Room v8 为本地待办新增 `todo_task`，使用自增主键、标题/备注、可选截止日期和完成时间、优先级、完成状态及创建/更新时间，并建立完成状态、截止日期和创建时间索引。`MIGRATION_7_8` 只创建新表与索引；真实模拟器从 v7 升级后 12 条最近使用记录保留，`PRAGMA user_version=8`，列与索引和导出的 schema 8 一致。见 [待办事项](TODO_LIST.md)。

Room v9 为习惯打卡新增 `habit` 与 `habit_entry`。习惯保存名称、说明、7 位星期掩码、归档/顺序、创建日和毫秒时间戳；打卡以 `(habit_id, date_epoch_day)` 为复合主键，外键级联删除。`MIGRATION_8_9` 只创建新表、外键与索引；模拟器从 v8 升级到 v9 后 12 条最近使用记录保留。见 [习惯打卡](HABIT_TRACKER.md)。

Room v10 为纪念日新增 `anniversary_event`。事件保存标题、备注、原始 epoch day、重复规则、分类、置顶状态和毫秒时间戳；`MIGRATION_9_10` 只创建新表及置顶/日期索引，500 条上限由新增 SQL 原子检查。日期、标题和备注都不上传，服务端不新增业务表。见 [纪念日](ANNIVERSARY_COUNTDOWN.md)。

Room v11 为番茄专注新增 `pomodoro_session`。记录保存专注名称、阶段、计划/实际秒数、完成状态和起止毫秒时间；`MIGRATION_10_11` 只创建新表及结束时间/阶段索引，DAO 在事务内插入并裁剪最近 2000 条。计时快照和设置保存在本机 SharedPreferences，服务端只登记工具目录。见 [番茄专注](POMODORO_FOCUS.md)。

Room v12 为物品资产新增 `inventory_item`。记录保存名称、备注、分类、存放位置、数量与单位、可选分单位单价、低库存阈值、可选购买/到期 epoch day 和创建/更新时间；`MIGRATION_11_12` 只创建新表及分类、到期日、更新时间索引。Room `@Transaction` 在同一事务中检查并插入 1000 条上限，快速增减在单条 SQL 中夹到 `0..999999`。全部物品数据不上传；服务端只登记工具目录。真实 v11 数据库覆盖升级到 v12 后保留了 `calculator` 最近使用记录，新表列和三个索引与导出 schema 12 一致。见 [物品资产](INVENTORY_ASSETS.md)。

Room v13 为订阅管理新增 `subscription_item`。记录保存名称、服务商、分类、分单位价格、计费周期与自定义天数、开始日期、原始账单锚点、下次续费日期、支付方式、备注、提醒/自动续费/启用状态和毫秒时间戳；`MIGRATION_12_13` 只创建新表及启用状态、下次续费、更新时间索引。Room `@Transaction` 原子检查 500 条上限，后台每日提醒接收器通过 App 级数据库提供器读取本地记录。真实 v12 数据库覆盖升级到 v13 后保留 `calculator` 最近使用记录，18 列和三个索引与导出 schema 13 一致。见 [订阅管理](SUBSCRIPTION_MANAGER.md)。

Room v14 为经期记录新增 `period_cycle`。记录保存开始/结束日期、可空的进行中状态、流量、症状位掩码、可空情绪、备注和毫秒时间戳；`MIGRATION_13_14` 只创建新表、唯一开始日索引和更新时间索引，不修改任何旧表或已执行迁移。`@Transaction` 检查 1200 条上限；记录不接入现有云同步。真实 v13 数据库升级到 v14 后保留 `recent_tool` 历史，9 列和两个索引与导出 schema 14 一致。见 [经期记录](PERIOD_TRACKER.md)。

## V49 本地文本工具目录

`V49__seed_text_tools_batch.sql` 登记 `number_chinese`、`char_convert`、`text_similarity`，TEXT/LOCAL，排序 80/85/90。不保存工具输入或改变已有业务表；重复执行保留后台状态及软删除。源码说明分别见 NUMBER_CHINESE.md、CHAR_CONVERT.md、TEXT_SIMILARITY.md。

## V50 Zepp Life 网页步数工具

`V50__seed_zepp_steps_web_tool.sql` 登记 `zepp_steps`，LIFE/WEB/ENABLED，排序 510、requires_login=0，config_json 仅含 `/tools/zepp_steps/` 路径。`ON DUPLICATE KEY` 不覆盖管理员配置。没有第三方账号、密码、Token 或提交记录表；计数沿用已有 tool_usage_* 和 API 流量统计。V1–V48 已执行文件保持不变；实际迁移结果见 ZEPP_STEPS.md。

## V51 光感仪目录

`V51__seed_light_meter_tool.sql` 登记 `light_meter`，SENSOR/LOCAL/ENABLED，排序 340、requires_login=0。复用既有后台管理及使用统计；重复执行保留管理员编辑、维护/关闭状态与软删除。不新增测量记录表，不上传传感器数据。验证范围见 [光感仪](LIGHT_METER.md)。

## V52 抛硬币目录

`V52__seed_coin_flip_tool.sql` 登记 `coin_flip`，OTHER/LOCAL/ENABLED，排序 600、requires_login=0。元数据与 Android 中文资源一致，复用后台管理、使用计数和排行榜；重放不会覆盖管理员编辑、维护状态或软删除。没有投掷记录表，结果和文字只保留在客户端会话。验证见 [抛硬币](COIN_FLIP.md)。

## V53 秒表目录

`V53__seed_stopwatch_tool.sql` 登记 `stopwatch`，LIFE/LOCAL/ENABLED，排序 620、requires_login=0。元数据与 Android 中文资源一致；复用后台管理、使用计数与排行榜，重放保留管理员编辑、维护状态与软删除。没有计时记录表，会话只存在于客户端。验证见 [秒表](STOPWATCH.md)。

## V54 短视频解析网页工具

`V54__seed_short_video_parse_web_tool.sql` 登记 `short_video_parse`，NETWORK/WEB/ENABLED，排序 630、requires_login=0，config_json 仅含 `/tools/short_video_parse/`。重放不覆盖管理员编辑、维护/关闭状态与软删除。解析结果只存在服务进程内存（15 分钟、最多 100 个会话），没有媒体、链接或下载明细表；计数沿用 tool_usage_* 与 API 流量统计。验证见 [短视频解析](SHORT_VIDEO_PARSE.md)。

## V56 汉字转拼音目录

`V56__seed_pinyin_tool.sql` 登记 `pinyin`，TEXT/LOCAL/ENABLED，排序 95、requires_login=0、is_featured=0。客户端使用离线字库和词组表，不新增执行 API、不上传文字、不新增用户输入表。公开目录与后台共用该记录，使用统计沿用 `tool_usage_*`；重放保留人工编辑、状态和软删除。验证见 [拼音工具](PINYIN.md) 与 [验收记录](PINYIN_ACCEPTANCE_2026-10-14.md)。

## V59 图片 Base64 目录

`V59__seed_image_base64_tool.sql` 登记 `image_base64`，IMAGE/LOCAL/ENABLED，排序 106、requires_login=0、is_featured=0。只写既有 `tool_definition` 目录列，分类按 `IMAGE` code 解析；`ON DUPLICATE KEY UPDATE tool_code=tool_code` 重放时不覆盖后台人工编辑、配置、状态、时间戳或软删除。图片、Base64 文本和导出内容只在 Android 临时状态/私有缓存中处理，没有新增数据表、公开 API 或服务端内容存储。说明与验证范围见 [图片 Base64](IMAGE_BASE64.md)。

## V60 纯色图目录

`V60__seed_pure_color_image_tool.sql` 登记 `pure_color_image`，IMAGE/LOCAL/ENABLED，排序 107、requires_login=0、is_featured=0。只写既有工具目录列，不保存颜色、尺寸或生成图片。重复执行保留后台编辑、配置、状态、时间戳与软删除。详见 [纯色图制作](PURE_COLOR_IMAGE.md)。

## V61 图片镜像发现性

`V61__update_image_compress_description.sql` 仅在 `image_compress` 描述仍等于 V13 原始种子时更新中文说明与关键词，加入镜像/翻转/flip/mirror；人工编辑过的目录不会被覆盖。没有新增表、API 或图片上传。实现见 [图片压缩](IMAGE_COMPRESS.md)。

## V62 图片圆角发现性

`V62__update_image_compress_rounded.sql` 仅在描述仍为 V61 内容时加入圆角与 `rounded` 关键词，保留人工编辑；不新增表或接口。

## V63 图片马赛克发现性

`V63__update_image_compress_mosaic.sql` 仅在描述仍为 V62 内容时加入马赛克与 `mosaic` 关键词，保留人工编辑；不新增表或接口。

## V64 图片模糊发现性

`V64__update_image_compress_blur.sql` 仅在描述仍为 V63 内容时加入模糊与 `blur` 关键词，保留人工编辑；不新增表或接口。

## V65 图片滤镜发现性

`V65__update_image_compress_filters.sql` 仅在描述仍为 V64 内容时加入滤镜与 `filter` 关键词，保留人工编辑；不新增表或接口。

## V66 图片旋转发现性

`V66__update_image_compress_rotation.sql` 仅在描述仍为 V65 内容时加入旋转与 `rotate/rotation` 关键词，保留人工编辑；不新增表或接口。

## V67 图片裁剪发现性

`V67__update_image_compress_crop.sql` 仅在描述仍为 V66 内容时加入居中裁剪与 `crop` 关键词，保留人工编辑。

## V68 世界时间目录

`V68__seed_world_clock_tool.sql` 登记 `world_clock`，LIFE/LOCAL/ENABLED，排序 621、requires_login=0、is_featured=0。关键词覆盖世界时间、时区、城市时间、UTC、夏令时和中英文检索词；`ON DUPLICATE KEY UPDATE tool_code=tool_code` 重放时保留管理员编辑、配置、状态、时间戳与软删除。工具只使用设备时区和系统 IANA 规则，没有新增表、公开 API 或服务端时间记录。实现与边界见 [世界时间](WORLD_CLOCK.md)。生产已执行 V68，Flyway 历史 68/68 且全部成功。

## V69 节日倒数目录

`V69__seed_holiday_countdown_tool.sql` 登记 `holiday_countdown`，LIFE/LOCAL/ENABLED，排序 622、requires_login=0、is_featured=0。关键词覆盖节日、倒数、农历、春节、中秋和中英文检索词；重复执行保留管理员编辑、配置、状态、时间戳与软删除。农历算法和内置节日只存在于 Android，不新增表、公开 API、远程执行代码或用户节日记录。实现与边界见 [节日倒数](HOLIDAY_COUNTDOWN.md)。生产已执行 V69，Flyway 历史 69/69 且全部成功。

## V70 万年历目录

`V70__seed_lunar_calendar_tool.sql` 登记 `lunar_calendar`，LIFE/LOCAL/ENABLED，排序 623、requires_login=0、is_featured=0。关键词覆盖万年历、公历、农历、黄历、干支、生肖、节气、宜忌及中英文检索词；重复执行保留管理员编辑、配置、状态、时间戳与软删除。历法与宜忌只存在于 Android，不新增表、公开 API、远程执行代码或用户日程记录。实现与边界见 [万年历](LUNAR_CALENDAR.md)。生产已执行 V70，Flyway 历史 70/70 且全部成功。

## V71 工作性价比目录

`V71__seed_work_value_tool.sql` 登记 `work_value`，CALC/LOCAL/ENABLED，排序 58、requires_login=0、is_featured=0。关键词覆盖工作性价比、时薪、工资、月薪、通勤、年收入及中英文检索词；重复执行保留管理员编辑、配置、状态、时间戳与软删除。薪资、工时和通勤只在 Android 本地计算，不上传也不新增表。实现与边界见 [工作性价比](WORK_VALUE.md)。生产已执行 V71，Flyway 历史 71/71 且全部成功。

## V72 记分牌目录

`V72__seed_scoreboard_tool.sql` 登记 `scoreboard`，LIFE/LOCAL/ENABLED，排序 624、requires_login=0、is_featured=0。关键词覆盖记分牌、计分、比分、两队、计时、比赛、裁判及中英文检索词；重复执行保留管理员编辑、配置、状态、时间戳与软删除。比分、队名、节次、计时和撤销历史只保存在 Android 本地，不上传也不新增表。实现与边界见 [记分牌](SCOREBOARD.md)。生产已执行 V72，Flyway 历史 72/72 且全部成功。

## V73 手持弹幕目录

`V73__seed_handheld_banner_tool.sql` 登记 `handheld_banner`，LIFE/LOCAL/ENABLED，排序 625、requires_login=0、is_featured=0。关键词覆盖手持弹幕、弹幕、滚动字幕、走马灯、LED、接机及中英文检索词；重复执行保留管理员编辑、配置、状态、时间戳与软删除。弹幕文字和样式只保存在 Android 本地，不上传也不新增表。实现与边界见 [手持弹幕](HANDHELD_BANNER.md)。生产已执行 V73，Flyway 历史 73/73 且全部成功。

## V74 时间屏幕目录

`V74__seed_time_screen_tool.sql` 登记 `time_screen`，LIFE/LOCAL/ENABLED，排序 626、requires_login=0、is_featured=0。关键词覆盖时间屏幕、全屏时钟、桌面时钟、数字时钟、防烧屏、时间显示及中英文检索词；重复执行保留管理员编辑、配置、状态、时间戳与软删除。时间、时区和显示偏好只读取或保存在 Android 本地，不上传也不新增表。实现与边界见 [时间屏幕](TIME_SCREEN.md)。生产已执行 V74，Flyway 历史 74/74 且全部成功。

## V75 做个决定目录

`V75__seed_decision_wheel_tool.sql` 登记 `decision_wheel`，LIFE/LOCAL/ENABLED，排序 627、requires_login=0、is_featured=0。关键词覆盖做个决定、随机选择、转盘、抽签、抉择、选择困难、权重及中英文检索词；重复执行保留管理员编辑、配置、状态、时间戳与软删除。选项、权重、随机结果与历史只保存在 Android，不上传也不新增表。实现与边界见 [做个决定](DECISION_WHEEL.md)。生产已执行 V75，Flyway 历史 75/75 且全部成功。

## V76 画板目录

`V76__seed_drawing_board_tool.sql` 登记 `drawing_board`，LIFE/LOCAL/ENABLED，排序 628、requires_login=0、is_featured=0。关键词覆盖画板、画画、涂鸦、手绘、绘图、画笔、橡皮擦及中英文检索词；重复执行保留管理员编辑、配置、状态、时间戳与软删除。笔画、颜色、粗细和保存状态只存在于 Android，不上传也不新增表。实现与边界见 [画板](DRAWING_BOARD.md)。生产已执行 V76，Flyway 历史 76/76 且全部成功。

## V77 待办事项目录

`V77__seed_todo_list_tool.sql` 登记 `todo_list`，LIFE/LOCAL/ENABLED，排序 629、requires_login=0、is_featured=0。关键词覆盖待办、任务、清单、计划、截止日期、优先级及中英文检索词；重复执行保留管理员编辑、配置、状态、时间戳与软删除。任务、完成状态和备注只保存在 Android Room，不上传、不同步也不新增服务端业务表。实现与边界见 [待办事项](TODO_LIST.md)。生产已执行 V77，Flyway 历史 77/77 且全部成功。

## V78 习惯打卡目录

`V78__seed_habit_tracker_tool.sql` 登记 `habit_tracker`，LIFE/LOCAL/ENABLED，排序 630、requires_login=0、is_featured=0。关键词覆盖习惯、打卡、连续、坚持、目标、日程及中英文检索词；重复执行保留管理员编辑、配置、状态、时间戳与软删除。习惯定义和打卡历史只保存在 Android Room，不上传、不同步，也不新增服务端业务表。实现与边界见 [习惯打卡](HABIT_TRACKER.md)。生产已执行 V78，Flyway 历史 78/78 且全部成功。

## V79 纪念日目录

`V79__seed_anniversary_countdown_tool.sql` 登记 `anniversary_countdown`，LIFE/LOCAL/ENABLED，排序 631、requires_login=0、is_featured=0。关键词覆盖纪念日、生日、周年、倒数、重要日子及中英文检索词；重复执行保留管理员编辑、配置、状态、时间戳与软删除。纪念日记录只保存在 Android Room，不上传、不同步，也不新增服务端业务表。实现与边界见 [纪念日](ANNIVERSARY_COUNTDOWN.md)。生产已执行 V79，Flyway 历史 79/79 且失败 0。

## V80 番茄专注目录

`V80__seed_pomodoro_focus_tool.sql` 登记 `pomodoro_focus`，LIFE/LOCAL/ENABLED，排序 632、requires_login=0、is_featured=0。关键词覆盖番茄、专注、计时、休息、效率及中英文检索词；重复执行保留管理员编辑、配置、状态、时间戳与软删除。计时设置、运行快照和专注记录只保存在 Android 本机，不上传、不同步，也不新增服务端业务表。实现与边界见 [番茄专注](POMODORO_FOCUS.md)。生产已执行 V80，Flyway 历史 80/80 且失败 0。

## V81 物品资产目录

`V81__seed_inventory_assets_tool.sql` 登记 `inventory_assets`，LIFE/LOCAL/ENABLED，排序 633、requires_login=0、is_featured=0。关键词覆盖物品、资产、库存、价格、位置、保质期及中英文检索词；重复执行保留管理员编辑、配置、状态、时间戳与软删除。物品资料、数量和日期只保存在 Android Room，不上传、不同步，也不新增服务端业务表。实现与边界见 [物品资产](INVENTORY_ASSETS.md)。生产已执行 V81，Flyway 历史 81/81 且失败 0。

## V82 订阅管理目录

`V82__seed_subscription_manager_tool.sql` 登记 `subscription_manager`，LIFE/LOCAL/ENABLED，排序 634、requires_login=0、is_featured=0。关键词覆盖订阅、会员、续费、账单、到期、自动续费及中英文检索词；重复执行保留管理员编辑、配置、状态、时间戳与软删除。订阅资料和提醒设置只保存在 Android Room/私有 SharedPreferences，不上传、不同步，也不新增服务端业务表或第三方支付接口。实现与边界见 [订阅管理](SUBSCRIPTION_MANAGER.md)。

生产已执行 V82，Flyway 历史 82/82 且失败 0。

## V83 白噪音目录

`V83__seed_white_noise_tool.sql` 登记 `white_noise`，LIFE/LOCAL/ENABLED，排序 635、requires_login=0、is_featured=0。关键词覆盖白噪音、粉噪音、褐噪音、雨声、海浪、助眠及中英文检索词；重复执行保留管理员编辑、配置、状态、时间戳与软删除。声音由 Android DSP 实时合成，播放设置只保存在应用私有 SharedPreferences，不新增音频文件、业务表、公开 API 或服务端播放记录。实现与边界见 [白噪音](WHITE_NOISE.md)。

生产已执行 V83，Flyway 历史 83/83 且失败 0。

## V84 经期记录目录

`V84__seed_period_tracker_tool.sql` 登记 `period_tracker`，LIFE/LOCAL/ENABLED，排序 636、requires_login=0、is_featured=0。关键词覆盖经期、月经、周期、排卵、日历、健康及中英文检索词；重复执行保留管理员编辑、配置、状态、时间戳与软删除。敏感健康记录只保存在 Android Room/私有 SharedPreferences，不上传、不同步，也不新增服务端业务表或健康数据 API。实现与边界见 [经期记录](PERIOD_TRACKER.md)。

生产已执行 V84，Flyway 历史 84/84 且失败 0。

## V85 计时器目录

`V85__seed_countdown_timer_tool.sql` 登记 `countdown_timer`，LIFE/LOCAL/ENABLED，排序 637、requires_login=0、is_featured=0。关键词覆盖计时器、倒计时、提醒、番茄及中英文检索词；重复执行保留管理员编辑、配置、状态、时间戳与软删除。计时快照、名称和保持屏幕设置只保存在 Android 私有 SharedPreferences，不新增 Room 表、服务端业务数据或公开 API。实现与边界见 [计时器](COUNTDOWN_TIMER.md)。

### V86：反应力测试目录

`V86__seed_reaction_test_tool.sql` 登记 `reaction_test`，OTHER/LOCAL/ENABLED，排序 638，无登录要求，默认非推荐。重复执行保留人工编辑、状态、软删除和使用量。成绩与最近 20 组历史仅在 Android 私有存储，不新增服务端成绩数据或 API；后台复用工具管理、排行及统计。见 [反应力测试](REACTION_TEST.md)。

### V87：手速测试目录

`V87__seed_tap_speed_tool.sql` 登记 `tap_speed`，OTHER/LOCAL/ENABLED，排序 639，无登录要求，默认非推荐。重放不改变人工编辑、开关、软删除或使用量。点击分组及最近 20 组成绩仅在 Android 私有存储；后台复用工具管理、使用计数和排行榜，不新增成绩接口或数据库表。见 [手速测试](TAP_SPEED.md)。

### V88：答案之书目录

`V88__seed_answer_book_tool.sql` 登记 `answer_book`，OTHER/LOCAL/ENABLED，排序 640，无登录要求，默认非推荐。重放保留人工配置、状态、软删除及使用量；答案收藏、历史和轮次仅存在 Android 私有存储，不新增服务端表或 API，后台复用原有管理/统计。见 [答案之书](ANSWER_BOOK.md)。

### V89：振动器目录

`V89__seed_vibrator_patterns_tool.sql` 登记 `vibrator_patterns`，OTHER/LOCAL/ENABLED，排序 641，无登录要求，默认非推荐。重放保留后台配置、状态、软删除和使用量。振动历史及参数只保存在设备，不新增服务端表或 API；后台继续复用目录、排行和统计。见 [振动器](VIBRATOR_PATTERNS.md)。

### V90：电子木鱼目录

`V90__seed_wooden_fish_tool.sql` 登记 `wooden_fish`，OTHER/LOCAL/ENABLED，排序 642，无登录要求，默认非推荐。重放保留管理员编辑、状态、软删除和用量。累计/今日次数、里程碑和声音设置仅在 Android 私有存储，不新增服务端业务数据或 API。见 [电子木鱼](WOODEN_FISH.md)。

### V91：可视帧率测试目录

`V91__seed_frame_rate_test_tool.sql` 登记 `frame_rate_test`，OTHER/LOCAL/ENABLED，排序 643，无登录要求，默认非推荐。重放保留人工编辑、状态、软删除和使用量。帧时间和最多 10 条历史仅在 Android 私有存储，不新增服务端业务表或接口。见 [可视帧率测试](FRAME_RATE_TEST.md)。

生产已执行 V85，Flyway 历史 85/85 且失败 0。
