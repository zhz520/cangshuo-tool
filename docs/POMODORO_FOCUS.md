# 番茄专注

## 定位

- 工具编码：`pomodoro_focus`
- 分类 / 模式：`LIFE` / `LOCAL`
- 排序：632
- 数据迁移：`V80__seed_pomodoro_focus_tool.sql`
- Android 数据：Room v11 `pomodoro_session` + 本机 SharedPreferences 计时快照
- 分层：Compose → `PomodoroViewModel` → `PomodoroUseCases` → `PomodoroRepository`

用专注、短休息和长休息组成可配置循环。计时基于绝对截止时间，应用退到后台后由前台服务继续推进，并通过本地通知提供暂停、继续和跳过操作；完成与提前结束的专注保存在本机统计中。

## 开源参考

查阅日期：2026-10-18。

| 项目 | 固定版本与许可 | 实际核验源码 | 采用内容与适配差异 |
| --- | --- | --- | --- |
| [Arijit-05/Fomodoro](https://github.com/Arijit-05/Fomodoro) | [`dfb49db261781e1f2ad151235b6ef77a36e29ebc`](https://github.com/Arijit-05/Fomodoro/commit/dfb49db261781e1f2ad151235b6ef77a36e29ebc)；MIT | [TimerService.kt](https://github.com/Arijit-05/Fomodoro/blob/dfb49db261781e1f2ad151235b6ef77a36e29ebc/app/src/main/java/com/arijit/pomodoro/services/TimerService.kt)、[TimerFragment.kt](https://github.com/Arijit-05/Fomodoro/blob/dfb49db261781e1f2ad151235b6ef77a36e29ebc/app/src/main/java/com/arijit/pomodoro/fragments/TimerFragment.kt)、[StatsManager.kt](https://github.com/Arijit-05/Fomodoro/blob/dfb49db261781e1f2ad151235b6ef77a36e29ebc/app/src/main/java/com/arijit/pomodoro/utils/StatsManager.kt)、[TimerPreset.kt](https://github.com/Arijit-05/Fomodoro/blob/dfb49db261781e1f2ad151235b6ef77a36e29ebc/app/src/main/java/com/arijit/pomodoro/models/TimerPreset.kt) | 核验了低优先级常驻通知、专注/短休息/长休息切换、时钟服务、每日专注分钟与连续天数统计、计时预设的数据结构。本项目保留这些业务概念，但改用绝对截止时间而非每秒递减持久值，使用 Room 原子插入而不是 JSON SharedPreferences 保存历史，并增加前台服务通知操作、并发互斥、历史上限、可恢复暂停状态、自动切换开关和 2 倍字体布局。没有复制源码、资源或测试。 |

## 已实现功能

- 专注、短休息、长休息三阶段；默认经典 25/5/15、每四轮长休息，并提供深度 50/10/20 和轻量 15/3/10 预设。
- 自定义专注 1–120 分钟、短休息 1–60 分钟、长休息 1–180 分钟、长休息前 2–8 轮；专注结束和休息结束可分别设置自动开始。
- 开始、暂停、继续、跳过和确认重置；阶段未运行时可直接切换阶段。
- 可选专注名称，最多 60 个 Unicode 码点；名称随完成/提前结束记录进入历史。
- 大号倒数、阶段进度环、当前轮次、今日专注分钟/完成轮数、近 7 天分钟、当前连续与最长连续。
- 最近 30 条历史界面（数据库保留最近 2000 条），显示名称、日期、分钟、阶段、完成或提前结束；清空记录需确认。
- Android 前台服务与常驻通知；通知提供暂停/继续和跳过操作，完成时使用独立提醒通知。
- 截止时间跨进程恢复；计时只按真实时间计算，不依赖每秒回调是否准点。
- 中文/英文资源、独立工具与通知图标、深色主题、2 倍字体和窄屏自适应。

## 输入、数据与边界

- 计时快照保存在 `pomodoro_timer_v1` SharedPreferences：阶段、运行状态、计划秒数、剩余秒数、截止时间、本轮开始时间、已完成轮数与名称。
- Room v11 新增 `pomodoro_session`，保存名称、阶段、计划/实际秒数、完成状态、起止时间；以 `ended_at` 和 `phase` 建索引，事务内插入并裁剪到 2000 条。
- 运行中使用 `deadlineEpochMillis` 和当前时间计算剩余；暂停保存向上取整的剩余秒数。进程重启时先恢复快照，再由界面/服务 tick 结算过期阶段。
- 自动追赶最多结算 12 个阶段，防止长时间离线后一次创建无界历史；超过上限时停在当前周期起点。
- 专注统计只累计 `FOCUS` 阶段实际秒数；完成轮数只统计完整结束的专注。提前结束超过 60 秒的专注仍保留分钟，短休息/长休息不进入专注分钟。
- 连续天数按完整专注的本地日期计算：今天有记录时从今天计算，今天尚未专注但昨天有记录时保留昨日连续性。
- 设置修改在运行期间禁用；暂停或就绪时可以应用，并将当前阶段重置到新时长。历史清空不可恢复。
- 前台服务声明 `specialUse`，用于用户主动启动的倒计时；Android 13+ 首次开始前请求通知权限，拒绝后计时仍可用，但系统可能隐藏通知。
- 不提供云同步、账号、跨设备统计、系统闹钟、开机自动恢复、日历写入或第三方任务集成。

## 验证范围

定向 JUnit 14 项覆盖设置/标签预算、暂停取整、今天语义、四次专注后的长休息、自动切换、跳过和重置记录、统计与连续天数、历史追赶上限、手动切阶段，以及 ViewModel 的状态收集、重复点击保护、设置校验和名称/历史操作。

2026-10-18 Android 16 模拟器实测：Android 13+ 首次开始时请求通知权限；启动一分钟专注后常驻通知显示倒计时，退到桌面后继续更新；通知栏“暂停”可停止前台服务并保留剩余时间，返回页面显示暂停和继续；完成专注后自动进入短休息、显示完成通知、写入历史与统计；跳过休息回到下一轮专注；重置前显示确认，重置后前台服务停止。深色主题与 2 倍字体下阶段按钮、计时卡、名称和操作均可换行访问。另从真实 Room v10 数据库覆盖安装 v11，升级后 `user_version=11`、2 条最近使用记录保留、`pomodoro_session` 表及索引创建成功。

本地门禁：Android Debug/Release 各 **768/768**，113 个测试套件；`assembleDebug`、`assembleRelease` 与 `bundleRelease` 成功，Debug APK **25,138,121** 字节、Release APK **17,084,005** 字节、AAB **16,516,127** 字节；Debug/Release Lint 均 **0 错误 / 91 条既有警告**，番茄专注源码、资源、测试与 schema 无 finding；Release artifact 门禁通过。服务端 **226/226**、V80 离线静态契约 **118/118**，后台 `vue-tsc --noEmit` 与生产构建通过。

2026-10-18 发布验收：严格隔离候选 **289/289** 通过，使用一次性 Docker 网络，仅连接生产 MySQL 容器与临时 Redis；未继承生产应用网络或第三方凭据。生产只读 HTTPS 验收 **189/189**；生产镜像 `sha256:c9fa69f09a43ad801f7d9f70ede6c88bbaeb034b66e486c1b762a5148551d06c`，Git/Docker 回滚标签 `before-pomodoro-v80`。Flyway **80/80** 且失败 **0**，最新记录为 `80 seed pomodoro focus tool`；五个容器 healthy，官网、后台登录页和 API 健康接口均返回 200。
