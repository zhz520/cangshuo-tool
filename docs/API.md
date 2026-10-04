# API Contract

本文档把 `toolbox-vibe-spec/PROJECT_SPEC.md` 中的 REST 约定收敛为客户端与服务端共用的接口契约。当前已实现的接口由 Springdoc 生成 OpenAPI 3.1 描述，作为逐接口的机器可读版本；变更接口时同步更新本文档及实际消费该接口的客户端模型。

## 通用约定

- API 前缀：`/api/v1`
- 正式环境 API 基础地址规划为 `https://toolapi.zhzgo.cn/api/v1`，供 Android 与后台使用；域名部署尚未完成。
- 官网使用 `https://tool.zhzgo.cn/`，后台默认规划为 `/admin/`；跨域访问配置见 [部署说明](../deploy/README.md#正式环境域名约定)。
- 请求与响应：UTF-8 JSON
- 工具目录和分类可匿名访问，便于未登录用户浏览和离线启动。
- 需要用户身份的接口使用 `Authorization: Bearer <accessToken>`。
- 时间使用 ISO 8601，并以 UTC 表示；分页从 1 开始。
- 服务端不得返回可执行代码、密钥或第三方 API Secret。
- 服务端为每次请求生成 32 位小写十六进制 `traceId`，同时返回 `X-Trace-Id` 响应头；API 响应中的 `traceId` 与该响应头一致。客户端传入的 `X-Trace-Id` 不替代服务端生成的标识。

## 浏览器跨域访问

API 已支持 `CORS_ALLOWED_ORIGINS` 精确白名单；正式配置允许 `https://tool.zhzgo.cn`，不包含 `/admin/` 或末尾斜杠。支持 `GET/POST/PUT/PATCH/DELETE/OPTIONS`，允许 `Authorization/Content-Type/Accept` 请求头，并暴露 `X-Trace-Id`。不启用 Cookie 凭据。

符合规则的 `OPTIONS` 预检在身份认证前处理，返回标准 CORS 响应头和空响应体，不使用业务封装。不符合白名单、方法或请求头规则的跨域请求返回 HTTP `403`、业务码 `10005`、`data=null` 与 traceId。预检通过后，实际业务请求仍按接口认证和权限规则执行。

## 统一响应

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "f1c438516dbd4e3190e439b457a40d95"
}
```

错误响应使用相同结构，`data` 为 `null`。`code` 是稳定的业务错误码；HTTP 状态码也应反映请求结果，便于通用客户端处理。

| HTTP 状态 | 使用场景 | 示例业务码 |
| --- | --- | --- |
| `400` | 参数无效 | `10001` |
| `401` | 未登录、Token 无效或过期 | `10002`、`10003`、`10004` |
| `403` | 无权限 | `10005` |
| `404` | 资源不存在 | `10006`、`30001` |
| `405`、`406`、`415` | HTTP 方法、响应格式或请求媒体类型不支持 | `10001` |
| `429` | 请求过于频繁 | `10007` |
| `500` | 未预期的服务端错误 | `10000` |
| `503` | 服务或第三方能力暂不可用 | `10008`、`50001` |

错误码完整清单见下方“错误码”。错误消息可面向用户展示；不得在响应或日志中泄露凭据、堆栈或敏感输入。

## 当前已实现：服务健康检查

`GET /api/v1/health` 可匿名访问，汇总 Actuator 的健康状态，包含进程、磁盘、MySQL 及 Redis 连接检查。数据库或 Redis 不可用时也返回下述 `503` 响应；不公开组件明细。响应字段保持原有契约，客户端模型无需调整。

服务可用时返回 HTTP `200`：

```json
{
  "code": 0,
  "message": "success",
  "data": { "status": "UP" },
  "traceId": "f1c438516dbd4e3190e439b457a40d95"
}
```

健康状态不是 `UP` 时返回 HTTP `503`，业务码为 `10008`、`message` 为 `Service unavailable`、`data` 为 `null`。两种响应均包含 `X-Trace-Id`。

Admin 概览通过集中 API 模块消费该接口，校验响应封装和 `data.status="UP"`，以 Pinia 保存连接状态。请求超时为 8 秒，支持手动刷新；失败时显示友好提示，有服务端 traceId 时展示请求编号。开发环境通过 Vite 代理访问同一接口。

基础设施端点 `GET /actuator/health` 使用 Actuator 原生格式，例如 `{"status":"UP"}`，不套业务响应，也不公开组件明细。仅暴露 Actuator 的 `health` 端点。

显式启用 `local` profile 后，可访问 `/v3/api-docs` 获取生成的 OpenAPI，以及 `/swagger-ui/index.html` 浏览接口。默认、`staging`、`production` 环境关闭文档端点。OpenAPI 和 Swagger 静态资源使用原生格式。

当前已实现健康检查与 `GET /api/v1/tools` 列表。Spring Security 仅为目录列表开放此精确路径的 GET；详情、搜索、推荐专用接口、分类接口及其他业务接口按对应阶段开发，当前仍默认要求认证，匿名请求返回 HTTP `401`、业务码 `10002`。登录与 JWT 认证在 Auth & Sync 阶段接入。目录实现及验证范围见 [目录接口说明](TOOL_CATALOG.md)。

## 分页

分页列表统一接收 `page` 和 `pageSize`，默认值分别为 `1` 和 `20`。当前工具列表中，省略或传空的分页参数使用默认值；`page` 范围为 `1..2147483647`，`pageSize` 范围为 `1..100`，非整数或超出范围返回 HTTP `400`、业务码 `10001`。`total` 为非负 64 位整数。列表内容放在统一响应的 `data` 中：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "page": 1,
    "pageSize": 20,
    "total": 0
  },
  "traceId": "f1c438516dbd4e3190e439b457a40d95"
}
```

## 工具目录模型

```json
{
  "code": "calculator",
  "name": "计算器",
  "description": "四则运算、小数、括号和百分比，本地计算。",
  "categoryCode": "CALC",
  "icon": "calculator",
  "keywords": ["计算器", "计算", "四则运算", "calculator", "arithmetic"],
  "mode": "LOCAL",
  "requiresLogin": false,
  "status": "ENABLED",
  "version": 1,
  "sortOrder": 10,
  "isFeatured": true
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | string | 稳定且唯一的工具编码 |
| `name` | string | 面向用户的工具名称 |
| `description` | string | 简短说明 |
| `categoryCode` | string | 稳定分类编码，例如 `CALC` |
| `icon` | string \| null | 客户端可识别的图标资源名 |
| `keywords` | string[] | 搜索别名和关键词 |
| `mode` | enum | `LOCAL`、`SERVER` 或 `HYBRID` |
| `requiresLogin` | boolean | 使用工具是否要求登录 |
| `status` | enum | `ENABLED`、`DISABLED` 或 `MAINTENANCE` |
| `version` | integer | 工具元数据版本 |
| `sortOrder` | integer | 升序展示顺序 |
| `isFeatured` | boolean | 是否为推荐工具 |

数据库中的 `tool_code` 映射为 API 的 `code`，`keywords_json` 映射为 `keywords`；分类关联映射为 `categoryCode`。目录列表只返回 `ENABLED` 且未软删除的工具，所属分类也必须启用且未软删除。详情接口规划用 `30002` 或 `30003` 告知客户端工具已关闭或维护中。客户端仅把图标名映射到内置资源，不把服务端字符串当作代码或资源路径执行。数据库主键、`config_json`、时间及删除标记不出现在目录响应中。

Android 已建立 `ToolMetadata` 领域模型、`ToolDefinition` 本地执行契约及 `ToolCatalogDto` / `ToolCatalogPageDto` 传输结构，字段映射见 [工具模型说明](TOOL_MODEL.md)。DTO 保留分类、模式和状态的字符串编码，映射时拒绝未知或无效元数据；`total` 使用 `Long`。所需 Android 权限、设备支持检查和 Compose 页面由客户端实现声明，不属于服务端目录响应。JSON 解析、网络请求、缓存和目录合并尚待客户端远程刷新任务接入，当前首页继续读取内置注册中心。

## 工具目录接口

| 方法与路径 | 规划认证 | 用途 | 当前状态 |
| --- | --- | --- | --- |
| `GET /api/v1/tools` | 无 | 分页获取启用的工具，可按分类筛选 | 已实现，验证范围见目录接口说明 |
| `GET /api/v1/tools/{code}` | 无 | 获取工具详情 | 待实现 |
| `GET /api/v1/tools/search?q={query}` | 无 | 搜索名称、编码、分类、关键词和说明 | 待实现 |
| `GET /api/v1/tools/featured` | 无 | 获取推荐工具 | 待实现 |
| `GET /api/v1/categories` | 无 | 获取启用的工具分类 | 待实现 |

### `GET /api/v1/tools`

查询参数：

| 参数 | 类型 | 必需 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 页码，默认 `1`，范围 `1..2147483647` |
| `pageSize` | integer | 否 | 每页条数，默认 `20`，范围 `1..100` |
| `categoryCode` | string | 否 | 精确分类编码，匹配 `^[A-Z][A-Z0-9_]{0,31}$`；省略表示全部分类 |

按 `sortOrder` 升序、同序时按 `code` 升序返回可见工具；与 Android 注册中心的顺序约定一致。响应 `data` 使用分页结构，`records` 为工具目录模型数组。数量和记录在同一 MySQL 可重复读的只读事务内查询，`total` 为当前筛选条件下的总条数。合法但不存在、关闭或已删除的分类返回空页；越界页返回空 `records` 并保留 `total`。空目录返回 `records=[]`、`total=0`；V3 已登记实际计算器元数据，已有同编码记录的状态继续由原配置决定。计算在 Android 本地执行，不增加服务端计算接口。目录显示文本当前主语言为中文，客户端内置工具按本机资源本地化。

分类编码按大小写精确匹配，不自动修剪或转换；空白、空字符串、小写、超长或格式不合法时返回 `400/10001/data=null`。分页参数的空字符串使用默认值。

数据库连接失败、资源不可用或查询超时返回 `503/10008/data=null`。关键词必须是字符串数组，每项包含非空白文本；目录数据不符合该约定或其他未预期错误返回 `500/10000/data=null`。这些错误均使用既有响应封装与 `X-Trace-Id`，不返回 SQL、数据库内容或原始异常。

### `GET /api/v1/tools/{code}`

路径参数 `code` 是工具稳定编码。成功时 `data` 为单个工具目录模型。工具不存在返回 `30001`；已关闭返回 `30002`；维护中返回 `30003`。

### `GET /api/v1/tools/search?q={query}`

`q` 必须包含非空文本；可附带 `page`、`pageSize`。搜索名称、英文名称（若有）、编码、分类名称、关键词和说明。排序优先级：工具名完全匹配、工具名开头匹配、关键词、说明、分类；同级结果按 `sortOrder` 稳定排序。客户端可在结果评分中再结合最近使用和收藏权重。

### `GET /api/v1/tools/featured`

可附带 `page`、`pageSize`。返回 `isFeatured=true` 且 `status=ENABLED` 的工具，按 `sortOrder` 升序排列。

### `GET /api/v1/categories`

返回启用的分类，元素字段为 `code`、`name`、`icon`、`description`、`sortOrder`。按 `sortOrder` 升序排列。

## 其他已规划端点

下列端点来自项目规格，待各业务阶段实现时补充完整请求与响应模型：

| 方法与路径 | 认证 | 阶段 |
| --- | --- | --- |
| `POST /api/v1/auth/register`、`POST /api/v1/auth/login` | 无 | Auth & Sync |
| `POST /api/v1/auth/refresh`、`POST /api/v1/auth/logout`、`GET /api/v1/auth/me` | 按端点 | Auth & Sync |
| `GET /api/v1/favorites`、`POST /api/v1/favorites/{toolCode}`、`DELETE /api/v1/favorites/{toolCode}` | 是 | Auth & Sync |
| `GET /api/v1/recent`、`POST /api/v1/recent/{toolCode}`、`DELETE /api/v1/recent` | 是 | Auth & Sync |
| `GET /api/v1/settings`、`PUT /api/v1/settings` | 是 | Auth & Sync |
| `POST /api/v1/sync/push`、`GET /api/v1/sync/pull` | 是 | Auth & Sync |
| `POST /api/v1/feedback`、`GET /api/v1/feedback/my` | 按端点 | 后续阶段 |
| `POST /api/v1/tools/ocr`、`POST /api/v1/tools/image/enhance`、`POST /api/v1/tools/pdf/convert`、`POST /api/v1/tools/translate` | 是 | Cloud Tools |

云端文件接口必须另外定义文件大小、MIME 白名单、超时、配额、限流和权限校验。

## 错误码

| 业务码 | 含义 |
| --- | --- |
| `0` | 成功 |
| `10000` | 通用错误 |
| `10001` | 参数错误 |
| `10002` | 未登录 |
| `10003` | Token 无效 |
| `10004` | Token 过期 |
| `10005` | 无权限 |
| `10006` | 资源不存在 |
| `10007` | 请求过于频繁 |
| `10008` | 服务暂不可用 |
| `20001` | 用户不存在 |
| `20002` | 用户已存在 |
| `20003` | 密码错误 |
| `30001` | 工具不存在 |
| `30002` | 工具已关闭 |
| `30003` | 工具维护中 |
| `40001` | 文件过大 |
| `40002` | 文件类型不支持 |
| `40003` | 文件解析失败 |
| `50001` | 第三方服务错误 |

## 客户端缓存和离线行为

首页和搜索应先使用内置工具元数据启动，不等待目录请求。目录请求成功后更新本地缓存；请求失败时继续使用上次缓存和内置工具。`LOCAL` 工具不请求服务端执行；`SERVER` 工具需要连接；`HYBRID` 工具可优先本地处理，并在用户可感知时使用云端能力。

## 参考

- [项目规格：REST API](../toolbox-vibe-spec/PROJECT_SPEC.md#22-rest-api-规范)
- [项目规格：统一错误码](../toolbox-vibe-spec/PROJECT_SPEC.md#23-统一错误码)
- [项目规格：API 分页](../toolbox-vibe-spec/PROJECT_SPEC.md#37-api-分页)
