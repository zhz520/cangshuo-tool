# 短视频解析（网页版 · 服务端解析）

代码 `short_video_parse`，分类 NETWORK，模式 WEB，排序 630，地址 `/tools/short_video_parse/`。V54 只登记目录元数据，不建业务表。App 复用远程 WEB 目录合并、来源白名单与 WebView；解析、资源代理和统计都在服务端完成，因此不新增 Android 工具页面或本地依赖。

## 源码查阅与适配

实际查阅日期：2026-10-13。第三方页面结构与接口字段会变化，平台改版后需重新验证。

| 实际阅读的源码 | 采用的思路与差异 |
| --- | --- |
| [wujunwei928/parse-video-py](https://github.com/wujunwei928/parse-video-py)（MIT），固定提交 `5fcf87256edb5ffcdebf0e4aac2a5a41745da76e`，阅读 `src/parse_video_py/parser/` 的 `base.py`、`__init__.py`、`douyin.py`、`kuaishou.py`、`redbook.py`、`weibo.py`、`bilibili.py`、`pipixia.py`、`xigua.py` 与 `pyproject.toml` | 借用平台清单、分享页数据来源（抖音 `_ROUTER_DATA` 与 slidesinfo、快手 `INIT_STATE`、小红书 `__INITIAL_STATE__`、微博 h5 component 接口、B 站 view/playurl、西瓜与皮皮虾的公开结构），以及优先原始播放地址、封面单独返回的组织方式。服务端用 Java 独立重写，未引入 Python 运行时；不复制其 CLI/Web 服务、代理配置与批量功能，并主动去掉无边界重试。 |
| [yt-dlp/yt-dlp 的 tiktok.py](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/tiktok.py) 与 [xiaohongshu.py](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/xiaohongshu.py)（master 分支，可变链接） | 查阅 TikTok `__UNIVERSAL_DATA_FOR_REHYDRATION__`/`SIGI_STATE` 路径、图集 `imagePost` 结构，以及小红书笔记页 `__INITIAL_STATE__`/`noteDetailMap` 与公开测试 URL 形态。未嵌入或执行 yt-dlp 代码，TikTok 处理为独立实现。 |
| [Apache HttpComponents Client 5.5.2](https://hc.apache.org/httpcomponents-client-5.5.x/)（Apache-2.0） | 有界 HTTP 客户端：显式连接/读取超时、连接池上限、禁自动重试、禁用 Cookie 容器、自定义 DNS 解析器校验公网地址。 |

## 功能与边界

- 支持平台（Host 精确匹配，识别分享链接或包含唯一链接的整段分享文案）：抖音、快手、小红书、哔哩哔哩（含 b23.tv）、微博（含 t.cn、h5.video.weibo.com）、西瓜视频、皮皮虾、TikTok。
- 视频：返回平台提供的播放资源，抖音把 `/playwm/` 换成 `/play/` 指向无水印播放路径（只替换该固定路径段，不改写签名参数）；返回多个码率时按码率降序排列并可切换。B 站仅接受 `durl` 单文件，遇到 DASH/分段或 `m3u8`/`mpd` 返回 61007。
- 图集：抖音/快手/小红书/微博/皮皮虾/TikTok 图文作品逐张返回原图；小红书 `notes_pre_post` 图片改写为 `ci.xiaohongshu.com` 原图；抖音与小红书的实况照片（Live Photo）同时返回配对的 `LIVE` 视频，可与图片一起预览、单独保存。
- 封面：每个作品返回一张 `COVER`。封面只作为附带资源，作品没有任何可下载的视频/图片时返回 61002，不把只有封面的结果当成解析成功。
- 结果字段：作品标题/描述/作者/时长、平台、原始链接，每个资源含序号、类型、尺寸、码率、来源标记（`PLATFORM_SOURCE`/`UNKNOWN`）、受控预览路径、下载路径与平台直链（供复制）。
- 不支持范围：私密、付费、直播、需要登录或触发平台验证的作品；DASH 音视频合并、分段下载与转码；已烧进画面的水印移除；验证码或风控绕过；主页批量解析、作品列表、下载历史与断点续传。
- 资源获取一律经服务端同源代理：`/api/v1/web-tools/short_video_parse/assets/{id}/{index}`（`?download=true` 为附件）、`/albums/{id}?selection=...`（ZIP），上游地址由服务端在平台 CDN 白名单内解析，客户端不能提交任意 URL，因此本工具不是开放代理。

## 资源、并发与限额

- 请求：分享文案 ≤ 4096 字符；JSON 正文 ≤ 16384 字节（超出 413/40001）；单条链接，出现多条链接或凭据、非 443 端口、非白名单主机时 400/10001 或 61001。
- 解析结果：最多 120 个资源、50 张图片，超过返回 61005；结果仅保存在服务进程内存 15 分钟，最多 100 个会话，重启即失效（61004 提示重新解析）。
- 传输：单文件 ≤ 200 MB，ZIP ≤ 32 MB；ZIP 只打包所选静态图片（≤ 50 张），按原顺序命名为 `001.jpg` 等；实况视频不进 ZIP。
- 并发：解析 3、资源传输 8、打包 1；IP 维度限流解析 12 次/分钟、打包 3 次/分钟（Redis 计数，Redis 故障回退内存限流）。
- 超时：解析总预算 30 秒、下载 150 秒、打包 45 秒；上游连接 5 秒内建立、读取 6 秒无数据即超时；异步响应请求上限 180 秒。

## 安全与隐私

- 只接受 HTTPS、无用户名密码、端口 443（或未指定）的链接；Host 做标签边界匹配，`v.douyin.com.evil.example` 这类后缀伪装被拒绝；页面与媒体分别有独立白名单。
- 连接使用自定义 DNS 解析器：解析结果包含环回、私网、链路本地、保留、文档、过渡地址时直接拒绝，避免 DNS 重绑定到内网。禁止 `m3u8`/`mpd`；上游重定向最多 5 跳且每跳仍必须命中白名单；不携带任何凭据，无 Cookie 容器，跨主机重定向不沿用会话。
- 上游响应上限：页面 6 MB；未知 `Content-Type` 时按魔数嗅探 JPEG/PNG/GIF/WebP/MP4/MOV，识别失败即 61007，绝不把 HTML/JSON 错误页当媒体保存。
- 上游正文、链接原文与请求参数不写日志、不写数据库；错误只返回固定业务码与 traceId。响应统一 `Cache-Control: no-store`、`X-Content-Type-Options: nosniff`、`Referrer-Policy: no-referrer`。
- 交付给客户端的下载文件名由服务端生成（平台_类型_序号.扩展名），`Content-Disposition: attachment`；不采用上游文件名。

## 数据与运维

- 无数据库表：只有 `tool_definition` 元数据（V54）。结果、计数与受限状态都在内存或既有统计表。
- 开关：环境变量 `MEDIA_ENABLED`（默认 true）与后台工具状态/分类状态共同生效；停用后 capabilities 返回 `available=false`，解析与下载均返回 503/10008。
- 后台复用现有工具管理、维护/关闭、分类控制、使用量与排行榜；网页首次连接成功后上报一次 `/api/v1/tools/short_video_parse/usage`，App 内 WebView 不重复计数。
- 页面自持脚本与样式（无 CDN、无第三方统计、无 JavaScript 桥），CSP 限制 `default-src 'self'`，图片仅同源与 blob。

## 界面与体验

- 单页布局：粘贴分享链接 → 解析 → 视频预览与清晰度切换/封面保存 → 图集勾选、打包 ZIP、逐张下载与大图预览（含键盘左右切换）→ 过期时间提示；支持 Ctrl/Cmd+Enter 解析、粘贴按钮、取消解析与取消下载。
- 中文/英文完整文案（默认跟随浏览器语言并记忆），浅色/深色跟随系统，尊重 `prefers-reduced-motion`；375 px 宽度实测无横向滚动，图集自动双列。
- 状态反馈：连接中/已连接/离线/服务停用四种状态；解析中禁用提交并显示取消；下载显示进度与取消，失败区分超时、超限、格式不支持、结果过期等固定文案。
- App 内打开：预览、选图与结果展示都在 WebView 完成；文件保存交给系统浏览器下载（`ToolboxWebScreen` 只放行本工具 `/api/v1/web-tools/short_video_parse/assets|albums` 的受控路径），并提示已交给系统浏览器下载；不引入 JavaScript 桥或存储权限。

## 实际验证

2026-10-13：

- Server 全量测试 **203/203** 通过（Linux 容器内 `mvn test`，与 CI 同环境），打包成功。其中 `com.cangshuo.toolbox.media` 新增 **30** 项测试（`MediaBoundaryTest` 5、`MediaParserTest` 10、`MediaServiceTest` 9、`MediaControllerTest` 4、`MediaHttpBoundaryTest` 2），覆盖：链接解析与伪装 Host、DNS 私网拒绝、JSON 状态解析（不执行 JS、不接受 NaN/尾随内容）、八平台样例结构、抖音码率排序与图集上限、小红书原图改写与实况配对、B 站分页与 DASH 拒绝、ZIP 选择与超限、Range 校验、下载魔数嗅探、并发信号量释放与幂等关闭、控制器 null 存储与二进制边界。
- Android `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` 通过：单元测试 **422/422**，APK 构建成功，Lint 0 error、85 warning（均为既有告警）；新增 `WebMediaDownloadPolicyTest` 覆盖受控下载路径与越权 URL 拒绝。
- `node --test scripts/check_web_short_video.cjs` **9/9** 通过：分享文案解析与八平台 Host 精确识别、凭据/控制字符/多链接拒绝、结果 schema 与不透明路径校验、过期与超量、ZIP 选择合法性、文件名防穿越、流式进度/截断/超限检测、取消释放、错误码与 traceId 脱敏。
- 浏览器实测（内置浏览器 + `scripts/check_web_media_ui_server.py` 合成 fixture，仅本机测试图片）：解析渲染、图集勾选与全选/取消、打包上限 61005 文案、大图弹层与左右翻页、英文切换、深色与 375 px 视口无横向溢出（`scrollWidth 360` ≤ `375`）。合成服务器不访问第三方平台。
- 隔离候选容器验收 **119/119**（`scripts/check_short_video_runtime.py`，含共享目录/统计基线）：V54 建表与元数据、公开目录 WEB 记录、`config_json` 路径、capabilities 预算、非法输入（无链接/超长/凭据/伪装域名）在触达第三方前拒绝、16384 字节正文预算、未知句柄 410/61004、任意下载 URL 拒绝、幂等清理、匿名管理 401、用量计数进入排行榜、维护暂停后 capabilities/解析/下载全部 503、重放保留暂停与软删除。
- 同一候选容器内真实平台抽查（`--live`）：抖音 `https://www.douyin.com/video/6961737553342991651` 解析成功（3 路视频 + 1 封面）；哔哩哔哩、小红书、TikTok 在该数据中心 IP 上返回 61002（B 站实测 HTTP 412 风控、小红书旧测试链接 302、TikTok 验证页），属于平台侧限制，错误分类与文案正确。
- 生产 HTTPS 只读验收 **21/21**（`scripts/verify_short_video_deploy.py`）：`/api/v1/tools` NETWORK 分类含 WEB 记录、capabilities 预算、非法/伪装/带凭据输入拒绝、匿名管理拒绝；页面与 style.css/engine.js/app.js/NOTICES.txt 全部 200，页面文案与隐私提示存在且 API 为同源相对路径；真实抖音链接再次解析成功并下载到 2,508,190 字节 `video/mp4`（该上游忽略 Range 直接返回 200 全量，服务端如实转发）；结果已清理；管理员登录、工具详情与 7/30/90 天统计正常。

验收期间发现并修复的缺陷：

- `Cache-Control: no-store, no-store`：请求预算过滤器与控制器重复设置响应头。改为过滤器统一设置，控制器不再重复（`MediaController` 去掉 `cacheControl`）。
- 抖音真实播放地址落在 `365yg.com` / `douyinstatic.com` 等字节 CDN 上，被媒体白名单拒绝成 61003。按真实响应补全抖音（`365yg.com`、`douyinstatic.com`、`toutiaoimg.com`、`zjcdn.com`）、西瓜/皮皮虾（同批域名）与 TikTok（`tiktok.com`）白名单。
- B 站数据中心 IP 的 412 风控原先归类为 61003，改为 61002「作品不可用或需要验证」，与客户端文案一致。
- 上游失败缺少可诊断信息：新增限流日志（最多每分钟一条），只记录主机名与异常类型，不记录 URL、查询串或正文。

部署：生产 API 镜像 `sha256:8e36c19108b49017cc059017734bcffabef09afb1199f017f485ab5c712b92c0`，Web（含本页静态资源）镜像 `sha256:dc296a9c2fc7bedcb99f898b23d7e1ae592b254eb54f2a92c6ce27e08cb2c6b7`，回滚标签 `before-short-video-v54`，四个容器健康。当前生产 compose 仍是旧版本（`MEDIA_ENABLED` 透传随下次同步生效），容器使用默认值 `true`。

## 后续验收清单

- 真机 WebView 打开、系统浏览器下载落地与返回键行为确认（本轮未执行）。
- 小红书与 TikTok 需要用户在手机端复制的当日分享链接；本轮旧链接在数据中心 IP 上被平台风控拦截，未做正向验证。
- 浏览器内的自动化复检（生产页面截图）因本机审批通道超时未完成。
