# 邮箱注册与登录

2026-10-05，Phase 3 的首项 Register/login。Android「我的」页提供注册、登录、当前账号和退出操作；收藏、最近使用、扫码历史继续保存在本机。

## 实现与边界

- Server：Controller → AuthService → UserAccountRepository，参数化 JDBC；V18 新增 `sys_user`，邮箱唯一索引处理并发注册。
- Android：Compose → AuthViewModel → AuthUseCases → RemoteAuthRepository → Retrofit/Moshi。登录成功再请求 `GET /auth/me`，确认 Bearer 可用且账号有效才发布状态。
- 工具打开流程实时读取同一账号状态，`requiresLogin` 在已登录时放行、退出/过期后恢复登录门槛；仍按既有设备/权限/目录状态校验。
- 邮箱：ASCII 邮箱，去除首尾空白，Locale.ROOT 转小写，全地址大小写不敏感，最多 128 字符；不支持国际化邮箱、无点域名、连续点本地部分。昵称去除首尾空白，1–64 个 UTF-16 字符。
- 密码：至少 8 个 UTF-16 字符，最多 72 个 UTF-8 字节，拒绝控制字符和未配对代理字符；不 trim、不改写。BCrypt cost=12、随机盐，检查字节上限避免截断。
- 注册 HTTP 201；重复邮箱 HTTP 409/20002；未知邮箱、错误密码、禁用账号均 HTTP 401/20001。响应沿用 traceId 和统一封装。敏感 DTO/账号/UI 状态的 `toString` 隐去原文，无请求日志或网络日志拦截器。
- JWT：Spring Security/Nimbus HS256；固定 issuer/audience，sub 仅用户 ID，15 分钟有效，含 iat/nbf/exp/jti；验证签名、算法、issuer、audience、过期、签发时间和生命周期。JWT 不携带邮箱/昵称/密码。精确 POST register/login/refresh/logout 匿名开放；refresh/logout 使用请求体中的刷新凭证。
- 签名密钥 `JWT_SECRET` 为服务端私有环境中的随机 32 字节十六进制值，缺失/格式错误启动失败。初始化脚本自动生成，Compose 显式传入；客户端无签名密钥。
- 持久登录已接入：30 天绝对期限的 Refresh Session，令牌轮换，旧令牌重放撤销整会话；JWT 带 sid，每次 Bearer 校验当前会话及用户状态，退出后旧 Access Token 立即失效。Android Keystore AES-256/GCM 加密 Refresh Token 到 noBackupFilesDir 的 DataStore，Access Token 仅驻留内存；恢复时先刷新并请求 me，离线保留重试入口。离线退出立即清身份，保存待撤销标记，下次联网只撤销而不恢复。设备列表、邮箱验证、密码重置按后续任务实现。
- 请求不自动重试、不跟随重定向，连接 8 秒、读 10 秒、写 8 秒、总请求 15 秒，响应最多 16 KiB（固定长度/chunked 均受限）；互斥避免重复请求，取消不会发布结果。正式 API 用 HTTPS，debug HTTP 限 loopback 白名单。
- 表单可滚动并避让键盘，中英资源同步、共享加载组件和主题卡片；密码不写 SavedStateHandle/持久层，页面离开和后台时清除，忙碌时禁用重复操作。

## 源码与资料参考

查阅日期：2026-10-05。6.5.x 为可变化分支；没有复制第三方业务源码。

| 来源 | 阅读与采用 | 本项目适配 |
| --- | --- | --- |
| [BCryptPasswordEncoder 源码](https://github.com/spring-projects/spring-security/blob/6.5.x/crypto/src/main/java/org/springframework/security/crypto/bcrypt/BCryptPasswordEncoder.java) | encode 的随机盐/strength、matches 格式检查 | cost=12，UTF-8 72 字节上限，未知用户 dummy hash 验证 |
| [NimbusJwtEncoder 源码](https://github.com/spring-projects/spring-security/blob/6.5.x/oauth2/oauth2-jose/src/main/java/org/springframework/security/oauth2/jwt/NimbusJwtEncoder.java) | JWKSource、JWS header、claims 编码 | 仅服务端对称密钥，显式 HS256 |
| [NimbusJwtDecoder 源码](https://github.com/spring-projects/spring-security/blob/6.5.x/oauth2/oauth2-jose/src/main/java/org/springframework/security/oauth2/jwt/NimbusJwtDecoder.java) | 签名解析与 validator 链 | 同一服务端 key，增加 audience/sub/必填时间/15 分钟上限 |
| [Spring JWT 官方指南](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) | Bearer 过滤器、异常入口、issuer/audience/time 验证 | 复用统一错误，无 Cookie session |
| [Android Keystore 指南](https://developer.android.com/privacy-and-security/keystore) | 持久凭证与设备 key 绑定 | AES/GCM + Android Keystore，DataStore 的文件在 noBackupFilesDir，API 来源作为 AAD |

## 实际验证

- Server Maven package 含测试：25/25，0 失败/错误/跳过。覆盖输入边界、哈希、重复注册、错误/禁用账号、JWT 签名/过期/issuer/audience/必填 exp、SecurityFilterChain + MockMvc 匿名边界与错误封装。
- Docker 镜像重建和本地真实 MySQL/HTTP/Nginx：`scripts/check_auth_api.py` 17/17；注册→登录→me、代理、仅保存哈希、禁用拒绝、目录 12 条及 Flyway 18 条成功。随机测试账号在 finally 删除，不输出凭据。
- Android 150/150 自动测试通过，0 失败/错误；其中新增 6 个输入/UseCase 回归及 3 个真实 loopback HTTP 的登录→me→退出、HTTP 401、chunked 响应预算和过期响应检查。离线 test/assemble/lint 完整门禁成功（1 分 29 秒），新增 HTTP 测试后增量 test/lint 成功（36 秒）；Lint 0 错误/22 条既有警告，新 auth 源码无 Lint 问题。APK 在 `android/app/build/outputs/apk/debug/app-debug.apk`。
- 用户要求跳过手机/模拟器验收；设备输入、网络切换、完整无障碍行为未验证。
- 工具登录门槛接入后最终增量 test/assemble/lint 成功（1 分 25 秒），仍 150 项全部通过、Lint 0 错误/22 条既有警告；actionlint 1.7.12（校验官方 SHA-256）、初始化 Shell 语法、本地/正式 Compose config 和 git diff --check 通过。CI 已新增 Server 测试及 Surefire 产物，本轮未提交/推送或运行托管 CI。

## Refresh Token（2026-10-05）

V19 新增会话和令牌哈希表。随机会话选择器 128 bit + 256 bit 随机凭证；数据库仅保存 SHA-256 和消费时间。refresh 在行锁事务中消费旧凭证并生成新凭证，30 天期限不顺延；旧凭证重放撤销该会话，其他会话不受影响，猜错凭证不撤销。logout 幂等，可用本会话已消费凭证；到期记录每小时批量清理 1,000 个，令牌通过外键级联清理。

参考实际 [Spring Authorization Server 刷新 Provider 源码](https://github.com/spring-projects/spring-authorization-server/blob/main/oauth2-authorization-server/src/main/java/org/springframework/security/oauth2/server/authorization/authentication/OAuth2RefreshTokenAuthenticationProvider.java) 的 active 校验与轮换策略；适配为本服务 JDBC 行锁、单会话重放撤销和已消费哈希记录。采用 [DataStore 1.2.1 官方文档](https://developer.android.com/jetpack/androidx/releases/datastore) 与 Keystore 指南，不引入 OAuth 客户端协议。

Server 31/31 测试通过；实际 MySQL/API/Nginx 30/30 检查通过（含轮换、期限不变、旧令牌重放、会话隔离、退出幂等与 Bearer 即时失效），随机账号及会话已级联删除。Android 156/156 测试通过，新增 6 项进程恢复、过期、离线恢复/退出、轮换先落盘回归；test/assemble/lint 通过，0 错误/22 既有警告。按用户要求未做真机 Keystore、文件损坏、设备重启验收，不宣称这些设备场景已通过。

## Profile（2026-10-05）

`GET /auth/me` 读取邮箱/昵称，`PUT /auth/me` 保存当前账号昵称（1–64 字符，trim，拒绝控制字符/非法 Unicode）。身份只从 Bearer sub 取，不接受目标用户 ID；禁用或删除账号不能修改。Android「我的」页可编辑并保存，等待服务端确认才显示已保存，失败保留输入供重试。邮箱更改、头像上传需要验证码/对象存储等后续任务，当前不提供虚假上传操作。复用已有 AuthInput、AuthService、Repository，未新增数据库字段。

Profile 验证：Server 34/34、Android 157/157 测试通过，实际 API/数据库/代理 35/35；昵称保存与读取一致，匿名、控制字符与伪造目标 ID 已覆盖。Android test/assemble/lint 通过（1 分 54 秒），设备验收按用户要求省略。

Phase 3 最终：Server 40/40、Android 170/170、真实认证 API 35/35 和同步 API 37/37，通过构建/Lint。续期同账号时保持仍有效的旧身份，me=401 立即清账号，新增 2 个回归防止刷新取消自身同步；没有追加真机验证。
