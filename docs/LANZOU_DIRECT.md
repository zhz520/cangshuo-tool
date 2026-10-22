# 蓝奏直链解析

`lanzou_direct`，NETWORK / WEB，排序 631，V92 目录迁移。官网页面为 `/tools/lanzou_direct/`，App 使用系统 WebView 打开；服务端负责受限抓取、解析、短时缓存和请求合并，网页负责输入校验、状态反馈与复制。服务端不代理文件流。

## 开源参考（2026-10-22）

| 来源 | 采用内容 | 适配差异 |
| --- | --- | --- |
| MIT [hanximeng/LanzouAPI](https://github.com/hanximeng/LanzouAPI/blob/eb2d2eb25233d7e44645162e4f817abc0ec87d94/index.php)，固定 `eb2d2eb25233d7e44645162e4f817abc0ec87d94`，同时阅读 [LICENSE](https://github.com/hanximeng/LanzouAPI/blob/eb2d2eb25233d7e44645162e4f817abc0ec87d94/LICENSE) | 文件页/密码页分支、`sign`/`ajaxdata`/`wp_sign`、`downprocess` 字段、跨域 `ajaxfile.php`、真实时长的直链跳转、旧/新文件名与大小规则，以及确定性 `acw_sc__v2` 下载握手 | Java 独立实现；不发送随机 `X-Forwarded-For`/`CLIENT-IP`，不关闭 TLS 校验，不提供文件代理或 302 下载端点。请求正文有大小上限，重定向和 DNS 有边界，缓存键不保存密码。 |
| MIT [WhY15w/lanzou](https://github.com/WhY15w/lanzou/tree/437b7d0389e9900e38770991da2d5506138f64fe)，固定 `437b7d0389e9900e38770991da2d5506138f64fe`，阅读 `lanzouParser.ts`、`lanzouHttpClient.ts` 与 `anti_acw_sc__v2.ts` | 新版把 Ajax 地址/Sign 放进变量、绝对地址与相对地址并存、跳转页只取 302 `Location`、遇到确定性 `acw_sc__v2` 后携带同站点 Cookie 重试一次 | 未引入 Node/Cheerio/Axios；改为 Apache HttpClient5 + Jackson。去掉随机出口 IP、公共 CORS、用户后缀改名和无限站点轮换。若第二次仍是挑战页，则明确返回官方页。 |
| MIT [cotzhang/Lanzou-Worker-Parser](https://github.com/cotzhang/Lanzou-Worker-Parser/blob/a009311702f16bf588e2264eb0f4abf13d8968ae/worker.js)，固定 `a009311702f16bf588e2264eb0f4abf13d8968ae` | 最终跳转不跟随文件本体、`pid` 清理、错误分类和页脚字段交叉核对的思路 | 没有照搬 Cloudflare Worker 随机来源头；服务器保持固定出口身份，无法解析时回退官方下载页。 |

本次实际查看的是上述固定提交源码与许可证；参考项目不作为运行时依赖。算法署名与 MIT 文本同时保存在官网 `deploy/site/tools/lanzou_direct/NOTICES.txt`。

## 功能范围

- 输入单个蓝奏公开分享链接或整段分享文案；支持当前 `lanzou[a-z]?.com` 官方域名家族、HTTP 输入升级为 HTTPS、`pwd/password/code` 查询密码、文案中的“分享密码/提取码/访问码”。
- 普通文件页支持新旧文件名、大小、iframe 详情页、内联或变量 `sign`、`wp_sign`、`ajaxdata`、`ajaxm.php`/`ajaxfile.php`、相对或绝对 Ajax 地址。
- 结果区分 `DIRECT`（临时直链）和 `OFFICIAL_PAGE`（官方回退页）。两者都显示真实文件名/大小；直链可复制或在新窗口打开。
- 可为已验证分享创建稳定下载跳转地址。地址路径中是 AES-GCM 加密令牌，包含分享 URL 与可选密码；每次访问按当前规则重新解析，并用 HTTP 302 自动跳转到最新临时直链或官方页面。
- 密码页必须提供密码；错误、失效、暂停分享、文件夹、上游限流、超时和无法识别的改版分别给出固定业务码，不向页面回显第三方异常文本。
- 文件夹分享、批量链接、登录后私有分享、文件代理下载、断点续传、自动重试和跨应用后台任务不在本工具范围。用户需要文件夹时进入官方页处理。

## 永久跳转

- 稳定路径固定为 `/api/v1/web-tools/lanzou_direct/download/<token>`；`<token>` 是版本化 AES-GCM 密文，密钥来自 `LANZOU_LINK_SECRET`，不会以明文保存密码。相同稳定地址可重复访问；真正会过期的是每次换得的蓝奏/CDN 临时 URL。
- 创建前会实际解析并验证密码、文件状态和官方限制。无密钥、令牌篡改、密码或分享失效时明确失败；文件仍在但临时 URL 失效时，下次访问重新解析。
- 令牌是持有即可使用的能力链接，应像私密分享地址一样保管。当前没有逐条撤销 UI；轮换 `LANZOU_LINK_SECRET` 会使全部旧稳定地址失效。文件被删除/取消分享时稳定地址也会失败，无法让第三方文件永久存在。
- 稳定跳转每来源每分钟最多访问 30 次；结果仍使用三分钟解析缓存。响应为标准 302，包含 `Cache-Control: no-store`、`Referrer-Policy: no-referrer`、`X-Content-Type-Options: nosniff`。令牌只放在路径中，容器 Nginx 对该前缀关闭 access log；Baota 外层站点也必须在发布时关闭该前缀的访问日志。

## 请求与解析边界

- 输入文本 ≤4096 字符、密码 ≤64 字符；密码中的控制字符拒绝。文本有多个链接时拒绝，避免误解目标。
- 只抓取 Lanzou 官方页面域名；当前下载页基础域接受 `lanzou[a-z]?.com`、`dmpdmp.com`、`lanrar.com`、`woozooo.com`、`baidupan.com` 的受控子域。响应给出的最终直链只接受 HTTP(S)、无用户信息/片段、非 IP 字面量、端口 80/443，并移除 `pid` 参数。
- HTTP 连接使用自定义 DNS 解析器，拒绝回环、私网、链路本地、文档/测试网段和 IPv6 转换地址，连接复用同一批已验证 IP，避免二次 DNS 漂移。禁止自动重定向与自动重试，最多 4 次官方重定向。
- 页面正文 ≤2 MiB、挑战页探针 ≤128 KiB；连接 4 秒、响应 8 秒、总解析期限 25 秒。Ajax 正文同样有上限。
- `acw_sc__v2` 只在响应出现 40 位 `arg1` 时计算一次 Cookie 并用同一 URL、请求方法、表单、UA、Referer 重试一次。它是公开文件的下载握手，不是验证码识别；若最终直链探针超时、被限流或仍返回交互式验证页，结果回退已确认的官方下载页面，不伪造成功。
- 文件大小、文件名和跳转页来自响应本身；第三方标识不写日志。原始密码不写日志、不进缓存键、不写数据库、不进入 URL。缓存键为分享 URL 与密码的 SHA-256。

## 防封和风控策略

- 同一链接/密码结果缓存 180 秒，最多 100 条；同键并发请求合并，最多 2 个上游解析；每个访问来源 8 次/分钟。
- 上游 403/429 或超时触发该主机 60 秒冷却，期间后续解析优先等待而不继续轰击；失败不写结果缓存。
- 页面和探针使用固定 UA 与 Referring 信息，保持连接身份一致；不使用随机来源 IP、代理池、验证码打码或账号轮换。
- 这是降低重复访问和误操作的策略，不承诺绕过服务端风控或永久避免封禁；临时直链也会过期或按网络敏感。

## API

- `GET /api/v1/web-tools/lanzou_direct/capabilities`：匿名，返回 `available,maxTextLength,maxPasswordLength,cacheSeconds,requestsPerMinute,maxConcurrent,verificationMode,permanentLinksAvailable`。
- `POST /api/v1/web-tools/lanzou_direct/parse`：匿名 JSON `{text,password?}`。成功 data 含 `shareUrl,fileName,fileSize,state,directUrl,downloadPageUrl,verificationRequired,rateLimited,cached,resolvedAt,expiresInSeconds`。
- `POST /api/v1/web-tools/lanzou_direct/permanent-links`：匿名 JSON `{text,password?}`；验证后返回 `{path,state,fileName,fileSize,verificationRequired,rateLimited,createdAt}`。不返回明文密码或临时直链。
- `GET /api/v1/web-tools/lanzou_direct/download/{token}`：解密并实时解析，成功时 302 到临时直链或官方回退页，不返回 JSON 包装；非法/被篡改令牌为 62001。
- 错误码：62001 链接无效、62002 需要密码、62003 密码错误、62004 文件不可用、62006 上游限制、62007 上游异常、62008 超时、62009 文件夹不支持；通用限流仍为 10007。62005 保留作为客户端兼容映射，正常解析遇到无法解除的官方验证时改为 HTTP 200/`OFFICIAL_PAGE`，而不是假装直链成功。
- 所有响应 `no-store`；请求体由共享过滤器限制 8192 字节。接口契约同步维护到 Apifox。

## 网页版与 Android

- `/tools/lanzou_direct/` 使用本地 CSS/JS，无外部 CDN 或内嵌第三方框架；CSP 禁止 frame、object 和外部连接。
- 中英双语；包含初始加载、离线/HTTPS/服务不可用、输入错误、提交中/停止等待、复制成功/失败、缓存命中、限速等待、直链/官方页结果和永久跳转的生成/不可用/复制/打开状态。
- 输入和密码不进入 localStorage/sessionStorage；离开页面清空密码。页面离开后可 abort 当前请求。
- Android 注册 `LanzouDirectToolDefinition`，Web 模式、NETWORK 分类、无设备权限。网页向系统浏览器交出官方页或临时直链前，只允许 HTTP(S)、公开域名、无用户信息/片段/控制字符、默认端口。

## 验证记录

2026-10-22 开发完成、生产验收前验证：

- 服务端完整 **248/248** 通过；蓝奏专项 **20/20**，覆盖官方域/伪装域、私网 DNS、控制字符、双链接、密码、新旧 Ajax/Sign、确定性挑战与残留挑战、失效/文件夹、不信任 Ajax 端点、缓存、限流、主机冷却和控制器边界。
- 静态契约与网页 Engine 覆盖链接/密码提取、签名查询保留、错误分类、结果状态、永久跳转、CSP、MIT 署名、Android 注册和文档一致性；永久跳转发布后 OpenAPI 快照为 **78 操作 / 64 路径 / 131 schema**。
- Android Debug/Release 各 **903/903**（130 suites），双构建与 Lint 通过，均 **0 错误 / 86 条既有警告**；Debug APK 26,501,589 字节、Release APK 17,736,597 字节、AAB 17,102,805 字节。HTTPS/权限/备份/原生库/ZIP 对齐门禁通过，`signatureVerified=false`（未签名 Release）。
- 实网只读读取三个公开分享，均成功识别文件名和大小并获得临时直链：两个普通分享分别来自 `www.lanzouf.com`（下载页 `dmpdmp.com`）和 `www.lanzoux.com`（下载页 `lanrar.com`），一个带密码分享来自 `wwd.lanzouf.com`（Ajax `apifile.woozooo.com`，最终文件域 `webgetstore.com`）。未打开或下载文件本体；未执行压力测试。
- Chrome 桌面及 390px 移动视口实际检查：中文/英文切换、默认/加载/错误/结果/复制/打开/清空路径已接线，隐藏按钮不占位，移动视口 `scrollWidth == clientWidth`，无横向溢出。页面使用临时本地 API 样例验证布局，图标与脚本均来自本地。
- 严格隔离候选 **224/224** 通过：V92、NETWORK/WEB 元数据、后台编辑、种子重放保留编辑与用量、热门排行、7/30/90 天统计、目录非缓存与蓝奏 API `no-store`；临时容器、数据库、用户、网络与 Redis 已清理。最终候选 API `sha256:4d0a168ae95cc80a9e9d13b3e786624674ed2a14ad309844d361283a540ee192`。
- 候选官网镜像 `sha256:ef23ebc71ece9f18b461b467c6c39ebd4948109ea0f203b806db55ef915b5edc` 在独立内网容器中实际返回 index/engine/app/CSS/NOTICES 200，CSP 保持 `frame-src 'none'`；临时容器已删除。
- 真机 WebView、Android 外部浏览器交接、网络切换、复制权限和所有 Lanzou 域名轮换仍需持续抽查。

## 生产发布（2026-10-22）

候选分支已快进合并到 `main` 并部署。最终代码提交 `2765bd048fe74db09245a89907417103ef1cc160`；生产 API 镜像 `sha256:4d0a168ae95cc80a9e9d13b3e786624674ed2a14ad309844d361283a540ee192`，官网镜像 `sha256:ef23ebc71ece9f18b461b467c6c39ebd4948109ea0f203b806db55ef915b5edc`。Flyway **92/92、失败0**，目录 `lanzou_direct` 恰1条，官网/工具页/后台登录/健康接口均200，五容器 healthy。

只读 HTTPS 验收 **111/111** 通过；生产实链普通分享和带密码分享均返回 `DIRECT`，同链接第二次返回 `cached=true`。被取消/失效/需人工验证时仍按固定错误码或 `OFFICIAL_PAGE` 回退。

回滚点：Git/Docker `before-lanzou-v92` 保留 V91 代码 `76a7481a6a448a72f31cbf8a4415c20a25455a90`、API `sha256:e87c639060277771bccf8a2e1eb2fa5af42947a444d30696ab523ed85bd3deaf` 和官网 `sha256:fe1587514202f23bbabbc7c178d4362337936f127a2ce853e803e49cb51d3773`；`before-lanzou-hotfix-v92` 保留上一版 V92 代码 `a3bd1facddea165cb12160cd1017203463932108` 与 API `sha256:abe721525c5dcf3e823abdf7498f0738c5767f6fa6e3687cba19c929b3380976`。网页镜像在两次 V92 版本间未变化。

Apifox 项目 8952528 在合并后显示 `Paths ( 62 )` 和“同步成功”；规范包含本次两个匿名接口。剩余4条均为可选元数据/排序提示（许可证对象与URL、公开联系邮箱、tags字母序），0条错误，没有为了样式提示擅自授予许可证或公开邮箱。
