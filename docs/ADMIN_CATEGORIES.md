# 分类管理（Category CRUD）

2026-10-05，Phase 5 第三项 Category CRUD。管理员可以维护分类编码、名称、图标、说明、排序与启用状态；停用分类会同时隐藏其下工具，删除只允许空分类。本项没有数据库结构变更：复用 V1 的 `tool_category`，因此不新增 Flyway 迁移。

## 接口

所有端点位于 `/api/v1/admin/categories`，需要管理员 Bearer JWT；普通用户令牌返回 403/10005。

| 方法与路径 | 说明 |
| --- | --- |
| `GET /api/v1/admin/categories` | 列出未删除分类（最多 128 条），按排序、编码升序；每行带 `toolCount`（该分类下未删除工具数）与 `updatedAt` |
| `POST /api/v1/admin/categories` | 创建，返回 201；编码重复 409/30006 |
| `PUT /api/v1/admin/categories/{code}` | 更新名称、图标、说明、排序、启用状态（编码不可改） |
| `PATCH /api/v1/admin/categories/{code}/status` | `{"enabled": true|false}` |
| `DELETE /api/v1/admin/categories/{code}` | 软删除；分类下仍有未删除工具时返回 409/30007，不会产生孤立工具 |

tool-editor 的只读下拉仍使用 `GET /api/v1/admin/tools/categories`（同一张表的投影）；两个入口的差异见 [工具管理](ADMIN_TOOLS.md)。

## 校验与边界

- 编码：`^[A-Z][A-Z0-9_]{0,31}$`；服务端会把输入转为大写后再校验与存储。唯一索引覆盖软删除行，因此删除后用同一编码重建会被 409/30006 拒绝。
- 名称：1–64 字符；说明：≤500 字符；两者拒绝控制字符。图标：可选，`^[a-z][a-z0-9_]{0,127}$`。
- 排序：0–100000；启用默认 true，显式 `false` 表示停用。
- 参数错误 400/10001；未知或已删除分类在更新/状态/删除上返回 404/10006。

## 与公开目录的联动

公开目录 `GET /tools` 的查询条件包含 `c.status = 1 AND c.deleted_at IS NULL`，因此：

- 停用分类后，其下所有 ENABLED 工具立即从匿名目录与 Android 搜索结果中消失；重新启用后恢复。
- `toolCount` 只统计未删除工具，删除工具（软删除）后计数下降，此时才允许删除分类。

实测：创建分类 → 创建 ENABLED 工具 → 分类停用后工具从公开目录消失 → 重新启用恢复 → 软删除工具 → 删除分类成功。

## 审计

写操作通过 `AdminAuditService`（独立 `REQUIRES_NEW` 事务）写入 `admin_operation_log`：`module='category'`，操作 `CREATE`、`UPDATE`、`STATUS_ENABLED`/`STATUS_DISABLED`、`DELETE`，结果 `SUCCESS`。读取不写审计。

## 后台页面

`admin/src/views/CategoriesView.vue`：表格展示编码、名称、说明、排序、工具数、启用开关与操作；新增/编辑对话框校验编码、名称、排序与图标格式；停用走确认弹窗（提示会隐藏 N 个工具）；删除要求输入分类编码二次确认，且工具数大于 0 时直接提示先处理工具。`admin/src/api/categories.ts` 严格校验响应结构。

## 未实现

分类合并、批量调整工具所属分类、拖拽排序、分类级可见性计划（定时上下线）、操作日志查询界面。

## 验证

- Server 单元测试 68/68 通过（分类部分新增 7 项：输入规范化/拒绝矩阵、重复编码、更新前置校验、状态与删除审计、在用保护、缺失分类与畸形编码）。
- `scripts/check_admin_categories.py` 在本地栈对临时管理员执行 22/22 项校验，覆盖上表全部接口、409/30006、400/10001、409/30007、公开目录隐藏/恢复、软删除标记、重复删除 404 与 `module='category'` 审计行；结束时删除临时分类、工具与审计行。
- 同批次工具 CRUD 回归 20/20 通过；`admin` 后台 `npm run typecheck && vite build` 成功（新增 `CategoriesView` 产物）。
- 本地库保持 20 个工具、13 个分类、`admin_user` 为空，无临时数据。
