# 习惯打卡

## 定位

- 工具编码：`habit_tracker`
- 分类 / 模式：`LIFE` / `LOCAL`
- 排序：630
- 数据迁移：`V78__seed_habit_tracker_tool.sql`
- 分层：Compose → `HabitViewModel` → `HabitUseCases` → `HabitRepository`

在本机建立布尔型习惯，为每个习惯选择每周打卡日，并记录今天和历史打卡。页面展示今日状态、本周进度、当前/最佳连续天数、近 30 天完成率及最近 28 天历史；归档、删除和清除记录都保留清晰的确认或可逆状态。

## 开源参考

查阅日期：2026-10-17。

| 项目 | 固定版本与许可 | 实际核验源码 | 采用内容与适配差异 |
| --- | --- | --- | --- |
| [iSoron/uhabits](https://github.com/iSoron/uhabits) | [`516bf394f85a5a3ab25f476da230ae2a93815a40`](https://github.com/iSoron/uhabits/commit/516bf394f85a5a3ab25f476da230ae2a93815a40)；GPL-3.0 | [Habit.kt](https://github.com/iSoron/uhabits/blob/516bf394f85a5a3ab25f476da230ae2a93815a40/uhabits-core/src/jvmMain/java/org/isoron/uhabits/core/models/Habit.kt)、[Frequency.kt](https://github.com/iSoron/uhabits/blob/516bf394f85a5a3ab25f476da230ae2a93815a40/uhabits-core/src/jvmMain/java/org/isoron/uhabits/core/models/Frequency.kt)、[EntryList.kt](https://github.com/iSoron/uhabits/blob/516bf394f85a5a3ab25f476da230ae2a93815a40/uhabits-core/src/jvmMain/java/org/isoron/uhabits/core/models/EntryList.kt)、[StreakList.kt](https://github.com/iSoron/uhabits/blob/516bf394f85a5a3ab25f476da230ae2a93815a40/uhabits-core/src/jvmMain/java/org/isoron/uhabits/core/models/StreakList.kt)、[HabitCardView.kt](https://github.com/iSoron/uhabits/blob/516bf394f85a5a3ab25f476da230ae2a93815a40/uhabits-android/src/main/java/org/isoron/uhabits/activities/habits/list/views/HabitCardView.kt) | 参考习惯定义与独立打卡条目、按频率生成计划日、连续区间、归档及卡片中的最近打卡序列。本项目按现有轻量架构自行实现每周星期掩码、纯本地布尔习惯、当前/最佳连续区间、近 30 天完成率和 28 天历史；没有复制 GPL 源码、资源或测试，也不包含量化习惯、自动补全、提醒、桌面组件、导入导出、备份和 Loop 的数据库格式。 |

## 已实现功能

- 新建和编辑：名称、最多 500 个 Unicode 码点的可选说明、每周一至周日至少一个打卡日。
- 今日打卡与撤销；详情中可点按习惯创建后至今天的日期补记或撤销。
- 当前连续和最佳连续只统计计划日；非计划日的额外打卡保留在累计记录中，但不会制造连续天数。
- 本周进度、近 30 天完成率、累计打卡次数和今日状态。
- 搜索名称/说明；全部、今日、归档筛选；今日优先、连续天数、最近创建、名称排序。
- 归档/恢复、删除确认、清除全部历史确认；归档习惯只读保留历史，`archived_day` 会冻结归档日之后的当前连续与完成率。
- 搜索、筛选、排序写入 `SavedStateHandle`；打卡操作按习惯禁用重复点击。
- 中文和英文资源、独立 24dp 线性图标、窄屏换行、深色主题、2 倍字体和软键盘适配。

## 输入、数据与边界

- Room 升至 v9，新增 `habit` 与 `habit_entry`；打卡以 `(habit_id, date_epoch_day)` 为复合主键，外键删除习惯时级联删除记录。`MIGRATION_8_9` 只创建两张表和索引。100 个习惯与 10000 条记录上限由单条 Room SQL 原子检查，避免并发同时通过校验。
- 星期掩码周一为最低位、周日为最高位；至少选择一天且只允许 7 位。日期使用设备本地 epoch day，跨午夜后重新进入页面会刷新今天。
- 最多 100 个习惯和 10000 条打卡；名称 80、说明 500 个 Unicode code point。数量或长度超限会明确拒绝，不静默截断已有内容。
- 当前连续允许“今天尚未打卡但今天还未结束”的宽限：从今天之前最近计划日的连续记录计算；一旦最近计划日缺失则为 0。最佳连续从全部计划日打卡记录计算。归档习惯以归档日为统计终点。
- 习惯创建前的历史日期不可编辑；归档习惯不可新增或撤销打卡，但可恢复、查看、清除历史或删除。
- 本周进度只统计习惯创建日至今天之间的计划日；窄屏历史网格自动改为 4 列，每个日期格至少 48dp，高字体下本周日期圆点可随文字增长。
- 保存期间编辑器和操作会锁定；写入失败时保持草稿并显示错误。跨午夜、时区变化或从后台恢复时，页面会重新读取设备今天。
- 不提供系统提醒/通知、量化目标、子习惯、标签、多套时区、跨设备同步、备份或导入导出。页面不会声称这些能力已经实现。
- 卸载应用或清除应用数据会删除本机习惯和打卡历史。

## 验证范围

定向 JUnit 16 项覆盖：名称/说明 code point 预算、说明和星期校验、星期掩码、跨年日期、今天宽限与连续中断、非计划日忽略、最佳连续、周进度、30 天完成率、归档日冻结统计、搜索/筛选/排序、统计、历史范围和 ViewModel 的 SavedState、增改、上限、打卡/撤销、条目上限、归档、恢复、清理、删除、日期刷新和存储失败。

2026-10-17 Android 16 模拟器（1080×2424）实测：从工具搜索打开；创建每周一/三/五习惯并编辑加入周六；今日待打卡与完成统计；直接打卡和撤销；详情中的 28 天网格；归档/恢复、清除历史和删除确认；标题搜索、今日/归档筛选和排序切换；安装升级后本机习惯仍在；深色主题、2 倍字体和弹出键盘的编辑布局；真实 Room v8→v9 升级保留已有 12 条最近使用记录。

本地门禁：Android 全量 **741/741**，109 个测试套件；`assembleDebug` 成功，APK **24,166,555** 字节；Lint **0 错误 / 91 条警告**，与接入前基线一致，习惯源码、资源、测试与 schema 无 finding。服务端 **226/226**；V78 离线静态契约 **102/102**；后台 `vue-tsc --noEmit` 与生产构建通过。

2026-10-18 发布验收：严格隔离候选 **274/274** 通过，使用一次性 Docker 网络，仅连接生产 MySQL 容器与临时 Redis；未继承生产应用网络或第三方凭据。生产只读 HTTPS 验收 **173/173**；生产镜像 `sha256:bb1da6efd391c159ff9c3030a1e18afefb02c3926795283078a62f968c287b98`，Git/Docker 回滚标签 `before-habit-tracker-v78`。Flyway **78/78** 且全部成功，五个容器 healthy，官网、后台登录页和 API 健康接口均返回 200。
