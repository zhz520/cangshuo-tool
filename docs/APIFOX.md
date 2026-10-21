# Apifox 接口文档同步

指定项目：[cangshuo-tool / 8952528](https://app.apifox.com/project/8952528)。2026-10-21 在项目「Git & Branches」核对，该项目已连接 `https://github.com/zhz520/cangshuo-tool.git` 的 `main`；Specs 能读取仓库中的 `docs/`。接口规范保存在 [openapi.json](openapi.json)，不使用生产 Swagger 的公开地址。

## 更新流程

在项目根目录运行：

```powershell
$env:JAVA_HOME='D:\Android\Android Studio\jbr'
python scripts/export_openapi.py
# 已有完整 Maven 缓存时可附加 --offline。
```

该命令通过真实 Spring Boot MVC/Springdoc 装配 Controller 和 DTO；数据库、Redis、会话与管理员初始化使用测试替身，Flyway 与外部服务关闭，不读取本地 `.env`，不访问生产或提交用户操作。Maven 依赖和可控临时文件分别保存在 `server/.maven-cache`、`temp/openapi`；Windows 输出强制要求 D 盘。

1. 修改 Controller/DTO 后重新生成，检查 `docs/openapi.json` 的差异。
2. 同步本文档、`API.md` 和实际客户端模型，运行服务端测试。
3. 提交并推送 `main`，让已绑定的 Apifox Git 项目同步新提交。
4. 在 Apifox「Specs → API」查看 `docs/openapi.json` 及接口列表，并确认最新提交。初次接入可能需要刷新或点击 Git 同步；不要另建一份脱离仓库的规范。

校验已有快照：`python scripts/export_openapi.py --check`。CI 在 Maven package 成功后使用 `--check --from-build` 对照刚生成的文件，避免重复构建；不能用旧构建声称验证了新代码。CI 保存 `openapi-contract` artifact，差异会使 Server 门禁失败。

## 契约覆盖

当前 **74 个操作 / 60 个路径 / 127 个 schema**。只包含真实 `/api/v1/**` 业务路由；Android 离线工具不伪造服务端接口，未实现的路线图接口不导入。

- OpenAPI 3.1；生产/本地 server 地址，路径已经带 `/api/v1`。
- 参数、请求体、Bean Validation、分页 DTO 与成功返回由真实源码生成。
- 匿名/用户 Bearer/管理员 Bearer 边界；匿名接口携带错误 Token 也可能返回 401。
- 稳定的 Controller/方法 operationId，保留代码中显式指定的 operationId。
- JSON 通用错误封装 `ApiFailure`（data=null）、常见 HTTP 错误和 `X-Trace-Id`；429 包含 `Retry-After`。业务特有错误码仍参考 [API.md](API.md)、[Zepp Life](ZEPP_STEPS.md)、[短视频解析](SHORT_VIDEO_PARSE.md)。
- 两个不同的能力 DTO 明确命名为 `ZeppStepsCapabilities` 与 `MediaCapabilities`，避免同名嵌套 record 串字段。
- Zepp Life 提交成功使用具体 `ZeppStepsResponse`；不再导出泛化 Object。
- 媒体下载为二进制 200/206；图集为 ZIP 二进制，不伪装 JSON；失败仍可返回 JSON。

## 验证与边界

`OpenApiExportTest` 对照实际 Spring MVC 映射逐项核对全部路由；每个 operationId 唯一、摘要非空、所有 `$ref` 可解析，且每个接口的匿名声明与实际 Spring Security AuthorizationManager 的授权结果一致。另校验两个能力模型、Zepp 成功 DTO 及媒体/ZIP 格式。导出过程中断言数据库、Redis、会话测试替身无业务调用。

本任务不改变生产鉴权、不启用公开 Swagger，也不修改数据库或客户端业务。接口文档导入成功不等于对全部写接口进行了生产调用验收。后续新增/修改接口必须重新生成、推送并核对 Apifox。
