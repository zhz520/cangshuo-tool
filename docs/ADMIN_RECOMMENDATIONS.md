# 推荐位管理（Recommendation slots）

2026-10-05，Phase 5 第四项 Recommendation slots。管理员维护首页推荐位：每个推荐位指向一个目录工具或一个 https 链接，并支持排序、启停与生效时间窗；公开接口 `GET /api/v1/home/recommendations` 只返回当前生效的推荐位。V30 新增 `home_recommendation` 表。

## 数据与迁移

`home_recommendation`：`slot_code`（唯一）、`title`、`subtitle`、`tool_code`（可空，外键到 `tool_definition.tool_code`，`ON DELETE SET NULL`）、`link_url`、`image_url`、`sort_order`、`status`、`start_at`、`end_at`、时间戳与 `deleted_at`；检查约束限制状态为 0/1，并要求 `end_at` 晚于 `start_at`。

## 接口

管理端 `/api/v1/admin/recommendations`（管理员 Bearer JWT）：

| 方法与路径 | 说明 |
| --- | --- |
| `GET` | 列出未删除推荐位（≤128），按排序、编码升序；每行带计算出的 `active` |
| `POST` | 创建，返回 201；编码重复 409/30008，引用工具不存在 404/30009 |
| `PUT /{slotCode}` | 更新标题、副标题、目标、图片、排序、启停与时间窗（编码不可改） |
| `PATCH /{slotCode}/status` | `{"enabled": true|false}` |
| `DELETE /{slotCode}` | 软删除 |

公开端 `GET /api/v1/home/recommendations`（匿名，最多 20 条）返回 `slotCode`、`title`、`subtitle`、`toolCode`、`linkUrl`、`imageUrl`，不含数据库 id、时间窗或审计信息。

## 校验与边界

- 编码：`^[a-z][a-z0-9_]{0,31}$`；唯一索引覆盖软删除行（删除后同编码重建返回 409/30008）。
- 标题 1–64 字符；副标题 ≤128 字符；拒绝控制字符。排序 0–100000；启用默认 true。
- 目标必须二选一：`toolCode`（小写编码格式，且必须是未删除工具）或 `linkUrl`（`https://`、≤500 字符、有主机、无用户信息）；同时提供或都不提供返回 400/10001。`imageUrl` 可选，同样是 https 链接。
- 时间窗使用 UTC ISO-8601（例如 `2026-10-05T00:00:00Z`），可只填一端；`endAt` 必须晚于 `startAt`，非法格式或倒置区间返回 400/10001。
- 生效判定：`status=1`、`deleted_at IS NULL`、`start_at` 为空或已到、`end_at` 为空或未到；停用、未到开始时间或已过结束时间的推荐位不会出现在公开接口。
- 未知或已删除推荐位在更新/状态/删除上返回 404/10006。

## 审计

写操作通过 `AdminAuditService` 写入 `admin_operation_log`：`module='recommendation'`，操作 `CREATE`、`UPDATE`、`STATUS_ENABLED`/`STATUS_DISABLED`、`DELETE`。

## 后台页面

`admin/src/views/RecommendationsView.vue`（导航“推荐位管理”，路径 `/banners`）：表格展示编码、标题、目标、排序、生效状态、时间窗与启停开关；新增/编辑对话框选择目标类型（工具/链接）并校验编码、标题、https、工具编码格式与 UTC 时间窗；删除要求输入编码二次确认。`admin/src/api/recommendations.ts` 严格校验响应结构。

## 未实现

- Android 首页尚未消费 `GET /home/recommendations`：App 目前仍用目录快照里的 `isFeatured` 展示推荐工具，接入新接口属于后续 Android 任务，不能声称已在 App 生效。
- 图片上传/裁剪（当前只接受外部 https 图片地址）、点击统计、排序拖拽、A/B 实验与定时发布预览。

## 验证

- Server 单元测试 73/73 通过（推荐位部分新增 5 项：输入规范化与时间窗解析、二选一/链接/时间窗拒绝矩阵、重复编码、未知工具、状态与删除审计）。
- `scripts/check_admin_recommendations.py` 在本地栈对临时管理员执行 26/26 项校验：匿名公开读取 200、匿名管理读取 401、同时/缺失目标 400、未知工具 404/30009、创建 201 且立即生效、重复 409/30008、公开接口可见、未来时间窗不生效且不可见、非法时间与倒置区间 400、清除时间窗后生效、停用后公开不可见、重新启用恢复、http 链接 400、软删除后公开不可见、重复删除 404、数据库 `deleted_at` 标记与 `module='recommendation'` 审计行；结束时删除临时数据。
- 同批次工具 20/20、分类 22/22 回归通过；`admin` 后台 `npm run typecheck && vite build` 成功；V30 已在本地应用（schema 30）。
- 本地库保持 20 个工具、13 个分类、0 个推荐位、`admin_user` 为空。
