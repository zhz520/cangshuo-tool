# 工具管理（Tool CRUD）

2026-10-05，Phase 5 第二项 Tool CRUD。管理员可以在后台分页查看、创建、编辑、上下线与软删除目录工具；公开目录 `GET /tools` 立即反映状态与删除变化。本项没有数据库结构变更：复用 V1 的 `tool_definition` 与 `tool_category`，因此不新增 Flyway 迁移。

## 接口

所有端点位于 `/api/v1/admin/tools`，需要管理员 Bearer JWT（`ROLE_ADMIN`/`ROLE_SUPER_ADMIN`）；普通用户令牌返回 403/10005。

| 方法与路径 | 说明 |
| --- | --- |
| `GET /api/v1/admin/tools` | 分页列表；`page`（1..2147483647）、`pageSize`（1..100）、可选 `keyword`（≤64 字符，匹配编码/名称/说明）、`categoryCode`、`status` |
| `GET /api/v1/admin/tools/categories` | 只读分类选项（`code`、`name`、`sortOrder`、`enabled`，最多 64 条、排除软删除）；完整的分类增删改属于下一项 Category CRUD |
| `GET /api/v1/admin/tools/{code}` | 单个工具详情，包含 `configJson` 与 `updatedAt` |
| `POST /api/v1/admin/tools` | 创建，返回 201；编码重复 409/30004，分类不存在 404/30005 |
| `PUT /api/v1/admin/tools/{code}` | 全量替换可编辑字段（编码不可改） |
| `PATCH /api/v1/admin/tools/{code}/status` | `{"status":"ENABLED|DISABLED|MAINTENANCE"}` |
| `DELETE /api/v1/admin/tools/{code}` | 软删除（写入 `deleted_at`）；公开目录与管理员列表都不再返回 |

管理视图比公开目录多返回：`id`、`categoryName`、`keywords` 数组、`configJson`、`updatedAt`、`requiresLogin`，以及非 ENABLED 状态与软删除前的记录。

## 校验与边界

- 编码：`^[a-z][a-z0-9_]{0,63}$`；唯一索引覆盖软删除行，因此删除后用同一编码重建会被 409/30004 拒绝。
- 名称：1–128 字符；说明：≤500 字符；两者拒绝控制字符。图标：可选，`^[a-z][a-z0-9_]{0,127}$`。
- 分类：`categoryCode` 为大写格式并必须存在于未删除的 `tool_category`（允许引用已停用分类，避免无法编辑历史工具）。
- 关键词：最多 20 个、每个 ≤32 字符，去除首尾空格并按出现顺序去重，写入 JSON 数组。
- 模式：`LOCAL|SERVER|HYBRID|WEB`；状态：`ENABLED|DISABLED|MAINTENANCE`（服务端大写规范化，列表过滤同样校验）。
- 版本 1–100000；排序 0–100000；推荐与需登录为布尔值。
- `configJson`：必须是 JSON 对象、≤10000 字符，入库前由服务端重新序列化为规范 JSON；数组、非法 JSON 返回 400/10001。当前没有 schema 约束（现存 `qr_studio` 的配置为 `{}`），WEB 工具的路径约定仍由客户端按工具编码拼 `/tools/<code>/`，待需要差异化路径时再新增约定。
- 参数错误统一 400/10001；未知或已删除工具返回 404/10006（`detail`/`update`/`status`/`delete` 一致）。

## 公开目录联动

`GET /api/v1/tools` 只返回 `status='ENABLED'` 且 `deleted_at IS NULL` 的记录，因此上线/下线/维护/软删除会立即影响 Android 目录与搜索结果；实测创建 ENABLED 工具后匿名目录可见，改为 DISABLED 后立即消失。

## 审计

每次写操作通过 `AdminAuditService`（独立 `REQUIRES_NEW` 事务）写入 `admin_operation_log`：`module='tool'`，操作为 `CREATE`、`UPDATE`、`STATUS_ENABLED`/`STATUS_DISABLED`/`STATUS_MAINTENANCE`、`DELETE`，结果为 `SUCCESS`。读取（列表/详情/分类）不写审计，避免噪声。

## 后台页面

`admin/src/views/ToolsView.vue` 提供筛选（关键词/分类/状态）、分页表格（编码、名称、分类、模式、状态、排序、推荐、版本、需登录）、新增/编辑对话框（含关键词与配置 JSON 校验）、状态切换与删除。删除使用 ElMessageBox 要求输入工具编码二次确认，状态变更使用确认弹窗；错误按 30004/30005/10001/10005/10002 分别提示。`admin/src/api/tools.ts` 严格校验响应结构（模式/状态枚举、字段类型、长度）。

## 未实现

更新人/变更历史查看、批量操作、导入导出、拖拽排序、配置 JSON 编辑器与 schema 校验、分类的增删改（下一项）、操作日志查询界面。

## 验证

- Server 单元测试 61/61 通过（新增 10 项：输入规范化/拒绝矩阵、创建重复与未知分类、更新前置校验、状态与删除审计、列表过滤校验、详情隐藏与畸形编码）。
- `scripts/check_admin_tools.py` 在本地栈对临时管理员执行 20/20 项校验：匿名 401、分类选项、列表分页、未知分类 404/30005、创建 201、重复 409/30004、匿名目录可见、详情、更新改名与排序、下线后目录消失、维护状态、非法状态 400/10001、非法配置 400/10001、畸形编码 400、删除后详情 404、数据库中 `deleted_at` 非空、`module='tool'` 审计成功行 ≥4；临时工具行与审计行在结束时删除。
- 同批次管理员认证回归 20/20 通过；`admin` 后台 `npm run typecheck && vite build` 成功（新增 `ToolsView` 产物）。浏览器自动化没有输入 API，因此页面上的表单操作未在真实浏览器点选，交互逻辑由 API 校验与类型构建覆盖。
- 本地库保持 20 个工具、`admin_user` 为空，无临时工具行。
