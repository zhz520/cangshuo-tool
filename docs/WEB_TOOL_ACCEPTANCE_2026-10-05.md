# 目录缓存、刷新与网页工具：2026-10-05 收尾记录

Room v3 持久目录缓存和去重刷新已完成，网页与 WebView 中发现的问题已修复。本轮完成必要构建、业务回归及本地浏览器/真机基础联调。用户要求加快推进后停止追加设备场景；未验证项保留到发布前，不作为后续功能开发的等待条件。这里不宣称完整 Phase 2 或网页版发布验收通过。

## 实现与文件范围

- 缓存：`core/database/CachedToolCatalog*`、`CatalogSnapshotCodec`、`RoomWebToolCatalogCache`、Restore/Refresh UseCase；来源/版本/checksum 隔离，完整有界快照，新增 2→3 migration，保留 1→2 路径和收藏/最近使用。
- 刷新：`RequestWebToolCatalogSyncUseCase`、应用容器和首页 ViewModel/Compose；首次恢复后联网，前台 5 分钟、手动 2 秒防连点，共享在途任务，失败保留列表和筛选。
- 网页：`deploy/site/tools/qr_studio/`；启用真实 UTF-8 编码、清空失败/空输入旧结果、正确版本号、Wi-Fi/vCard 转义和必填/长度/容量反馈。
- 容器：`ToolboxWebView`、`feature/webtools/domain/data/ui`；精确来源、主框架错误/重试、SSL 取消、历史回退、渲染失败恢复和受限 SAF 导出，沿用 ViewModel → UseCase → Repository 分层。
- 缓存更新：WebView LOAD_NO_CACHE，Nginx `/tools/` no-cache，网页资源版本参数；正式 HTTPS、API 契约及已执行的 Flyway 文件未改。

细节及边界见 [目录缓存](TOOL_CATALOG_CACHE.md)、[刷新策略](TOOL_CATALOG_REFRESH.md)和[网页版承载](WEB_TOOL_HOSTING.md)。

## 实际阅读的开源源码

查阅日期：2026-10-05。下面只描述读过的具体逻辑，不声称阅读完整项目。

| 源码 | 实际阅读与采用 | 适配差异 |
| --- | --- | --- |
| [qrcode-generator js1.4.4 qrcode.js](https://github.com/kazuhikoarase/qrcode-generator/blob/js1.4.4/js/qrcode.js)、[qrcode_UTF8.js](https://github.com/kazuhikoarase/qrcode-generator/blob/js1.4.4/js/qrcode_UTF8.js)（MIT） | 阅读 byte 编码、自动版本/容量和模块数；采用已自带的 UTF-8 转换函数，按 `(moduleCount-17)/4` 显示版本 | 保留现有 1.4.4 vendor，无依赖升级；客户端先限制输入和字节数，厂商容量失败转换为固定中文反馈，不展示内部异常 |
| [Home Assistant Android HAWebViewClient.kt](https://github.com/home-assistant/android/blob/main/app/src/main/kotlin/io/homeassistant/companion/android/util/HAWebViewClient.kt)（Apache-2.0，main 可变化） | 阅读主框架网络/HTTP/SSL 失败过滤、历史更新和渲染进程回调；采用主框架错误状态、SSL 取消和可观察回退能力 | 不复制证书认证、原始 URL 日志或 JS 注入；本项目限制到单一配置来源，复用共享加载及双语错误 |
| [Now in Android NewsResourceDao.kt](https://github.com/android/nowinandroid/blob/main/core/database/src/main/kotlin/com/google/samples/apps/nowinandroid/core/database/dao/NewsResourceDao.kt)（Apache-2.0） | 阅读 Room 事务和 Upsert 更新路径；采用完整快照原子写入 | 单行 WEB 快照、独立来源和 checksum；没有复制示例的关联图/增量同步 |
| [Now in Android SyncManager.kt](https://github.com/android/nowinandroid/blob/main/core/data/src/main/kotlin/com/google/samples/apps/nowinandroid/core/data/util/SyncManager.kt)、[SyncWorker.kt](https://github.com/android/nowinandroid/blob/main/sync/work/src/main/kotlin/com/google/samples/apps/nowinandroid/sync/workers/SyncWorker.kt)（Apache-2.0） | 阅读可观察同步状态、显式请求接口及 IO 仓库同步；采用应用级共享请求/状态 | 用现有协程、Mutex 和共享 Deferred，没有引入 WorkManager、周期后台任务或分析事件 |

网络来源与历史查阅日期另见 [请求校验说明](TOOL_CATALOG_SYNC.md)。

## 已执行的验证

| 项目 | 结果与实际范围 |
| --- | --- |
| Android 必要门禁 | 最终离线 `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` 成功，2 分 44 秒；96/96 JUnit，0 失败/错误；Lint 0 错误/22 条既有警告，无新增容器/导出问题 |
| 主机 SQLite | `scripts/check_android_catalog_cache.py` 24 项通过，执行 Kotlin migration/DAO SQL 并比对 schema；1/2→3、收藏/最近使用保留、预算/来源/版本/事务；不代替完整 Android migration 演练 |
| 网页回归 | `scripts/check_web_qr.cjs` 22 项通过，使用实际 payload/qrcode/app 逻辑；Unicode、Wi-Fi EAP/开放网络/转义、vCard 多值/CRLF、必填和 SSID 边界、容量/代理项、空输入清除和导出事件。DOM/Canvas 为替身，不代替浏览器实际文件下载 |
| 浏览器交互 | 本地二维码工作台：中文/emoji 文本、H/1024/留白参数，WPA3-EAP/隐藏网络/身份转义，vCard 多电话/邮箱/地址；空必填禁用导出；1000 个中文字符显示容量错误并禁用复制/导出，缩短后恢复；复制得到合成文本“沧烁验收 QA 123 😀”；深色移动宽度页面可显示 |
| 真机目录 | Vivo V2453A，Android 16/API 36，保留数据更新安装；真实 API 12 项且首页更新成功。隔离本地代理增加纯远程探针后 13 项，模拟 HTTP 503 并重启进程仍恢复探针，搜索可见；恢复真实成功快照后回到 12 项，同一查询不再找到探针；没有改后端目录数据库 |
| 真机 Room | 进程停止后读取应用缓存；数据库 v3、格式版本 1、正常快照 404 字节、SHA-256 一致、WEB code 为 qr_studio。仅保留公开缓存摘要，不保留完整用户数据库副本 |
| 真机网页 | 搜索 qr_studio 并打开二维码工作台成功。实际发现旧 HTML/脚本缓存，随后补充禁缓存和服务端 no-cache；最后修复包已构建，因 USB 再次断开尚未更新安装到手机 |
| 部署与 CI 配置 | 本地 admin 镜像重建/启动，Nginx `-t` 通过；二维码页 200、Cache-Control=no-cache；actionlint 通过。新增 SQLite/网页门禁已接入 CI，尚未执行新增 GitHub 托管运行 |

## 未验证与保留边界

- 手机 PNG/JPEG/SVG 的系统保存流程及实际生成文件尚未验收。浏览器下载事件多次未在内置浏览器工具中返回，不能把按钮可点击或替身回归当成下载成功。导出解码和 ViewModel 状态有 11 个来源/导出/保存状态测试，内容提供者的实际写入仍需补验。
- 真机完整历史返回、主框架 404/断网/重试、越权资源、SSL 证书失败、渲染进程恢复、缺少 WebView、不同 Android 版本/语言/生命周期尚未运行完整矩阵；生产 DNS/HTTPS/真实证书未部署。没有忽略证书或安装信任证书。
- 目录失败联调用本地 HTTP 503，不能据此宣称设备的所有物理断网/DNS/不可中断 I/O 场景通过；跨页快照 token 和底层取消限制仍存在。
- PNG 只校验必要签名/尺寸、JPEG 首尾，SVG 只接受本页固定几何模板；不是通用图片完整性校验器。文件提供者失败可能留下部分用户目标文件，不擅自删除目标。
- 二维码原生功能已在后续任务全部完成（扫码历史、裁剪、线性条码、PNG/JPEG/SVG、邮件抄送、企业 Wi-Fi、vCard 多值，见 docs/QR_TOOL.md）；剩余的第三方 Wi-Fi/vCard 导入属于设备验收范围。qr_studio 描述中的裁剪措辞已由后续任务 V17 修正为“尺寸与留白控制”，V16 文件未修改；扫码历史、手动框选识别与事件时区/提醒已在后续小任务完成，见 docs/QR_HISTORY.md 和 docs/QR_TOOL.md。

## 产物与现场收尾

所有可控制的主机文件保存在 D 盘项目目录：

- APK：`android/app/build/outputs/apk/debug/app-debug.apk`（本地真机 API override）。
- 测试/Lint：`android/app/build/reports/tests/testDebugUnitTest/`、`android/app/build/test-results/testDebugUnitTest/`、`android/app/build/reports/lint-results-debug.html`。
- 真机证据：`android/app/build/reports/device-acceptance/` 的 catalog-network-probe、catalog-offline-home/search、catalog-network-recovered、webview-initial 截图/XML及 room-cache.json；webview-initial 是修复禁缓存前的历史截图。

临时代理与验收脚本、私有数据库副本须在收尾清理；保留项目自身构建缓存和其他任务文件。普通网页在本地后台浏览器中验收，浏览器自身固定缓存位置不由本项目控制；手机文件系统也不能重定向为主机 D 盘。

手机临时充电亮屏的原值为 0（关闭），验收期间值为 7。收尾时手机 USB 已断开，无法写回；需要恢复连接后执行 `adb shell settings put global stay_on_while_plugged_in 0`，或用户在开发者选项关闭“充电时保持唤醒”。不会把未恢复写成已恢复。最终禁缓存 APK 的手机安装同样保留为未执行。

扫码历史、事件时区/提醒与手动框选识别已作为后续小任务完成。每项只运行与本次修改直接相关的必要检查；以上补验集中在发布前处理。
