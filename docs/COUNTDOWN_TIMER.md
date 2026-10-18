# 计时器

## 定位

- 工具编码：`countdown_timer`
- 分类 / 模式：`LIFE` / `LOCAL`
- 排序：637
- 数据迁移：`V85__seed_countdown_timer_tool.sql`
- 分层：Compose → `TimerViewModel` → `CountdownUseCases` → `CountdownRepository` → `specialUse` 前台计时服务

提供最长 23:59:59 的本机倒计时，支持快捷时长、自定义时分秒、名称、暂停/继续、加 1 分钟、重置、结束提醒和后台通知操作。倒计时使用单调时钟和持久化截止点；不联网、不申请精确闹钟权限，也不写入 Room 或服务端业务数据。

## 开源参考

查阅日期：2026-10-18。只核验实际源码和测试，没有复制源码或资源。

| 项目 | 固定版本与许可 | 实际核验源码 | 采用内容与适配差异 |
| --- | --- | --- | --- |
| [ashpakouski/QuickTimer](https://github.com/ashpakouski/QuickTimer) | [`2eea3ab03766633df5b0944f67dcb37b5b642264`](https://github.com/ashpakouski/QuickTimer/commit/2eea3ab03766633df5b0944f67dcb37b5b642264)；MIT | [AndroidTimerClock.kt](https://github.com/ashpakouski/QuickTimer/blob/2eea3ab03766633df5b0944f67dcb37b5b642264/timer/android/src/main/java/com/shpak/timer/android/AndroidTimerClock.kt)、[TimerReducer.kt](https://github.com/ashpakouski/QuickTimer/blob/2eea3ab03766633df5b0944f67dcb37b5b642264/timer/core/src/main/java/com/shpak/timer/core/TimerReducer.kt)、[TimerCountdown.kt](https://github.com/ashpakouski/QuickTimer/blob/2eea3ab03766633df5b0944f67dcb37b5b642264/timer/core/src/main/java/com/shpak/timer/core/TimerCountdown.kt)、[TimerService.kt](https://github.com/ashpakouski/QuickTimer/blob/2eea3ab03766633df5b0944f67dcb37b5b642264/app/src/main/java/com/shpak/quicktimer/presentation/TimerService.kt)、[TimerReducerTest.kt](https://github.com/ashpakouski/QuickTimer/blob/2eea3ab03766633df5b0944f67dcb37b5b642264/timer/core/src/test/java/com/shpak/timer/core/TimerReducerTest.kt) | 采用 `IDLE/RUNNING/PAUSED/FINISHED` 状态机、单调时钟、暂停冻结剩余时间、独立完成通知和 `startForeground()` 前置。参考其通知暂停/继续/加时/停止操作；本项目通知只提供暂停、继续和加时，重置仅在应用内确认后执行，避免从通知直接清除计时。项目增加了 SharedPreferences 截止点持久化、重启后的墙钟恢复和 WAKE_LOCK；使用本项目的 `specialUse` 前台服务类型，不照搬 `dataSync` 或内存状态存储。 |
| [dubrowgn/micro-timer](https://github.com/dubrowgn/micro-timer) | [`9d05e0cbddb737706377311e27560e6e6905d2cb`](https://github.com/dubrowgn/micro-timer/commit/9d05e0cbddb737706377311e27560e6e6905d2cb)；MIT | [Alarm.kt](https://github.com/dubrowgn/micro-timer/blob/9d05e0cbddb737706377311e27560e6e6905d2cb/app/src/main/java/dubrowgn/microtimer/db/Alarm.kt)、[MainActivity.kt](https://github.com/dubrowgn/micro-timer/blob/9d05e0cbddb737706377311e27560e6e6905d2cb/app/src/main/java/dubrowgn/microtimer/MainActivity.kt)、[AlarmReceiver.kt](https://github.com/dubrowgn/micro-timer/blob/9d05e0cbddb737706377311e27560e6e6905d2cb/app/src/main/java/dubrowgn/microtimer/AlarmReceiver.kt) | 参考其持久化绝对截止时间、使用 elapsedRealtime 计算剩余和整秒向上取整。没有采用全屏闹钟、持续振动或精确闹钟依赖；本项目以可操作的前台服务通知为主，禁用提醒权限时仍保留应用内状态。 |

## 已实现功能

- 1、3、5、10、15、25、30、45、60 分钟快捷时长；小时/分钟/秒自定义输入，范围 00:00:01–23:59:59。
- 可选名称（最多 60 个 Unicode 码点）、开始、暂停、继续、重置和加 1 分钟。
- 大号进度环和倒计时，显示预计结束时间；计时期间可选择保持屏幕常亮。
- `specialUse` 前台计时服务与低重要度常驻通知，通知支持暂停、继续和加 1 分钟；重置在应用内确认。
- 独立高重要度完成通知，使用系统默认声音与振动；不打开全屏页面。
- Android 13+ 通知权限按需申请，拒绝返回只影响通知，不阻止应用内计时。
- 使用 `elapsedRealtime` 截止点计算剩余；暂停冻结剩余时间，继续重新生成截止点。
- 进程重建使用持久化状态和同一开机周期内的单调截止点；设备重启后改用墙钟截止点。已过期状态立即变为完成，不按原时长重开。
- 前台计时持有 `PARTIAL_WAKE_LOCK`，暂停、完成、停止或服务销毁时立即释放；没有精确闹钟权限。
- 中英文、深色主题、系统大字体、窄屏、软键盘安全区域、无障碍进度描述和 Snackbar 状态。

## 输入、状态与边界

- 时长必须为 1 秒至 23:59:59；输入空白、非数字、分秒超出 59 或全零时明确报错。名称最多 60 个码点，超限不截断。
- 状态类型固定为 `IDLE / RUNNING / PAUSED / FINISHED`；运行时剩余值只由截止点计算，不使用递减计数作为事实来源。
- 暂停会冻结剩余时间并清除截止点；继续从当前剩余时间重新计算；重置保留当前总时长但回到空闲状态。
- 加 1 分钟同时延长总时长、运行截止点或暂停剩余值；只在运行/暂停状态可用。
- 前台服务使用 `specialUse`，在 `onStartCommand` 开头立即 `startForeground()`，返回 `START_STICKY`；`onStartCommand(null)` 从持久化状态继续或恢复。
- 只有运行、暂停或待发完成提醒的状态才进入前台；重置通过 `stopService()` 结束服务，不构造会再次调用 `startForeground()` 的停止命令，避免应用退到后台后触发 `ForegroundServiceStartNotAllowedException`。
- 服务实例只在首次有效命令时注册前台，后续命令更新同一通知；暂停、继续和加时由页面或通知入口单独同步服务，仓储状态更新不再嵌套触发服务启动。
- 通知权限被拒时，前台服务仍运行，任务管理器可见；应用页面实时显示剩余。强制停止应用会终止计时。
- 不使用 `AlarmManager`/`SCHEDULE_EXACT_ALARM`，设备休眠或厂商省电策略下完成提醒仍可能延迟；不宣称安全关键或医疗用途。
- 计时状态仅存应用私有 SharedPreferences；不新增 Room 表、业务 API、账号数据或使用记录。

## 界面与可访问性

- 页面顶部使用状态徽标、圆形进度和等宽格式时钟；空闲时显示快捷时长与自定义输入，运行时聚焦控制和加时。
- 开始时使用共享按钮、主题色、细边框和无障碍进度描述；结束/重置操作有明确反馈，重置需要确认。
- 通知使用 `CATEGORY_PROGRESS`，完成提醒使用 `CATEGORY_ALARM`；通知操作使用不可变 PendingIntent。
- 触控区域复用共享 48dp 按钮，内容使用最大表单宽度、`imePadding()` 和纵向滚动；深色与 2 倍字体使用主题令牌。

## 当前不支持

- 多个并行计时器、间隔重复、工作/休息自动切换、锁屏/桌面小组件、倒计时历史、云端同步、导出或精确闹钟。
- 自定义铃声、持续闹铃或全屏唤醒；完成通知遵循系统通知权限与勿扰设置。

## 验证范围

定向 JUnit 覆盖时长与名称边界、单调开始/暂停/继续、加时、完成清零、前台服务状态策略、纯时钟格式、ViewModel 委派、输入错误，以及继续/加时后的服务恢复请求。

2026-10-18 本地验收：

- Android Debug/Release 各 **814/814**（123 suites），两种构建 Lint **0 错误/91 条既有警告**，计时器文件无 finding；Debug APK **26,686,106** 字节，Release APK **17,503,073** 字节，AAB **16,899,422** 字节，Release artifact 门禁通过（HTTPS、权限、备份、16 KB ZIP 对齐和 8 条权限集合）。
- 服务端 JDK 21 **226/226**，静态契约 **109/109**，后台 `vue-tsc --noEmit` 与 Vite 生产构建通过。
- Android 16 模拟器验证通知权限拒绝、重新授权、后台计时、通知栏暂停/继续/加时、重置确认、进程被杀后的服务重启和状态追赶；修复后的受控 5 秒计时在 `10:29:36.348` 获取 `PARTIAL_WAKE_LOCK`，在完成时 `10:29:41.299` 释放，状态持久化为 `FINISHED`、`completion_notified=true`，通知栏显示带自定义名称的 “Countdown finished” 完成提醒。重置不再产生重复 FGS 启动，`ForegroundServiceStartNotAllowedException` 和崩溃日志均未出现。
- 截止即完成规则覆盖暂停、继续和加时竞态；页面或服务任一先观察到截止时都走同一个完成提醒入口，最大 23:59:59 后不能继续增加时长，墙钟回拨也不会恢复出超过配置总时长的剩余时间。
- 深色主题、2 倍字体和 720×1600 窄屏覆盖了标题/状态换行、全宽时分秒输入、快捷按钮、名称和设置区；真机 TalkBack 与厂商省电策略下的完成提醒延迟仍待后续设备验收。

2026-10-18 发布验收：严格隔离候选 **280/280** 通过，使用一次性数据库、用户和临时网络，V85、目录元数据、后台编辑保持、使用量、排行及 7/30/90 天统计均通过，结束后资源全部清理。生产只读 HTTPS **180/180**；生产 API 镜像 `sha256:f867f8beaf53f31bfda829501e0cb0564137f17e0326afee1c6d5596a4b21a6b`，Git/Docker 回滚标签 `before-countdown-v85`。Flyway **85/85** 且失败 0，五个容器 healthy，官网、后台登录页和 API 健康接口均返回 200。GitHub CI `39119507915` 与 Release `39119507829` 均成功。
