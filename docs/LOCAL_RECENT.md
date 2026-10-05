# 本地最近使用

当前数据库已随远程目录缓存升级到 Room v3，2→3 迁移只新增缓存表，保留最近使用及收藏数据；本文日期记录仍对应初始最近使用任务。缓存结构与实际验证见 [目录缓存说明](TOOL_CATALOG_CACHE.md)。

Phase 1 的匿名最近使用功能。成功打开工具后在本机记录编码、时间与次数，首页“最近使用”区域按最近时间展示，可重新打开或清除。界面提供中文和英语，默认跟随系统，语言规则见 [多语言说明](ANDROID_LOCALIZATION.md)。

## 分层与实例范围

```text
HomeRoute / HomeScreen
  → RecentViewModel
  → ObserveRecentUseCase / RecordToolUseUseCase / ClearRecentUseCase
  → RecentRepository / RoomRecentRepository
  → RecentToolDao / ToolboxDatabase
```

`ToolboxApplication` 持有应用级 `ToolboxAppContainer`，同一进程内共用 Room 实例和注册中心。首页的最近使用 ViewModel 由 Activity 范围共享，UI 使用生命周期内的 StateFlow 收集。UI 不直接访问 Room，读写使用 DAO 的 Flow/suspend 方法，没有主线程查询。

记录发生在工具通过注册、目录状态、设备能力、登录与权限检查并真正打开之后；从首页、搜索、收藏或最近使用进入都会记录。记录失败不打断工具打开。最近使用只保存工具编码与统计，不保存计算器输入、输出或搜索原文。

## SQLite 结构

本机数据库文件为应用私有目录下的 `toolbox.db`，Room 数据库版本由 1 升到 2，新增 `recent_tool`：

| 表/字段 | 类型 | 含义 |
| --- | --- | --- |
| `recent_tool.tool_code` | TEXT，非空主键 | 稳定工具编码，同一工具一条记录 |
| `recent_tool.last_used_at` | INTEGER，非空 | 最近一次成功打开的 UTC Unix 毫秒时间 |
| `recent_tool.use_count` | INTEGER，非空 | 成功打开次数，从 1 开始累计 |

再次打开同一工具使用 REPLACE 更新时间和次数；每次记录后由单条 SQL 只保留 last_used_at 最新的 12 条，读取按 last_used_at 降序、tool_code 升序排列。已移除工具的记录保留在库中但不展示。清除删除全部本机记录，并在确认后执行。

版本 1 → 2 的 Migration 只新增 recent_tool，保留既有 favorite_tool 数据；不使用 fallbackToDestructiveMigration。导出的 Room JSON schema 位于 `android/app/schemas/`，需纳入版本控制。

## 状态与页面

- 首页最近使用区显示 Loading、空说明、失败重试或最近 6 条工具卡片；加载效果复用 `core/ui/ToolboxLoading.kt` 的共享指示器。
- 工具卡片显示共享收藏星标，状态与首页、搜索、收藏一致；记录更新后卡片顺序随之变化。
- “清除”位于区域标题右侧，仅在存在记录时出现；确认对话框说明只清除本机记录后执行，清除失败显示固定提示。
- 搜索相关度相同时优先最近使用，其次已收藏，再按 sortOrder、code 稳定排列；空白查询同样优先显示最近使用工具。语言变化时重新读取并解析当前语言名称。
- 从工具返回回到打开它的入口；最近使用状态由 Room 查询更新，进程重建后重新读取。

## 在 Android Studio 查看

1. 打开 `android/`，执行 Gradle Sync，运行 `app`。
2. 从首页、搜索或收藏打开计算器，返回首页后查看“最近使用”出现计算器卡片。
3. 再次打开后时间与次数更新；点击“清除”并确认后记录消失。

上述是待执行的体验路径，不表示已经完成设备验证。

## 构建配置

2026-10-04 目录更新：Repository 已将 DAO Flow 与共享 ToolRegistryStore 快照合并；纯远程 WEB 工具到达、更新或移除时重新解析已有记录，不改变时间或次数。目录回归测试使用 Flow DAO 替身，真实持久化和设备操作仍未验收，见 [目录更新说明](TOOL_CATALOG_SYNC.md)。

沿用 Room 2.8.5 与 KSP 2.3.6，无新增依赖或版本升级。Room 插件导出并管理 schema，KSP 生成数据库与 DAO 实现。

## 验证记录

2026-10-04 使用本机 Android Studio 自带 JDK 21.0.9、SDK 36 和现有固定工具链：

- Android `:app:assembleDebug` 成功，用时 46 秒，37 个任务中 16 个执行、21 个使用已有产物；Debug APK 已生成。
- `:app:lintDebug` 成功，用时 35 秒；Lint 为 0 错误、11 个既有警告：9 个 SDK/构建工具/依赖版本提示、1 个应用图标提示、1 个 localeConfig 的 API 33+ 提示，无新增。
- KSP 已导出 Room schema 版本 2，recent_tool 建表 SQL 与 MIGRATION_1_2 一致。
- Git 差异空白检查通过。

未新增或执行自动化测试，未启动设备或操作实际数据库；记录写入、去重计数、上限裁剪、重启恢复、清除、排序、布局及语言切换仍待运行验证。构建及静态检查不代表最近使用持久化或设备联调已经验证。

2026-10-05：独立且默认关闭的账号最近同步已接入；账号列表仍最多 12 项，删除/清空保存同步版本，匿名列表独立，次数按 LWW 快照处理。见 [云同步](CLOUD_SYNC.md)。
