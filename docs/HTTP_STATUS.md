# HTTP 状态

2026-10-05，http_status / NETWORK / LOCAL；Compose → HttpStatusViewModel → ProbeHttpStatusUseCase → OkHttpStatusRepository，V28 注册目录。仅新增显式 OkHttp 依赖声明（版本与项目既有 Retrofit 传递依赖一致），无服务端接口、数据库结构或权限变化。

## 功能与边界

- 输入完整 URL，选择 HEAD（默认，仅响应头）或 GET，可开关“自动跟随跳转（最多 5 次）”。结果显示状态码与状态文本、最终地址、HTTP 版本、总耗时、跳转链、TLS 版本与加密套件、Server、Content-Type、Content-Length 与有界响应头列表；整份复制/系统分享。
- 一次只允许一个请求，两次启动间隔 ≥1s；单跳连接 4s、读写 6s、call 10s，整次 30s 上限并支持取消。HEAD/GET 都不读取响应正文：GET 只取响应头后关闭，`Content-Length` 是目标声明值而非本工具下载量，已明确标注。
- 只发送用户主动发起的诊断请求；不携带 Cookie（`CookieJar.NO_COOKIES`）、不自动重试（`retryOnConnectionFailure(false)`）、不写入历史或日志、不记录原始异常文本。请求 UA 固定为 `CangshuoToolbox/0.1`。
- URL 校验：仅接受 `http://`/`https://` 绝对地址；拒绝空主机、登录凭据（`user:pass@`）、控制字符/空格、非法端口（0、非数字、>65535）、未加方括号的 IPv6、前导 0 或越界的 IPv4、空/超长 label；域名经 `IDN.toASCII(STD3)` 转 punycode（如 `中国.cn` → `xn--fiqs8s.cn`），fragment 丢弃，query 原样保留；非 ASCII 路径交由 OkHttp 规范化编码，路径中 `"<>\^`{|}[]` 等危险字符拒绝。
- 明文 HTTP 受构建的网络安全配置约束：应用默认 `cleartextTrafficPermitted=false`，仅 debug 覆盖允许 localhost/10.0.2.2/127.0.0.1；其它主机的 `http://` 在请求前检查 `NetworkSecurityPolicy` 并给出明确提示，不会发出明文请求。HTTPS 证书由系统校验，TLS 失败单独反馈。
- 跳转逐跳校验：`Location` 相对地址按 RFC 3986 解析后重新走同一 URL 校验与明文检查，任何一跳不合格立即失败；303 改成 GET，301/302/307/308 保持方法；超过 5 次或回环（含空 Location）报固定错误。304/300 等不视为跳转。
- 错误固定映射：非法地址、不支持协议、明文被阻止、繁忙/限频、超时、DNS、TLS、连接、跳转超限/回环、协议数据、不支持、未知失败；不显示异常堆栈、原始响应或敏感头部。取消立即中止 OkHttp 调用并结束加载，旧任务不会覆盖新结果。
- 响应头最多 60 条、单值 512 字符、合计 8192 字符，控制字符替换为空格，超长标记省略号；Content-Length 仅接受 1–18 位纯数字。

## 实际源码参考

2026-10-05 完整阅读 OkHttp 官方示例（tag `parent-4.12.0`，可变化引用）：[CancelCall.java](https://github.com/square/okhttp/blob/parent-4.12.0/samples/guide/src/main/java/okhttp3/recipes/CancelCall.java)（`Call.cancel()` 中止在途请求，blob 58a791adc729cdd720dcb8ddda1e10c749b082b2）、[AccessHeaders.java](https://github.com/square/okhttp/blob/parent-4.12.0/samples/guide/src/main/java/okhttp3/recipes/AccessHeaders.java)（`header()/headers()` 读取与关闭响应，blob 95fa49241cbb289def8fadedc63b7fb0c97141a9）、[ConfigureTimeouts.java](https://github.com/square/okhttp/blob/parent-4.12.0/samples/guide/src/main/java/okhttp3/recipes/ConfigureTimeouts.java)（connect/write/read/call 分层超时，blob 3c429bf23ba3c58c638ac7b645911dbbe117b001）。同时核对 Android [NetworkSecurityPolicy](https://developer.android.com/reference/android/security/NetworkSecurityPolicy) 明文放行查询（查询日期 2026-10-05）。

采用取消、头部读取与分层超时思路；本项目不采用其阻塞 `execute()` 与示例直连 URL，改为 `enqueue` + `suspendCancellableCoroutine` 桥接、手动逐跳跳转并在每跳重做校验、头部有界化、明文默认拒绝与固定错误文案。参考代码为 Apache-2.0 示例，未复制源码；OkHttp 依赖版本与工程既有 Retrofit 2.11.0 传递版本（3.14.9）一致，避免计划外升级。

## 验证

新增 4 项策略回归：IDN/IPv6/端口/fragment 规范化，凭据/协议/控制字符/畸形主机拒绝，相对与绝对跳转解析及方法/分类映射，响应头与 Content-Length 边界。新增 4 项策略回归；186/186 Android 测试通过，assembleDebug 与 lintDebug 成功（3 分 3 秒），Lint 0 错误/29 警告（含新增的 OkHttp 3.14.9 版本可用提示，显式声明与 Retrofit 传递版本一致、未做计划外升级）。Server 镜像成功（测试跳过），V28 success=1，8081/8088 total=20，http_status/NETWORK/LOCAL 无需登录。未对公网真实站点、证书异常、明文拦截与代理环境做真机验收，按用户要求留到发布验收。
