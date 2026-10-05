# 对象存储（MinIO）

2026-10-05，Phase 6 第一项 MinIO。服务端接入 MinIO Java SDK 8.5.17，提供有界上传、服务端 SHA-256、预签名下载与删除；存储默认关闭，关闭时写入类操作统一返回 503/10008，不影响现有接口与健康检查。本项无数据库结构变更。

## 配置

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `STORAGE_ENABLED` | `false` | 为 true 时才创建 MinIO 客户端与健康指标；必须同时配置密钥 |
| `MINIO_ENDPOINT` | `http://127.0.0.1:9000`（Compose：`http://minio:9000`） | SDK 访问地址；**预签名 URL 会使用该主机名签名**，因此生产环境必须填客户端也能解析的公开地址（如 `https://files.example.com`） |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | 无 | 启用时必填（Compose 复用 `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD`） |
| `MINIO_BUCKET` | `toolbox-files` | 小写合法桶名，首次使用时懒创建 |
| `MINIO_PRESIGNED_EXPIRY_SECONDS` | `900` | 60–604800 |
| `MINIO_MAX_OBJECT_BYTES` | `26214400`（25 MiB） | 1 B–100 MiB；同时受 `spring.servlet.multipart` 的 32MB 限制保护 |
| `MINIO_ALLOWED_CONTENT_TYPES` | png/jpeg/webp/pdf/plain | 逗号分隔的小写 MIME 列表 |

`minio` 服务位于 Compose 的 `storage` profile；启用存储的部署必须同时启用该 profile，否则健康检查会 DOWN（这是运维选择：启用即代表存储是必需依赖）。未启用时不会注册存储健康指标，`/actuator/health` 不受影响。

## 管理员接口

全部位于 `/api/v1/admin/storage`，需要管理员 Bearer JWT：

| 方法与路径 | 说明 |
| --- | --- |
| `GET` | 返回 `enabled`、`bucket`、`maxObjectBytes`、`presignedExpirySeconds`、`allowedContentTypes`；不回显地址与密钥 |
| `POST /objects`（multipart `file`） | 上传单个对象，201；服务端生成键 `objects/yyyyMM/<uuid>.<ext>`，返回 `key`/`size`/`sha256`/`contentType` |
| `GET /objects/presigned?key=&expirySeconds=` | 为已存在对象生成预签名 GET URL |
| `DELETE /objects?key=` | 删除对象；不存在返回 404/10006 |

错误码：超过上限 413/40001；类型不在白名单 415/40002；空文件或非法键 400/10001；存储关闭或 MinIO 不可用 503/10008。上传与删除写入 `admin_operation_log`（`module='storage'`，操作 `UPLOAD`/`DELETE`）。

## 边界与安全

- 键完全由服务端生成，客户端只能通过严格模式 `^[a-z0-9][a-z0-9._/-]{0,191}$` 且禁止 `..`、`//`、结尾 `.`/`/` 的键访问；不接受客户端提供的文件名或路径。
- 大小与类型在读取流之前校验；SHA-256 使用 `DigestInputStream` 与上传同一遍流计算，不信任客户端哈希，不二次读取对象。
- 上传异常统一映射为 503/10008，日志只记录固定原因，不打印对象内容、凭据或签名 URL。
- 桶懒创建并缓存结果；并发首次写入由 `synchronized` 保护。
- 预签名 URL 使用 SDK 配置的端点签名，代码不做主机重写（重写会破坏 SigV4 的 Host 签名并返回 403）；本地开发因此只能在 Compose 网络内取回预签名内容，生产需要把 `MINIO_ENDPOINT` 配成客户端可达的公开域名。

## 验证

- Server 单元测试 99/99 通过（存储部分新增 11 项：配置校验与默认值、MinIO 单遍流式 SHA-256、大小/类型拒绝、预签名透传与过期夹取、键规范化与穿越拒绝、管理员服务的大小/类型/键/过期/存在性校验与审计、关闭时 fail-closed）。
- `scripts/check_storage.py` 在本地 MinIO 容器上执行 23/23 项校验：匿名 401、状态与限制、错误类型 415/40002、空文件 400/10001、上传 201 且 size/服务端 sha256/contentType 正确、预签名 URL 指向已配置端点、在 Compose 网络内下载校验哈希一致、非法键 400、缺失对象 404、非法过期 400、删除后重复删除 404、删除后预签名 404、旧预签名 URL 404、`module='storage'` 审计行 ≥2；结束时删除对象与审计行。
- 本地以 `STORAGE_ENABLED=true` 验证后已恢复默认（关闭）运行；`admin_user` 保持为空。

## 后续

面向用户的文件上传/下载 API、每用户配额、病毒扫描与内容嗅探、临时文件清理、CDN/反向代理下的公开域名与缓存策略分别属于 Quotas、File security 与部署任务；当前接口定位为管理/基础设施能力，供后续 OCR、PDF 等云端工具复用。

上传内容的魔术字节校验与类型一致性规则见 [文件安全](FILE_SECURITY.md)：声明类型与真实内容不一致会被 415/40002 拒绝。

上传记账与配额（每所有者字节/对象数、全局字节、`stored_object` V33）见 [存储配额](QUOTAS.md)。
