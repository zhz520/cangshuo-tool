# 本地收藏

当前数据库已随远程目录缓存升级到 Room v3，1→2→3 迁移保留收藏表及数据；本文日期记录仍对应初始收藏任务。缓存结构与实际验证见 [目录缓存说明](TOOL_CATALOG_CACHE.md)。

Phase 1 的匿名收藏功能。首页、工具列表、推荐区、搜索结果和工具标题栏提供统一星标按钮，底部“收藏”页读取本机保存的工具。界面提供中文和英语，默认跟随系统。

## 分层与实例范围

```text
HomeRoute / ToolCard / SearchScreen / FavoritesScreen
  → FavoritesViewModel
  → ObserveFavoritesUseCase / SetFavoriteUseCase
  → FavoriteRepository / RoomFavoriteRepository
  → FavoriteToolDao / ToolboxDatabase
```

`ToolboxApplication` 持有应用级 `ToolboxAppContainer`，同一进程内共用 Room 实例和注册中心。页面共享 Activity 范围的收藏 ViewModel，使用生命周期内的 StateFlow 收集。UI 不直接访问 Room，持久化读写使用 DAO 的 Flow/suspend 方法；没有主线程查询或破坏性迁移配置。

工具打开继续使用首页的状态、设备能力、登录与权限检查流程。收藏只是工具书签，不代替这些检查，也不保存计算器输入、输出或搜索原文。

## SQLite 结构

本机数据库文件为应用私有目录下的 `toolbox.db`，Room 初始版本为 1：

| 表/字段 | 类型 | 含义 |
| --- | --- | --- |
| `favorite_tool.tool_code` | TEXT，非空主键 | 稳定工具编码，区分大小写，防止重复收藏 |
| `favorite_tool.added_at` | INTEGER，非空 | 收藏时的 UTC Unix 毫秒时间，来自设备时钟 |

只存工具编码与时间。名称、说明、图标和状态从当前注册中心解析，随语言资源变化；收藏列表按 added_at 降序、tool_code 升序排列。同一工具再次设置为已收藏使用 INSERT IGNORE，保留已有时间；取消使用按编码参数化删除。重新收藏产生新的时间。

新增收藏需匹配有效编码且工具当前已注册、启用；取消收藏允许已移除或停用的有效编码。读取不会因工具缺失而删除书签：未注册工具显示通用不可用条目并允许取消，已注册但关闭或维护的工具保留卡片，点击时沿用状态提示。

本次是 Android SQLite 初始建库；服务端 MySQL/Flyway 仍为 V3。导出的 Room JSON schema 位于 `android/app/schemas/`，需纳入版本控制。后续结构变化增加 Room 数据库版本和保留数据的 Migration，不覆盖已导出的基线，不使用 fallbackToDestructiveMigration。服务端结构变更继续遵循 Flyway 规则。

## 状态与页面

- 初始读取显示 Loading，空列表显示添加方法和浏览工具入口；失败显示重试和固定错误提示。
- 收藏提交期间禁用同一工具在各页面的星标，避免并发重复操作。选中状态以 Room 查询结果为准，保存失败不伪造成功状态。
- 收藏数量与所有星标由同一份查询结果更新；取消后条目从列表移除。读取失败时停止编辑收藏，首页和搜索的工具打开流程仍可使用。
- 语言变化重新读取书签并从当前资源解析显示名称，编码和时间保持原值。
- 返回工具时回到原目录、搜索或收藏入口；数据库保存已提交的书签。进程重建后重新读取 Room，页面入口仍由既有 SavedStateHandle 管理。
- 搜索相关度相同时优先最近使用，其次已收藏，随后按 sortOrder、code 稳定排列；文本匹配等级优先于这些标记。空白查询也使用这一顺序，最近使用排序见 [本地最近使用说明](LOCAL_RECENT.md)。

App 未接入账号收藏接口或跨设备同步，书签无需登录即可操作。本地最近使用已单独接入，见 [本地最近使用说明](LOCAL_RECENT.md)；使用计数、历史、同步队列和收藏同步事件由后续任务实现；本轮没有记录或上传工具处理原文。

## 构建配置

2026-10-04 目录更新：Repository 已将 DAO Flow 与共享 ToolRegistryStore 快照合并；纯远程 WEB 工具到达、更新或移除时重新解析已有书签，无需再次写入数据库。目录回归测试使用 Flow DAO 替身，真实持久化和设备操作仍未验收，见 [目录更新说明](TOOL_CATALOG_SYNC.md)。

新增 Room runtime/compiler/Gradle plugin `2.8.5` 与 KSP `2.3.6`，统一放入版本目录。Room 插件导出并管理 schema；KSP 生成数据库与 DAO 实现。版本依据：

- [Room 官方发布说明与构建配置](https://developer.android.com/jetpack/androidx/releases/room)：2.8.5 已发布；该版本线支持 KSP2，Room 插件最低 AGP 为 8.4、最低 Android API 为 23。
- [KSP 2.3.6 发布说明](https://github.com/google/ksp/releases/tag/2.3.6)：包含 Windows 增量构建及 AGP 内置 Kotlin 检测修正。

当前工程使用 AGP 9.1.0、内置 Kotlin 2.2.10、Gradle 9.3.1 和 minSdk 26。其他功能依赖升级不属于本次任务。

## 在 Android Studio 查看

1. 打开 `android/` 并执行 Gradle Sync，首次同步会下载 Room/KSP 依赖。
2. 运行 `app`，点击计算器卡片或标题栏的星标，再打开底部“收藏”页。
3. 从收藏打开计算器，返回后可取消收藏；也可从搜索结果操作星标。
4. 重启应用后查看保存结果，再切换系统应用语言查看显示名称。

上述是待执行的体验路径，不表示已经完成设备验证。

## 验证记录

2026-10-04 使用 Android Studio JDK 21.0.9、SDK 36 和当前固定工具链：

- Android `:app:assembleDebug :app:lintDebug` 成功，用时 3 分 9 秒，47 个任务全部执行，Debug APK 已生成。
- KSP 已生成数据库和 DAO 实现，Room schema 已导出版本 1；生成的 SQL 使用参数绑定和 INSERT OR IGNORE。
- Lint 为 0 错误、11 个既有警告：9 个 SDK/构建工具/依赖版本提示、1 个应用图标提示、1 个 localeConfig 的 API 33+ 提示。
- Git 差异空白检查通过。

未新增或执行自动化测试，未启动设备或操作实际收藏数据库；收藏保存、取消、重启恢复、失败路径、排序、布局及语言切换仍待运行验证。构建及生成代码检查不代表收藏持久化或设备联调已经验证。

2026-10-05 已接入默认关闭的账号收藏同步，账号分区和删除冲突策略见 [云同步](CLOUD_SYNC.md)。
