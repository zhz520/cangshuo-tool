# 工具目录列表接口

本次对应 Phase 1 的 `GET /tools` 任务，实现 `GET /api/v1/tools` 的服务端列表与对应 Android 传输模型。该次未增加数据库迁移或工具种子；后续 [本地计算器任务](CALCULATOR.md) 通过 V3 登记已实现工具的元数据。

## 服务端分层

```text
ToolCatalogController
  → ToolCatalogService
  → ToolCatalogRepository
  → NamedParameterJdbcTemplate
  → MySQL tool_definition / tool_category
```

复用既有 Spring JDBC、HikariCP、Jackson、Validation 和统一响应，不新增持久化框架或依赖。Repository 只查询元数据；Service 解析关键词并组装公开 DTO；Controller 处理参数与 traceId。

## 查询行为

| 项目 | 约定 |
| --- | --- |
| 分页 | 页码从 1 开始，默认 `page=1&pageSize=20`，每页最多 100 条；分页参数省略或为空时使用默认值 |
| 分类 | 可选 `categoryCode`，1–32 字符，匹配 `^[A-Z][A-Z0-9_]{0,31}$`，精确匹配，不规范化大小写或空白 |
| 可见性 | 工具 `ENABLED` 且未删除，所属分类启用且未删除；登录要求作为元数据返回，不影响浏览 |
| 排序 | `sort_order ASC, tool_code ASC`，同序规则与 Android 注册中心一致 |
| 空目录 | 返回成功封装、空 `records` 和真实 `total`；不添加占位工具 |
| 未知分类 | 格式合法但不存在的分类返回空页，非法格式返回 `400/10001` |
| 越界页 | 返回空 `records`，保留筛选后的 `total` |
| 一致性 | 同一只读、`REPEATABLE_READ` 事务内查询总数和记录；偏移量用 `long` 计算，避免整数溢出 |
| SQL | 仅拼接固定片段，分类、条数及偏移均使用绑定参数 |

返回字段与 [API 契约](API.md#get-apiv1tools) 一致。`isFeatured` 使用显式 JSON 属性名；可空 `icon` 在 OpenAPI 3.1 中声明为 string/null。数据库 ID、配置 JSON、时间、权限和执行实现不下发。关键词必须为非空白字符串组成的数组，数据格式异常返回固定的 `500/10000` 错误，不返回或记录原始 JSON。

## 访问控制与错误

只为精确路径 `GET /api/v1/tools` 开放匿名访问，未开放工具路径通配符。详情、搜索、推荐、分类及写操作仍按后续任务接入，默认受现有身份认证规则保护。CORS 精确来源白名单和仅 local 环境开放文档的配置沿用既有规则。

分页类型或范围错误、分类格式错误由 Spring MVC 内置方法校验交给现有异常处理，返回 `400/10001/data=null`。Controller 不添加类级 `@Validated`，以使用 MVC 的方法参数校验。数据库资源错误、查询超时及事务无法建立时返回 `503/10008/data=null`；其余错误返回 `500/10000`。日志只包含异常类型与既有 traceId，不输出请求值、SQL、连接信息、关键词或堆栈。

## Android 契约模型

`core/network/model/ToolCatalogDto` 保持接口字段命名，分类、模式、状态保存为字符串，`toMetadataOrNull()` 校验并映射到 `ToolMetadata`。未知或不合法的条目返回 null，不误归类为其他、不注册执行实现。`ToolCatalogPageDto` 使用 `Long` 保存 total，并校验页码、每页条数和记录数量。

这些是纯 Kotlin DTO，尚未加入 JSON 序列化、Retrofit、请求、缓存或首页远程刷新。客户端当前已注册本地计算器，仍从内置注册中心启动。Admin 当前只消费健康接口，现有响应模型及业务页面沿用原任务。

## 本地使用

按 [服务端说明](../server/README.md#构建与启动)启动更新后的 JAR，或重建本地 Compose 的 server 镜像。服务运行后可在 local Swagger 中查看 `getTools`，示例地址：

- 全部工具：`http://localhost:8080/api/v1/tools`
- 按分类：`http://localhost:8080/api/v1/tools?page=1&pageSize=20&categoryCode=CALC`
- 后台网关代理：`http://localhost:8088/api/v1/tools`

这些地址用于访问更新后的本地服务；正式域名发布仍属于部署任务。

## 验证记录

2026-10-04 完成本轮构建及本地启动门禁：

- Windows JDK 21.0.9：`package -Dmaven.test.skip=true` 成功，用时 1 分 32 秒，编译 19 个 Java 源文件并生成可运行 JAR。
- Android：`:app:assembleDebug :app:lintDebug` 成功，用时 41 秒；Lint 为 0 错误、10 个既有版本/应用图标提示。
- 本机 local 预览已更新并成功启动，监听 8080；启动日志确认校验现有两份迁移，数据库版本仍为 2，无需新迁移。
- 本地 Compose 的 server 镜像构建成功，API 容器已更新并通过现有启动健康门禁；其他服务和数据卷继续复用。
- Git 差异空白检查通过，没有修改已执行的 migration。

未新增或执行自动化测试，未请求目录接口进行功能验证；分页、筛选、JSON 输出、参数错误和异常分支的运行行为尚待验证，Android 网络接入及设备联调尚未实现。启动及健康门禁不表示业务目录行为已经验证。

## 实现依据

- [Spring MVC 方法参数校验](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)
- [NamedParameterJdbcTemplate](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html#jdbc-NamedParameterJdbcTemplate)
- [Spring 声明式事务](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)
