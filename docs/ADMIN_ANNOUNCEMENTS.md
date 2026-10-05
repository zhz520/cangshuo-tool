# 公告管理（Announcements）

2026-10-05，Phase 5 第六项 Announcements。管理员创建公告草稿、发布/下线，并可用 UTC 时间窗定时展示；公开接口只返回当前可见的公告。V31 新增 `announcement` 表。

## 数据与接口

`announcement`：`title`、`body`、`level`（INFO/WARNING/CRITICAL）、`status`（0 草稿 / 1 已发布）、`start_at`、`end_at`、时间戳与 `deleted_at`；检查约束限制级别、状态与时间窗。

| 方法与路径 | 说明 |
| --- | --- |
| `GET /api/v1/admin/announcements` | 列出未删除公告（≤128，新在前，含草稿） |
| `POST /api/v1/admin/announcements` | 创建，201；`enabled=false` 即草稿 |
| `PUT /api/v1/admin/announcements/{id}` | 更新标题、正文、级别、发布状态与时间窗 |
| `PATCH /api/v1/admin/announcements/{id}/status` | `{"enabled": true|false}` 发布/下线（幂等） |
| `DELETE /api/v1/admin/announcements/{id}` | 软删除 |
| `GET /api/v1/home/announcements` | 匿名；仅返回 `status=1`、未删除且在当前时间窗内的公告，最新 5 条，字段 `id`/`title`/`body`/`level` |

## 校验与边界

- 标题 1–128 字符且必须是单行（拒绝换行与控制字符）；正文 1–2000 字符，允许 `\n` 分段，其余控制字符拒绝。
- 级别仅 `INFO|WARNING|CRITICAL`（默认 INFO，大写规范化）；时间窗为 UTC ISO-8601，可只填一端，`endAt` 必须晚于 `startAt`，非法格式或倒置区间 400/10001。
- 未知 id、非正数或非数字 id：前者 404/10006，后者 400/10001。发布/下线在状态未变化时不写审计，直接返回当前记录。
- 正文按纯文本返回，不渲染 HTML，避免在客户端产生注入面。

## 审计

写操作写入 `admin_operation_log`：`module='announcement'`，操作 `CREATE`、`UPDATE`、`PUBLISH`/`UNPUBLISH`、`DELETE`。

## 后台页面

`admin/src/views/AnnouncementsView.vue`（导航“公告管理”）：表格展示 ID、标题、级别标签、状态（展示中/已发布未生效/草稿）、时间窗与更新时间；新增/编辑对话框校验标题单行、正文长度、级别与 UTC 时间窗；发布/下线与删除均有确认弹窗。`admin/src/api/announcements.ts` 严格校验响应结构。

## 未实现

- Android 首页尚未消费 `GET /home/announcements`（接入属于后续 Android 任务，不能声称 App 已展示公告）。
- 富文本/Markdown、多语言正文、定时草稿自动发布（当前靠时间窗在读取时判定）、已读状态与推送。

## 验证

- Server 单元测试 82/82 通过（公告部分新增 4 项：输入校验矩阵、创建审计、幂等发布/下线审计、缺失记录 404）。
- `scripts/check_admin_announcements.py` 在本地栈对临时管理员执行 25/25 项校验：匿名读取 200、匿名管理 401、草稿 201 且公开不可见、非法级别/多行标题/空正文 400、倒置时间窗与非法日期 400、发布后公开可见、未来时间窗不生效、清除时间窗恢复、下线后不可见、非法 id 400、缺失 id 404、软删除后公开不可见、重复删除 404、数据库 `deleted_at` 标记与 `module='announcement'` 审计行 ≥4；结束时删除临时数据。
- 同批次工具 20/20、分类 22/22、推荐位 26/26、用户 19/19 回归通过；`admin` 后台 `npm run typecheck && vite build` 成功；V31 已在本地应用（schema 31）。
