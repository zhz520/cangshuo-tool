# 账号云同步

2026-10-05，Phase 3 收藏同步。服务端 Controller → SyncService → SyncRepository，Android Compose → CloudSyncViewModel → CloudSyncUseCases → CloudSyncRepository → Room/Retrofit。

## 收藏的交互与隐私

默认关闭；登录后在「我的」主动开启，首次将匿名本机收藏合并到当前账号一次。匿名数据继续保留，账号的数据在独立分区。退出后展示匿名数据，切换账号不会混用收藏。关闭同步后保留账号在本机的数据，后续修改仍存待同步版本，重新开启后上传。不上传工具输入或扫码原文，只发送稳定编码、添加/删除、操作时间和应用生成的随机设备 ID。

## 版本与请求

V20 `user_sync_state`/`user_sync_entity`：每账号独立递增 revision，`user_id + entity_type + entity_key` 主键。客户端 `updatedAt` 为 UTC 毫秒，`deviceId` 为安装生成的 32 hex 随机值；按时间、再按设备 ID ASCII 顺序 Last Write Wins。删除保存 tombstone，旧添加不能复活。相同版本重复推送不生成 revision；同账号行锁串行，整个请求事务成功才提交。

`POST /sync/push` 每批 1–100 条，`GET /sync/pull?cursor=...` 每页最多 100 条。游标是账号 ID/revision 的 base64url，不是访问凭证，Bearer 决定所有权，跨账号或未来游标拒绝。响应有 nextCursor/hasMore，最新记录分页按 revision。允许 2000 年以来、最多未来 5 分钟的操作时间；每账号最多 10,000 个不同实体（含 tombstone），不自动删 tombstone。时间异常需修正设备时间后重试；不存在跨设备 useCount 求和或服务器时间替代规则。

Room v5 新增 sync_entry/sync_preference/sync_device，旧收藏、最近、目录缓存和扫码历史保留。dirty 表示持久 outbox，每实体保留最后一次操作。成功推送只确认同一时间/设备的快照，上传期间的新改动不会被误清；pull 合并和游标在同一个 Room 事务提交。应用启动、登录/开启、修改后及存活进程中每 60 秒重试；有手动同步和失败反馈，网络请求不阻塞收藏本机保存。单次请求响应 256 KiB、15 秒期限；同步最多 100 批/100 页，401 只刷新重试一次。未引入保证进程被杀后运行的后台 WorkManager。

## 查阅与适配

2026-10-05 阅读 [Now in Android OfflineFirstUserDataRepository 业务源码](https://github.com/android/nowinandroid/blob/main/core/data/src/main/kotlin/com/google/samples/apps/nowinandroid/core/data/repository/OfflineFirstUserDataRepository.kt) 的 bookmark/theme 写入本地路径，及 [SyncUtilities](https://github.com/android/nowinandroid/blob/main/core/data/src/main/kotlin/com/google/samples/apps/nowinandroid/core/data/SyncUtilities.kt) 的同步串行、删除/更新和成功后推进版本逻辑。本项目改用账号分区 Room outbox、双向 LWW、单事务游标和默认关闭；没有采用示例的 analytics，也没有复制其业务代码。

## 已验证范围

- Server 38/38、Android 161/161 自动测试通过；新增冲突、幂等、删改、账号隔离、默认关闭、断网队列与并发确认回归。
- 真实 MySQL/API/Nginx 26/26 检查：两个随机账号、多设备并发、LWW、重放、跨账号游标、未来时间、整批拒绝及 101 条分页；测试账号级联删除。用 HTTP 客户端模拟设备身份，没有执行两台 Android 手机互通。
- 实际 Room 4→5 SQL/导出 schema 与确认查询 12/12 主机 SQLite 检查；旧扫码历史 12 项仍通过。Android test/assemble/lint 通过（2 分 3 秒），0 错误/27 警告。
- 用户要求跳过真机；实际 Room 事务/进程恢复/系统网络与主题布局留发布验收，托管 CI 未运行。

## 最近使用同步（2026-10-05）

独立开关默认关闭，收藏同步不会触发上传使用记录。Room 5→6 为账号开关新增 recent_enabled/recent_enrolled（默认 0，旧收藏开关/游标保留）；首次开启只合并本机最近列表，对已下载账号记录按相同 LWW 比较。账号最近最多显示 12 项；持久层保留已知实体，清空为所有已知未删除记录保存 tombstone。其他设备尚未拉取到的独立条目不属于本次清空范围。

RECENT payload={lastUsedAt,useCount}，最后使用时间 UTC 毫秒、次数 1–2,147,483,647；delete 的 payload={}。useCount 是胜出版本的快照，不求多设备总和；后续使用在本机已知次数上加一，已删除后重新使用从 1 开始。同步只传元数据，不传工具输入/结果。停止同步仍保留账号分区，匿名列表独立。

Server 39 项、真实 API 32 项与 Room 4→5→6 主机 SQLite 21 项通过；最近开关、记录次数与清空 tombstone 已有 Android 回归，设备验收按用户要求省略。

## 设置同步（2026-10-05）

第三个独立开关，默认关闭。SETTING 只允许 theme（SYSTEM/LIGHT/DARK）、language（SYSTEM/zh-CN/en）、grid_columns（AUTO/1/2/3）和 startup_page（HOME/TOOLS/FAVORITES/PROFILE）。禁止上传 sync_enabled、扫码历史开关、密钥或任意扩展设置。每键独立 LWW，payload={value}，删除返回默认值。Room 6→7 加 settings_enabled/settings_enrolled，旧开关与游标保留。匿名设置使用本机分区 0，不进上传队列；账号设置分区独立，首次开启按版本合并本机四个值。

主题、语言与列数直接接入 Compose；语言用带配置的 ContextWrapper 和 CompositionLocal 更新字符串与本地工具元数据，保留 Activity 上下文及已成功远程目录，不新增库。SYSTEM 遵循 Android 当前应用配置；这属于应用内选择器，未双向桥接 Android 13 系统语言选择器或 IME，系统提供商行为留设备验收。目录选项是列数上限，随可用宽度限制；启动页等待会话恢复及匹配分区的本机设置，只在无页面恢复/用户未导航时应用，不打断当前工具和页面。

关闭某类同步后不合并该类的远程改动；重新开启重置游标进行完整版本合并，保留本机较新 outbox。开关修改和网络同步串行，pull 合并与本机编辑共享写锁；上传快照确认仍使用版本条件。

参考前述 Now in Android 的主题/本地写入源码与 [Android 应用语言指南](https://developer.android.com/guide/topics/resources/app-languages)（2026-10-05）。适配为现有 ComponentActivity/Compose 的配置上下文，账号/匿名 Room 设置分区，不采用示例的统计上报或新增 AppCompat 依赖。

服务端 40 项、真实 API 37 项与主机 SQLite 30 项通过；Android 已增加设置白名单、匿名不上传、仅已开启类型上传、账号切换、语言元数据保留和启动页导航回归，真实 JBR loopback 验证 Retrofit 混合类型记录及账号游标。真机渲染、系统重建和两台手机仍按用户要求延后。

最终 Android 170/170、Server 40/40、实际同步 API 37/37、SQLite 30/30；test/assemble/lint 通过，0 错误/28 警告。目录语言刷新保留远程工具，同账号续期保持身份，启动页等待会话与正确分区就绪。
