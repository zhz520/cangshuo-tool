# 接口限流（Rate limits）

2026-10-05，Phase 6 Rate limits，Phase 6 最后一项。所有 `/api/v1` 请求按“身份”做固定窗口计数：已登录请求按 JWT `sub`，匿名请求按 `X-Forwarded-For` 首值或远端地址；登录/注册/刷新使用更严格的独立桶。超限返回统一 429/10007 与 `Retry-After`。

## 队列与响应头

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `RATE_LIMIT_ENABLED` | `true` | 关闭后过滤器不注册 |
| `RATE_LIMIT_WINDOW_SECONDS` | `60` | 固定窗口长度（1–3600） |
| `RATE_LIMIT_MAX_ANONYMOUS` | `600` | 每窗口匿名请求上限 |
| `RATE_LIMIT_MAX_AUTHENTICATED` | `3000` | 每窗口已登录请求上限 |
| `RATE_LIMIT_MAX_AUTH_ATTEMPTS` | `30` | `/auth/login`、`/auth/register`、`/auth/refresh` 每窗口上限，且不得大于匿名上限 |

每次响应都会带 `X-RateLimit-Limit` 与 `X-RateLimit-Remaining`；被限流时额外返回 `Retry-After`（秒）与统一错误体：

```json
{ "code": 10007, "message": "Too many requests", "data": null, "traceId": "…" }
```

`/api/v1/health`、`/actuator/**`、HTTP `OPTIONS` 预检与非 `/api/v1` 路径不计数，保证编排健康检查与 CORS 预检不受影响。

## 计数器实现

- 优先使用 Redis（`toolbox:rate:*` / `toolbox:rate:auth:*`，`INCR` + 首次写入设置 `EXPIRE`），多实例共享同一窗口。
- Redis 抛错或不可用时自动退化为进程内固定窗口计数，并每 60 秒最多记录一条 WARN；退化期间限流仍生效，不会因为缓存故障阻断 API。
- 固定窗口存在边界效应（窗口切换瞬间可能放行两倍请求），本项接受该折衷；滑动窗口/令牌桶属于后续增强。
- 管理员登录另有 `AdminLoginGuard` 的 5 次失败锁定（见 [管理员认证](ADMIN.md)），与这里的接口级限流叠加。

## 验证

- Server 单元测试 116/116 通过（限流部分新增 9 项：配置默认值/非法组合、内存窗口计数与重开、Redis 计数与 TTL 决定 `Retry-After`、Redis 故障回退、过滤器跳过健康/预检/非 API、429 统一响应体与头部、JWT 身份与匿名身份分桶）。
- `scripts/check_rate_limit.py` 自动读取响应头中的上限并耗尽匿名与登录两个桶：断言剩余数递减、429/10007、`Retry-After`、限流期间 `/api/v1/health` 仍 200、登录桶上限严格小于匿名桶；脚本最后运行，避免影响同批次其它校验。
- 本地校验通过后服务端恢复默认配置；多实例/Redis 故障的真实网络场景留待发布验收。
