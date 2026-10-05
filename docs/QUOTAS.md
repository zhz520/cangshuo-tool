# 存储配额（Quotas）

2026-10-05，Phase 6 Quotas。对象存储新增每所有者与全局用量记账（V33 `stored_object`）与写入前配额校验：按对象数、按字节与全局总量三道限制，超限返回 413/40004，删除即释放额度。本项无外部依赖。

## 数据与配置

`stored_object`：`owner_type`（ADMIN/USER/SYSTEM）、`owner_id`、唯一 `object_key`、`size_bytes`、`sha256`、`content_type`、`created_at`、`deleted_at`。删除为软删除，历史保留可追溯；用量统计只计入 `deleted_at IS NULL`。

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `STORAGE_QUOTA_ENABLED` | `true` | 关闭后跳过写入前校验（用量仍记账） |
| `STORAGE_MAX_OWNER_BYTES` | 104857600（100 MiB） | 单个所有者未删除对象总字节上限 |
| `STORAGE_MAX_OWNER_OBJECTS` | 200 | 单个所有者未删除对象数上限 |
| `STORAGE_MAX_TOTAL_BYTES` | 1073741824（1 GiB） | 全库未删除对象总字节上限 |

校验启动时拒绝非法组合：任一限制必须为正、`STORAGE_MAX_OWNER_BYTES ≤ STORAGE_MAX_TOTAL_BYTES`、上限不超过 10 TiB。

## 行为

- 上传前按“当前用量 + 本次大小”做三次校验（所有者字节、所有者对象数、全局字节）；任一条超限直接返回 413/40004，不写入 MinIO、不占额度。
- 上传成功后写入 `stored_object`；删除流程先确认存在未删除元数据（否则 404/10006），再删除 MinIO 对象，最后软删除元数据并释放额度；元数据更新失败返回 503/10008 并可重试。
- `GET /api/v1/admin/storage` 返回 `quotaEnabled`、`ownerUsageBytes`、`ownerUsageObjects`、`totalUsageBytes` 与三个上限，管理端可直接核对。当前上传入口是管理员接口，所有者类型为 `ADMIN`；后续面向用户的文件 API 复用同一服务时按 `USER` 记账。
- 并发说明：校验与写入之间存在竞态窗口（两个并发上传可能同时通过校验），最终一致性由下一次校验收敛；严格并发配额需要数据库行锁或原子计数器，属于后续增强。

## 验证

- Server 单元测试 107/107 通过（配额部分新增 5 项：配置默认值/非法组合、字节/对象数/全局三种拒绝、关闭时跳过记账、快照字段）。
- `scripts/check_storage.py` 在真实 MinIO 上执行 38/38 项校验，其中配额场景以 `STORAGE_MAX_OWNER_BYTES=100`、`STORAGE_MAX_OWNER_OBJECTS=3` 运行：上传后用量与对象数正确、按字节超出返回 413/40004、按对象数超出返回 413/40004、删除后用量归零、`stored_object` 无未删除行；V33 迁移 success=1。
- 小配额仅在本次校验运行中通过环境变量临时启用，校验后服务端恢复默认（存储关闭、默认配额）。
