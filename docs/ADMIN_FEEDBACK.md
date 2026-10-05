# 用户反馈（Feedback）

2026-10-05，Phase 5 第七项 Feedback。登录用户可以提交问题/建议并查看自己的反馈与管理员回复；管理员分页检索、流转状态并回复。V32 新增 `user_feedback` 表。

## 数据与接口

`user_feedback`：`user_id`（外键 `ON DELETE CASCADE`）、`type`（BUG/SUGGESTION/OTHER）、`content`、`contact`、`status`（PENDING/PROCESSING/RESOLVED）、`reply`、`replied_at`、时间戳；约束保证类型与状态取值合法。

用户端（需要登录，身份取自 JWT）：

| 方法与路径 | 说明 |
| --- | --- |
| `POST /api/v1/feedback` | 提交反馈，201；`type`、`content` 必填，`contact` 可选 |
| `GET /api/v1/feedback/my` | 仅返回调用者自己的反馈（最近 50 条），含状态与管理员回复 |

管理端（管理员 Bearer JWT）：

| 方法与路径 | 说明 |
| --- | --- |
| `GET /api/v1/admin/feedback` | 分页列表；`keyword`（≤128，匹配内容/联系方式/用户邮箱）、`status` 过滤 |
| `PATCH /api/v1/admin/feedback/{id}` | `{"status": "PENDING|PROCESSING|RESOLVED", "reply": "可选"}` |

## 校验与边界

- 类型与状态大小写不敏感，服务端转大写；非法值 400/10001。
- 正文 1–2000 字符，允许 `\n` 分段，其余控制字符拒绝；联系方式可选、≤128 字符、单行。
- 回复 ≤2000 字符，允许 `\n`；一旦写入即记录 `replied_at`，再次提交回复会覆盖旧文本并刷新时间（不支持清空）。
- 未登录访问用户端点 401/10002；不存在或已删除（用户被删除后级联）的反馈返回 404/10006。
- 用户列表只返回自己的记录，不返回他人 id、邮箱或内部审计字段。

## 审计

管理员操作写入 `admin_operation_log`：`module='feedback'`，操作 `STATUS_<新状态>`（未回复）或 `REPLY`（带回复）。用户提交不写管理员审计。

## 后台页面

`admin/src/views/FeedbackView.vue`（导航“用户反馈”）：关键词与状态筛选、分页表格（ID、用户邮箱、类型、内容、状态、回复、提交时间）、处理对话框（状态选择 + 回复文本域，展示完整原文）。`admin/src/api/feedback.ts` 严格校验响应结构。

## 未实现

- Android 已接入提交、本人反馈与管理员回复，含账号隔离、限流与网络歧义提示。见 [客户端反馈](ANDROID_FEEDBACK.md)，设备联调延期。
- 附件上传、分类标签、优先级、指派/内部备注、回复通知推送、批量处理。

## 验证

- Server 单元测试 85/85 通过（反馈部分新增 3 项：列表过滤校验与空页、状态/回复校验与审计、非法状态/控制字符/超长回复/缺失记录）。
- `scripts/check_admin_feedback.py` 在本地栈对临时管理员执行 17/17 项校验：匿名用户端点 401、非法类型/空内容/超长内容 400、提交 201 且状态 PENDING、`/feedback/my` 可见本人记录、匿名管理端 401、关键词列表命中、状态过滤、非法状态过滤 400、处理中流转、控制字符回复 400、缺失 id 404、回复后用户侧可见 RESOLVED 与回复文本、`module='feedback'` 审计行 ≥2；结束时删除临时数据。
- 同批次工具 20/20、分类 22/22、推荐位 26/26、用户 19/19、公告 25/25 回归通过；同时段管理员认证回归 20/20 通过；`admin` 后台 `npm run typecheck && vite build` 成功；V32 已在本地应用（schema 32）。
