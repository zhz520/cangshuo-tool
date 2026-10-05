# 操作日志（Operation logs）

2026-10-05，Phase 5 第八项 Operation logs，Phase 5 最后一项。管理员分页查看审计表 `admin_operation_log`：模块、操作、请求、来源地址、结果与时间；只读，不提供修改或删除入口。本项没有数据库结构变更。

## 接口

`GET /api/v1/admin/logs`（管理员 Bearer JWT；匿名 401/10002，普通用户令牌 403/10005）：

| 参数 | 说明 |
| --- | --- |
| `page` / `pageSize` | 默认 1 / 20；`pageSize` 上限 100，越界 400/10001 |
| `module` | 可选，`[A-Z][A-Z0-9_]{0,31}`（大小写不敏感，服务端转大写）；数据库列排序规则不区分大小写，因此 `category` 与 `CATEGORY` 都能命中 |
| `result` | 可选，同上格式（SUCCESS/FAILED/UNKNOWN） |
| `keyword` | 可选，≤128 字符，匹配 `operation` 或 `request_uri` |

响应按 `id DESC` 排列，字段：`id`、`adminId`（管理员删除后为 null）、`adminUsername`（同名外连接，删除后为 null）、`module`、`operation`、`requestUri`、`requestMethod`、`ip`、`result`、`createdAt`。

## 数据来源与隐私

日志由 `AdminAuditService`（独立 `REQUIRES_NEW` 事务）写入，覆盖管理员登录/退出、工具、分类、推荐位、用户、公告与反馈的写操作；读取类接口不写审计。`request_uri` 只含路径，不含查询串、请求体或任何敏感值；`ip` 取 `X-Forwarded-For` 首值并按 45 字符截断。全部字段在写入时按列宽裁剪（`module` 32、`operation` 64、`request_uri` 255、`request_method` 10、`ip` 45、`result` 32）。

## 后台页面

`admin/src/views/OperationLogsView.vue`（导航“操作日志”）：模块/结果下拉、操作或路径关键字搜索、分页表格与结果标签；不提供编辑或删除操作。`admin/src/api/logs.ts` 严格校验响应结构。

## 未实现

导出 CSV、按管理员/时间区间筛选、保留期与归档策略、告警订阅、日志删除审批。当前表只增不删，生产环境应在部署任务中补充清理与备份策略。

## 验证

- Server 单元测试 88/88 通过（日志部分新增 3 项：过滤规范化与行映射、越界空页、非法过滤值）。
- `scripts/check_admin_logs.py` 在本地栈对临时管理员执行 13/13 项校验：匿名 401、创建临时分类产生审计、`module=CATEGORY` 最新行为 CREATE/POST/SUCCESS 且管理员名与请求路径正确、`module+result` 组合过滤、关键字过滤、越界页返回空但 total ≥1、非法 module 400/10001、`pageSize=0` 400/10001、删除分类后最新行为 DELETE；结束时删除临时分类与审计行。
- 同批次全部脚本回归通过：工具 20/20、分类 22/22、推荐位 26/26、用户 19/19、公告 25/25、反馈 17/17、管理员认证 20/20；`admin` 后台 `npm run typecheck && vite build` 成功。
- 本地库最终基线：20 个工具、13 个分类、0 推荐位、0 公告、0 反馈、0 临时账号、`admin_user` 为空；schema 31→32 迁移全部 success=1。
