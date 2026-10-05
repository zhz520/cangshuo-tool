# 管理员认证

2026-10-05，Phase 5 第一项 Admin auth。管理员使用独立账号表与独立令牌（`kind=admin`），不复用普通用户账号或刷新会话；本项包含服务端登录、当前资料、退出与登录限流/审计，以及后台登录页、令牌存储、路由守卫和退出登录。

## 数据与迁移

V29 新增两张表：

- `admin_user`：`id`、唯一 `username`、`password_hash`（BCrypt cost 12）、`nickname`、`role_code`、`status`、`last_login_at`、时间戳；`username`/`role_code` 使用 ascii 排序规则，状态仅允许 0/1。
- `admin_operation_log`：`id`、可空 `admin_id`（外键 `ON DELETE SET NULL`）、`module`、`operation`、`request_uri`、`request_method`、`ip`、`result`、`created_at`；账号被删除后审计记录仍保留。

本地栈已应用 V29（schema 29），旧迁移文件未修改。

## 接口

| 方法与路径 | 认证 | 说明 |
| --- | --- | --- |
| `POST /api/v1/admin/auth/login` | 匿名 | 请求 `{ "username", "password" }`；成功返回 `AdminAuthResponse` |
| `GET /api/v1/admin/auth/me` | 管理员 Bearer JWT | 返回当前启用管理员的资料 |
| `POST /api/v1/admin/auth/logout` | 管理员 Bearer JWT | 写入退出审计并返回 `data: null`；令牌本身无状态 |

`AdminAuthResponse`：`accessToken`、`tokenType=Bearer`、`expiresIn`、`expiresAt`、`admin{id, username, nickname, roleCode}`。响应和日志不包含密码哈希、令牌或请求原文；`AdminLoginRequest`/`AdminAccount`/`AdminAuthResponse` 的 `toString()` 均标记为 `[redacted]`。

错误码：`20004` 管理员账号或密码错误（未知账号、密码错误、禁用账号使用同一响应）；`10007` 触发登录限流（HTTP 429）；参数错误 `400/10001`；缺失或无效令牌 `401/10002`；普通用户令牌访问管理端点 `403/10005`。

## 令牌与授权

- 与用户令牌共用 HMAC-SHA256 签名密钥、issuer 与 audience，但带 `kind=admin`、`role`、`username` 声明，且不携带 `sid`；TTL 来自 `ADMIN_TOKEN_TTL_SECONDS`（默认 3600 秒，允许 300–86400，越界启动失败）。
- 解码器按 `kind` 分流校验：用户令牌仍要求 `sid` 为 32 位十六进制且有效期 ≤901 秒；管理员令牌要求角色符合 `[A-Z][A-Z0-9_]{2,31}` 且有效期 ≤86401 秒，两类令牌都校验签名、issuer、audience、签发/过期时间。
- `CombinedJwtConverter` 把管理员令牌映射为 `ROLE_<role>`，用户令牌继续走会话校验；`/api/v1/admin/**`（除登录）要求 `ROLE_ADMIN` 或 `ROLE_SUPER_ADMIN`，用户令牌因此得到 403。
- 管理员令牌没有刷新令牌，也没有服务端撤销列表：退出登录由客户端立即清除令牌并写审计，令牌在 TTL 内仍然有效。需要即时撤销时应在后续任务引入服务端会话/黑名单。

## 登录限流

`AdminLoginGuard` 为进程内计数器：同一账号与同一来源地址各累计 5 次失败后锁定 15 分钟，锁定期间在查询账号前直接返回 `429/10007`；任一次成功登录清除两类计数；表大小超过 512 时清理过期条目。地址取 `X-Forwarded-For` 首值（Nginx 以 `$remote_addr` 写入）。重启进程即清空计数；多实例部署需要改用 Redis 共享计数，尚未实现。

## 审计与事务边界

登录成功/失败与退出写入 `admin_operation_log`，结果取 `SUCCESS`、`FAILED`（未知账号时 `admin_id` 为空）或 `UNKNOWN`；`ip`、`uri`、`method` 按列宽截断，控制字符不会进入日志。

审计通过 `AdminAuditService` 的 `REQUIRES_NEW` 独立事务提交，因此失败登录在外层回滚时仍会留痕。`AdminAuthService.login` 本身故意不加 `@Transactional`：实测发现若外层事务先执行 `UPDATE admin_user`……行锁，独立审计事务插入带外键的审计行时需要对同一父行加共享锁，两个连接互等到 JDBC socket 超时（10 秒）并让登录返回 500。登录流程的写入都是单条语句，各自原子提交即可。

## 首个管理员引导

服务启动时若 `ADMIN_BOOTSTRAP_USERNAME` 与 `ADMIN_BOOTSTRAP_PASSWORD` 都为空则跳过；只设置其一会启动失败；两值都存在但 `admin_user` 已有任意记录时跳过，避免通过环境变量重复注入特权账号。账号规则与登录一致（3–32 位、小写字母/数字/. _ -），密码要求 12–72 字节且至少包含一个字母和一个数字。密码使用 BCrypt(cost 12) 存储，日志只记录“已创建/已跳过”，不记录账号口令。变量放在私有 `.env` 或部署密钥管理，见 [部署说明](../deploy/README.md)；首次创建后可从环境文件移除。

## 后台前端

- `admin/src/views/LoginView.vue`：独立登录页，账号/密码前端预校验（≥3 / ≥8 位）、`20004` 与 `10007` 的本地化提示、提交中的 loading 状态和 redirect 参数回跳。
- `admin/src/api/token.ts` 把管理员令牌保存在 `localStorage` 的 `cangshuo.admin.token`；`admin/src/api/client.ts` 请求拦截器统一附加 `Authorization`。密码不写入浏览器存储。
- `admin/src/stores/auth.ts` 负责登录、`ensureProfile`、退出与本地清理；`admin/src/router/index.ts` 的全局守卫在无令牌时跳转登录页，令牌存在但资料缺失时先请求 `me`，401 时清除令牌；已登录访问登录页会回到仪表盘。
- `AdminLayout.vue` 顶栏显示昵称与角色标签，菜单提供退出登录（调用审计接口后本地清理并回到登录页）。

## 未实现与后续

修改密码/找回密码、管理员列表与角色管理界面、服务端令牌撤销、Redis 共享限流、操作日志查询界面（属于路线图 Operation logs）、高风险操作二次确认。后台 Dashboard 目前仍是健康概览，管理员登录后暂不展示统计。

## 验证

- Server 单元测试 51/51 通过（新增 11 项）：管理员令牌往返/声明绑定/畸形声明拒绝、TTL 边界、限流锁定与解锁、登录成功/失败/禁用/未知账号、计数器清除、`current`/`logout` 与脱敏。
- `scripts/check_admin_api.py` 在本地栈对临时引导管理员执行 20/20 项校验：匿名 401、参数 400/10001、未知管理员 401/20004、用户令牌访问管理端点 403、登录 200 与资料不泄漏、`me` 200、退出 200、审计 SUCCESS/FAILED 行存在、连续 5 次失败后第 6 次返回 429/10007；脚本默认只跑负路径，正向路径需要 `ADMIN_CHECK_USERNAME/ADMIN_CHECK_PASSWORD`。
- 管理后台 `npm run typecheck && vite build` 成功（产物含 `LoginView`）；浏览器（应用内浏览器）验证了未登录访问 `/admin/dashboard` 会跳转到 `/admin/login?redirect=/dashboard`，登录页在 `http://localhost:8088/admin/login` 正常渲染；浏览器自动化当前没有输入 API，因此表单提交、退出按钮和角色标签未在真实浏览器点选验证，凭据流程由上述 API 校验覆盖。
- 验证使用临时账号 `verify-admin`，完成后已删除；`admin_user` 当前为空，审计行保留（`admin_id` 已按外键规则置空）。验证期间的一次 500（事务/外键锁）已在修复后重跑通过。
