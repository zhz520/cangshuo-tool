# 网页版工具承载说明（Decision 019）

本文记录官网网页版工具从目录登记到 App 内打开的完整链路、当前实现范围与新增工具步骤。规则来源见 `toolbox-vibe-spec/PROJECT_SPEC.md` Decision 019 与 `docs/TOOL_DEVELOPMENT_GUIDE.md` 第 4 节。

## 1. 链路总览

| 环节 | 位置 | 现状 |
| --- | --- | --- |
| 模式枚举 | Android `core/model/ToolMode.kt`（LOCAL/SERVER/HYBRID/WEB） | 已实现 |
| 传输模型 | Android `core/network/model/ToolCatalogDto.kt`（按枚举名映射） | 已实现 |
| 接口契约 | Server `ToolResponse` 的 OpenAPI 允许值含 WEB | 已实现 |
| 数据库约束 | `V15__allow_web_tool_mode.sql` 放宽 `ck_tool_definition_mode` | 已执行（schema v15） |
| 目录登记 | `V16__seed_web_qr_studio_tool.sql` 登记 `qr_studio`（mode=WEB） | 已执行（schema v16） |
| 官网页面 | `deploy/site/tools/<code>/`，Nginx 静态托管 | 已实现（`qr_studio`） |
| 客户端容器 | `core/ui/ToolboxWebView.kt`（ToolboxWebScreen） | 已实现 |
| 打开路径 | `feature/home/ui/HomeRoute.kt` 的 ToolDetailPage 按 mode 分支 | 已接线 |
| 来源白名单与基础地址 | `BuildConfig.WEB_TOOL_BASE_URL` + 精确 scheme/host/有效端口 | 已实现；禁止用户凭据、其它来源及 file/data/javascript 导航（受限导出除外） |
| 客户端本地定义 | feature/webtools/QrStudioToolDefinition.kt（qr_studio，中英资源与 V16 描述一致） | 已实现 |
| 目录远程合并 | ToolCatalogClient 有界读取并完整校验，经 UseCase 保存快照后一次发布 | 四处入口自动观察；失败/取消/超限保留旧目录，最多 3 页/300 条、20 秒；Room v3 持久缓存与启动/前台 5 分钟/手动 2 秒去重刷新，见 [缓存](TOOL_CATALOG_CACHE.md)和[刷新](TOOL_CATALOG_REFRESH.md) |

## 2. 地址与白名单

- 正式：`https://tool.zhzgo.cn/tools/<tool_code>/`；本地：`http://localhost:8088/tools/<tool_code>/`。
- 基础地址来自构建配置：默认正式域名，debug 变体覆盖为本地地址（见 `android/app/build.gradle.kts`）。
- WebView 按来源（协议/主机/有效端口）校验初始导航、后续导航与可拦截的资源请求，禁止用户凭据及其它来源；主框架越权显示本地化错误，资源请求返回固定空 403。系统对子资源后续重定向的回调限制尚未做完整设备验收，不声称能捕获平台内所有请求。
- 明文流量：主变体禁用；debug 变体仅对 localhost / 127.0.0.1 / 10.0.2.2 放开（`src/debug/res/xml/network_security_config.xml`）。
- 网页只从本站加载脚本与样式，不引用 CDN，避免被白名单拦截。

## 3. 页面结构（以 qr_studio 为例）

```
deploy/site/tools/qr_studio/
├── index.html   表单、预览画布、下载按钮；深色模式与安全区适配
├── payload.js   纯载荷构建（文本 / Wi-Fi / vCard），无 DOM，可被 node 测试
├── app.js       DOM 绑定、二维码渲染、PNG/JPEG/SVG 导出
└── qrcode.js    Kazuhiko Arase qrcode-generator 1.4.4（MIT），随站点自带
```

- 载荷构建与渲染分离，便于用 node 直接断言编码结果（转义、EAP 字段、vCard 多值、CRLF）。
- 已覆盖：文本/链接；Wi-Fi（WPA/WPA2、WPA3、WPA2/WPA3 企业 EAP、WEP、开放网络、隐藏网络）；vCard 3.0（多电话、多邮箱、多地址）；容错等级、尺寸与留白；PNG/JPEG/SVG 导出与复制内容。
- 未覆盖：图片裁剪与识别（识别仍在原生侧），二维码以外的符号生成、历史持久化。

### 2026-10-05 容器与导出修复

- 每次加载使用 `LOAD_NO_CACHE`；Nginx `/tools/` 返回 `Cache-Control: no-cache`，脚本增加版本参数，避免旧 HTML/脚本掩盖更新和请求失败。工具页面仍需联网。
- UTF-8 明确启用，空内容/失败清空旧结果并禁用复制和导出；修正 QR 版本展示、Wi-Fi/vCard 换行转义，限制 SSID 32 UTF-8 字节并检查必填字段、代理项和容量。
- 主框架 HTTP/网络失败显示本地化错误；SSL 一律取消；渲染进程崩溃/容器创建失败有错误和重试路径。重试只发起一次新加载，返回键优先网页历史；关闭时销毁 WebView。这些平台路径的完整运行矩阵未验证。
- 普通浏览器走 Blob 下载；App 专用 UA 标记让页面用受限 data URL 传递生成文件。通过导航/下载回调交给 ExportViewModel → Decode/SaveUseCase → ContentResolver Repository，以 `ACTION_CREATE_DOCUMENT` 保存；不引入 JavaScript 桥或存储权限。只接受 PNG/JPEG/固定几何 SVG、最多 4,000,000 解码字节（URL 5,333,400 字符）；PNG 尺寸 1–2048，JPEG 校验首尾，SVG 拒绝主动内容/外部资源/实体。并非通用图片解码器或 SVG 清洗器。
- 保存/校验在 IO，重复操作受 busy 状态控制，取消位置选择可重试，错误为固定双语消息。文件提供者异常可能留下部分文件，不能擅自删除用户选择的目标。

源码参考、功能边界及真实验证矩阵见 [2026-10-05 记录](WEB_TOOL_ACCEPTANCE_2026-10-05.md)。手机文件保存仍未验证，不能由单元测试推断保存已通过。

## 4. 新增一个网页版工具的步骤

1. 在 `deploy/site/tools/<code>/` 下新建页面目录，静态资源使用相对路径，脚本随站点自带。
2. 新增一条 Flyway 迁移登记目录记录：`tool_code` 与目录名一致，`mode='WEB'`，补齐名称、描述、分类、关键词、排序；不要修改已执行的迁移。
3. 客户端下次启动时经目录请求注册新的 WEB 工具；结果到达后四处入口自动更新。需首次离线可见的工具仍可增加内置定义；联网请求及缓存限制见 [目录更新说明](TOOL_CATALOG_SYNC.md)。
4. 重建 admin 与 server 镜像后验证：`/api/v1/tools` 含该 code 且 mode 为 WEB；`/tools/<code>/` 与全部资源返回 200。
5. 更新本文、工具说明文档与 ROADMAP；记录验证范围（构建/Lint、页面与资源状态码、载荷测试），未做的真机验收要写明。

## 5. 验收清单

- 构建与静态检查：Android `assembleDebug`/`lintDebug` 无新增警告；Server 打包与迁移校验通过。
- 接口：`/api/v1/tools` 直连与代理一致，WEB 记录的 mode/status/sortOrder 正确。
- 页面：索引与每个静态资源返回 200；表单输入、下载按钮、深色模式在浏览器中可用。
- 容器（真机）：首次加载显示共享加载组件；断网与证书错误显示本地化错误并可重试；越权域名被拦截；返回键先回退网页历史；提示文案显示处理域名；系统返回与工具关闭行为一致。
- 安全：不通过 URL 传递 Token 或敏感原文；页面不记录敏感数据；WebView 关闭文件/内容访问与 JS 桥。

## 6. 运行记录（2026-10-04）

- schema v15：放宽 mode CHECK 允许 WEB；schema v16：登记 `qr_studio`（WEB/ENABLED/211）。
- `/api/v1/tools` 直连与 Nginx 代理均返回 total=12 且包含 `qr_studio`。
- `http://localhost:8088/tools/qr_studio/` 返回 200（5760 字节、含表单），`payload.js`/`app.js`/`qrcode.js` 均 200。
- node 断言通过：文本直通、Wi-Fi 转义与开放网络、EAP 的 E/I/PH2、vCard 多值与转义、CRLF 分隔；期间修复了换行转义双反斜杠与 vCard ADR 缺尾分号两个缺陷。
- 此时未执行浏览器自动化、真机 WebView 交互验收；客户端目录合并及自动更新在后续任务接入，见 [目录更新说明](TOOL_CATALOG_SYNC.md)。
