# 待办事项

## 定位

- 工具编码：`todo_list`
- 分类 / 模式：`LIFE` / `LOCAL`
- 排序：629
- 数据迁移：`V77__seed_todo_list_tool.sql`
- 分层：Compose → `TodoViewModel` → `TodoUseCases` → `TodoRepository`

在本机创建和管理待办，支持标题、备注、优先级、可选截止日期、完成状态、搜索、筛选、四种排序、统计与已完成清理。待办只写入 Android Room 数据库，不上传、不同步，也不需要账号或运行时权限。

## 开源参考

查阅日期：2026-10-17。

| 项目 | 固定版本与许可 | 实际核验源码 | 采用内容与适配差异 |
| --- | --- | --- | --- |
| [tasks/tasks](https://github.com/tasks/tasks) | [`4af8bb5dfe931ad073c5e8ca659cad8c35aee5fd`](https://github.com/tasks/tasks/commit/4af8bb5dfe931ad073c5e8ca659cad8c35aee5fd)；GPL-3.0 | [TaskRow.kt](https://github.com/tasks/tasks/blob/4af8bb5dfe931ad073c5e8ca659cad8c35aee5fd/composeApp/src/commonMain/kotlin/org/tasks/compose/tasklist/TaskRow.kt)、[TaskViewHolder.kt](https://github.com/tasks/tasks/blob/4af8bb5dfe931ad073c5e8ca659cad8c35aee5fd/app/src/main/java/org/tasks/tasklist/TaskViewHolder.kt)、[HeaderFormatter.kt](https://github.com/tasks/tasks/blob/4af8bb5dfe931ad073c5e8ca659cad8c35aee5fd/kmp/src/commonMain/kotlin/org/tasks/tasklist/HeaderFormatter.kt)、[SectionedDataSourceTest.kt](https://github.com/tasks/tasks/blob/4af8bb5dfe931ad073c5e8ca659cad8c35aee5fd/kmp/src/jvmTest/kotlin/org/tasks/tasklist/SectionedDataSourceTest.kt) | 参考任务列表的持续可点击行、完成项保留、状态切换、截止日期展示与分组/统计思路。本项目使用 Compose + Room 独立实现单列表、搜索筛选、优先级、统计、四种排序和有界本地存储；没有复制 GPL 源码、测试、资源或依赖，也不包含 Tasks.org 的同步服务、账户、标签、子任务、重复规则、通知和日历集成。 |

## 已实现功能

- 新建和编辑：标题、最多 2000 个 Unicode 码点的可选备注、高/普通/低优先级与可选截止日期。
- 完成状态：可勾选完成或重新打开；完成时记录时间，取消完成时清除完成时间。
- 搜索：对标题和备注执行大小写不敏感的子串匹配，查询最多 80 个字符。
- 筛选：全部、进行中、已完成三种状态。
- 排序：进行中优先（同组最近创建优先）、截止日期、优先级、最近创建。
- 统计：全部任务上的进行中、已逾期、今天到期和已完成数量；已完成的逾期/今天到期事项不重复计数。
- 危险操作：单条删除和清除全部已完成均要求二次确认。
- 空状态、加载状态、保存失败提示、中文和英文资源、24dp 线性图标。
- 搜索词、筛选和排序写入 `SavedStateHandle`，旋转屏幕、进程短暂回收或重新进入时恢复。
- 窄屏、深色主题和 2 倍字体适配：统计使用 2×2 布局，筛选和排序自动换行，任务操作用独立行，编辑对话框支持滚动并在软键盘上方保持操作按钮可见。

## 数据与边界

- Android Room 升至 v8，新增 `todo_task` 表；`MIGRATION_7_8` 只创建新表和三个索引，不修改已有的收藏、最近使用、目录缓存、二维码、同步或设置表。导出的 `8.json` 与迁移 SQL 的列、主键和索引一致。
- `due_date` 保存设备本地日期对应的 epoch day；`completed_at`、`created_at` 和 `updated_at` 保存毫秒时间戳。日期跨午夜后，页面重新进入或刷新会按新的设备日期计算逾期与今天到期。
- 标题去除首尾空白并把连续空白折叠成一个空格；标题与备注预算按 Unicode code point 计算，避免表情符号占用两次预算。
- 最多保存 500 项任务；超限时拒绝新建并明确提示，已有任务仍可编辑、完成或删除。
- 进行中优先排序先按完成状态分组，再按创建时间倒序；截止日期排序同样把进行中任务放在已完成项之前，同组内未设置日期的任务排在最后；优先级排序按高、普通、低排列，再用截止日期和创建时间打破平局；最近创建排序不按完成状态分组。
- 不提供重复任务、提醒通知、闹钟、附件、子任务、标签、多清单、协作、云同步、导入导出或跨设备合并。备注只是本地文本，不进行联网识别。
- 应用不申请通知、存储、账户或网络权限来完成待办功能；卸载应用或清除应用数据会删除本机任务。

## 验证范围

定向 JUnit 13 项覆盖：标题/备注 code point 预算、空白归一化、优先级解析、搜索与状态筛选、四种排序、空截止日期、统计、完成时间；以及 ViewModel 的初始收集、SavedState、增改、500 项限制、切换、删除、清理、存储失败和重新创建恢复。

2026-10-17 Android 16 模拟器（1080×2424）实测：从工具搜索打开待办；新建高优先级、带备注和截止日期的事项；新建普通事项；勾选完成与重新打开；全部/进行中/已完成筛选；四种排序切换；标题搜索；编辑标题和优先级；停止进程后重新打开并确认两项数据仍在；深色主题和 2 倍字体布局；编辑框弹出软键盘时仍可滚动并访问保存操作。

同一模拟器从真实 **Room v7** 数据库升级到 **v8**：升级前 12 条最近使用记录保留，升级后 `PRAGMA user_version=8`，`todo_task` 的 9 个列和 3 个索引与导出 schema 一致，原有目录缓存与其他同步表未被删除。

本地门禁：Android 全量 **725/725**，107 个测试套件，`assembleDebug` 成功；APK 为 **24,166,476** 字节；Lint **0 错误 / 91 条警告**，待办源码、资源、测试与 schema 无 finding。服务端 **226/226**；V77 离线静态契约 **86/86**；后台 `vue-tsc --noEmit` 与生产构建通过。
