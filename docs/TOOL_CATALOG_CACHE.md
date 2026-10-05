# Android 远程目录持久缓存

2026-10-05，在既有目录请求校验与共享快照之上增加断网重启恢复。缓存只包含已完整校验的 WEB 元数据，不保存工具输入、网页资源、Token 或执行实现。

## 链路与存储

启动立即显示内置目录；后台 RestoreWebToolCatalogUseCase → RoomWebToolCatalogCache 读取完整快照，然后执行 RefreshWebToolCatalogUseCase。有效缓存先显示，完整网络结果最终覆盖；请求失败保留已恢复的目录。网络优先是更新权威来源，不让首屏等待网络。

复用应用私有 `toolbox.db`，数据库从 v2 升到 v3。新增 `MIGRATION_2_3`，v1 用户经 1→2→3 升级；不重写旧迁移、不启用破坏性回退，不改变收藏/最近使用表。服务端 MySQL/Flyway/API 均未改变。`cached_tool_catalog` 仅保存 singleton=1 的单行快照：format_version、source_key、written_at、payload_sha256、payload；单次 Room @Upsert 原子替换，没有先清空再插入的窗口。成功空目录也是有效快照，重启不会复活已删除工具。

source_key 是当前 API 基础地址 ASCII 形式去除尾斜杠后的 SHA-256，区分 debug/release、主机与 API 路径；不存储原地址。payload_sha256 检测意外损坏，不能作为防篡改签名或身份认证。written_at 是成功编码后保存的 UTC 毫秒时间。

运行时数据库位于 Android 应用沙盒，手机没有 Windows D 盘。可控制的本机源码、导出的 Room 3.json、测试报告、APK 和临时文件均在 D 盘。

## 开源与官方依据

查阅日期 2026-10-05；只采用流程和校验思路，没有复制第三方实现。

| 来源 | 实际阅读及采用 | 适配差异 |
| --- | --- | --- |
| [Now in Android NewsResourceDao.kt](https://github.com/android/nowinandroid/blob/main/core/database/src/main/kotlin/com/google/samples/apps/nowinandroid/core/database/dao/NewsResourceDao.kt)（main 可变化，Apache-2.0） | 阅读 @Transaction 查询、@Upsert 更新与按 ID 删除路径；采用共享 Room 数据源和主键更新 | 本项目保存一个完整目录快照，不建立新闻/交叉关联表，不按网络页面增量发布 |
| [Room @Upsert 官方说明](https://developer.android.com/reference/androidx/room/Upsert) | 核对按主键插入或更新的契约 | 复用现有 Room 2.8.5，不增加依赖；KSP 导出 schema v3 并生成 DAO |
| 项目 CatalogJsonReader、[目录网络源码参考](TOOL_CATALOG_SYNC.md#开源源码参考) | 复用严格字段类型、数字精度、Unicode/重复字段及预算检查 | 缓存不是 HTTP 响应，不伪造业务码或 trace；独立缓存格式版本，允许 300 个 WEB 条目与最多 60,000 个 token |

## 功能与边界

| 场景 | 行为 |
| --- | --- |
| 首次安装、无缓存 | 继续显示内置工具，再请求网络 |
| 断网重启 | 后台恢复最近成功缓存；WEB 页面仍需联网，恢复目录不等于缓存网页 |
| 成功更新 | 转换全部 DTO 后保存单行完整快照，再发布共享目录；正常空结果同样写入 |
| 无效或失败网络结果 | 不触及缓存，也不替换当前内存目录 |
| 缓存写失败或超预算 | 保留旧磁盘快照；仍发布已验证的网络结果到内存；不误报已持久化 |
| 缓存缺失/损坏/不兼容/地址不同 | 忽略整份缓存，不发布部分条目；网络请求仍执行；不删除收藏或最近使用 |
| 缓存体积 | 编码时限制 UTF-8 字节数，最多 1,000,000 字节；SQLite 查询先过滤 BLOB 字节长度，并限制投影，避免把超大值加载到 CursorWindow。该预算可能使较大的有效网络目录只能在内存更新 |
| 缓存结构 | 最多 300 条 WEB/ENABLED、64 个关键词/条、512 UTF-16 单元/关键词，16 层容器、60,000 个 token、字符串 4096 单元、数字 64 字符；编码、读取、还原均验证，不依赖数据库内容天然可信 |
| 损坏与重复 | checksum 不一致、非标准 JSON、未知枚举/类别、关闭/非 WEB 条目、重复 code 或逆序都拒绝整份快照 |
| 并发与取消 | 读写/编解码在 IO 调度器，使用检查点；发布前再次检查取消，取消向上传播。取消前已经提交的有效缓存不强制回滚；不会因此发布取消后的结果 |
| 陈旧数据 | 不设 TTL 删除最近成功快照；完整网络成功才移除旧远程项。启动、前台 5 分钟、手动 2 秒及并发去重见 [刷新策略](TOOL_CATALOG_REFRESH.md) |
| 隐私与错误 | 固定 CatalogCacheException，锁定 null cause，不保留底层异常、路径、payload 或原地址，不记录缓存内容 |

底层 SQLite/存储整体损坏可能使数据库不可用，当前不会自动删除或重建用户数据库；这种情况沿用现有收藏/最近使用的错误状态。跨页快照和网络取消限制仍见 [目录更新说明](TOOL_CATALOG_SYNC.md)。

## 实际验证

新增 23 个持久化 JUnit：CatalogSnapshotCodecTest 8 个、RoomWebToolCatalogCacheTest 8 个、CachedWebToolCatalogUseCasesTest 7 个，覆盖全部字段/Unicode/空快照、300 条/体积上限、无效与损坏、地址/版本隔离、checksum、存储失败、取消、恢复/网络替换与失效项不复活。DAO 使用内存替身；这些测试不代替 Android Room 运行。

首轮 74 个测试有 2 个失败：协程异常恢复为固定错误附加了 cause。显式设置 null cause 后，最终离线 `testDebugUnitTest/assembleDebug/lintDebug` 在 2 分 53 秒成功，74/74 通过，0 失败/错误/跳过；Lint 0 错误、22 警告，缓存源码/迁移/相关测试没有新增 Lint 问题。没有依赖升级。

`scripts/check_android_catalog_cache.py` 在本机 SQLite 内存数据库执行实际 Kotlin 迁移及 DAO SQL，并与 KSP 导出 3.json 比较；24 项通过：1/2→3、收藏/最近使用数据保留、三表结构、空快照、来源/版本隔离、1,000,000 字节边界、多字节体积拒绝及事务回滚。该脚本不创建临时数据库，不声称执行 Android Room/设备 CursorWindow；CI 已在 Android 构建后加入这一门禁。actionlint 及 git diff --check 通过。

Android 16 真机已验证真实服务更新、独立探针在进程重启和网络失败后恢复、搜索可见及联网成功移除探针。真实 Room v3 缓存的格式版本 1、SHA-256 一致、正常 WEB 记录为 qr_studio，公开摘要在 `android/app/build/reports/device-acceptance/room-cache.json`。临时数据库副本清理，不保留用户收藏/最近使用数据。旧版本迁移的设备过程未捕获，完整保存/TLS 等未验证，见 [本轮记录](WEB_TOOL_ACCEPTANCE_2026-10-05.md)。不据此勾选完整 Phase 2 或整项网页版发布。
