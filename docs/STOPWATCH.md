# 秒表

`stopwatch`，LIFE / LOCAL，排序 620。原生 Compose → StopwatchViewModel → StopwatchUseCases → AndroidStopwatchRepository（SystemClock.elapsedRealtime）；无权限、无网络、无上传。

## 具体开源参考

查阅日期：2026-10-12。以下源码已实际读取；没有复制代码或引入新依赖。

| 来源 | 读取内容与适配差异 |
| --- | --- |
| [JustDeax/ComposeStopwatch StopwatchViewModel.kt](https://github.com/JustDeax/ComposeStopwatch/blob/b1ae176242965694c901833aa343c289df3ef7c9/app/src/main/java/com/justdeax/composeStopwatch/stopwatch/StopwatchViewModel.kt)、[TimeFormat.kt](https://github.com/JustDeax/ComposeStopwatch/blob/b1ae176242965694c901833aa343c289df3ef7c9/app/src/main/java/com/justdeax/composeStopwatch/util/TimeFormat.kt)，Apache-2.0 | 实际读取「暂停时累计 + 恢复时记录新起点」的计时模型、毫秒截断成两位小数的显示格式、以及从持久化状态恢复运行中会话的流程。借鉴恢复与显示思路；本项目改用 SystemClock.elapsedRealtime（单调时钟）而不是 System.currentTimeMillis，状态放进 SavedStateHandle，不含前台服务、通知、震动或主题切换。 |
| [bostwick/android-stopwatch models.kt](https://github.com/bostwick/android-stopwatch/blob/94735805b22592f077a40b5578d661983a57b42a/stopwatch-core/src/main/java/com/danielbostwick/stopwatch/core/model/models.kt)，BSD-3-Clause | 读取状态枚举（PAUSED/STARTED）+ startedAt + offset 的显式状态机，以及每个计次记录累计时间的数据结构。本项目自行实现为不可变 StopwatchSession/StopwatchLap，单圈时长由相邻总时间相减得出，不引入 Joda-Time 与 MVP/服务层。 |
| [Android SystemClock 官方文档](https://developer.android.com/reference/android/os/SystemClock) | 核实 elapsedRealtime 自系统启动起计时、包含深睡眠、保证单调，适合区间计时。据此选择该时钟，并说明设备重启会让单调时钟归零（恢复时按当前时间重新起算当前段）。 |

## 功能与边界

- 开始 / 暂停 / 继续 / 重置；显示 `MM:SS.CC`，满一小时切换为 `H:MM:SS.CC`，上限 99:59:59.99，超限只影响显示不溢出布局。
- 计时只在运行中可用；每次记次生成序号、单圈与总时间，最多保留 500 条，达到上限后明确提示重置。暂停状态点「记次」提示只能在计时中添加，不产生假记录。
- 统计：总时间、计次数量、最快 / 最慢单圈（至少 2 条才有意义）、平均单圈（按保留记录计算）。列表默认展示最近 20 条，可展开全部；最快 / 最慢有文字标记，不依赖颜色。
- 重置在存在计次时先确认；确认文案说明不可恢复。重置只清计时与计次，保留「保持常亮」「CSV / 摘要」偏好。
- 「计时中保持屏幕常亮」默认开启，关闭后立即解除；离开页面时恢复系统默认。
- 复制与分享：统计摘要（状态 + 全部统计）或计次 CSV（`lap,split_ms,total_ms`，最多 500 行，CRLF）；CSV 无用户文本，不需要转义处理。
- 中文 / 英语资源对齐；复用主题令牌与共享组件；数字使用 Locale.US 保证等宽字体下稳定显示。

## 并发、恢复与资源

- 计时值全部由单调时钟现算：暂停把已流逝时间累加进 accumulatedMs，继续记录新的 startedAtMs；UI 每 33ms 刷新一次显示，不把 tick 累加当作时间来源，因此掉帧或后台都不会丢时间。
- 回到前台按单调时钟补算（模拟器与单元测试均覆盖）；切后台只停止刷新，不改变会话。
- 运行中、累计毫秒、起点与计次编码进 SavedStateHandle（`序号,单圈,总`），Activity 重建 / 进程被系统回收后仍可恢复；编码解析拒绝编号不连续、单圈 ≤ 0、总时间不递增或与差值不符的条目。
- 若保存的起点大于当前单调时钟（例如期间设备重启），恢复时把当前时间作为新段起点，避免出现无法解释的巨大时长；已在文档中说明该段不计入。
- 进程退出后不保留记录；本版本没有前台服务、常驻通知或后台闹钟，到期不会提醒。历史与统计不上传、不写日志。

当前不支持：后台通知提醒、秒表结束后的系统闹钟、导出文件写入（只走复制 / 分享）、跨设备同步、计次备注、多组并行计时。

## 前后端与验证

V53 只新增工具目录 seed（LIFE / LOCAL / ENABLED、排序 620、requires_login=0），复用现有管理端与 `/tools/stopwatch/usage` 统计；不新增计时或上传接口，种子重放保留管理员编辑与软删除。

2026-10-13 Android Debug 单元测试 421/421 通过（本工具新增 21 项：领域 11、ViewModel 10），覆盖显示格式与钳制、暂停/恢复累加、重复开始/暂停、单调时钟回退、重复记次、计次拆分与上限、统计阈值、CSV 结构、SavedState 编解码健壮性、恢复/重启钳制、前台/后台刷新、偏好保留。APK 构建与 Lint 通过（0 错误 / 77 条既有警告），新增文件无发现。测试结束前清理 ViewModel 的持续 ticker，防止测试时钟无限推进。

Android 16 模拟器实际验收：开始、三次记次、暂停/继续、切后台后补算、最快/最慢标记、CSV 复制、取消重置/确认清空、1.5 倍字号和深色；截图已人工查看，位于 `android/build/calc-emulator/stopwatch-{paused,laps,large-font,dark}.png`。真机安装、TalkBack 和 500 条记录设备性能未验证。

V53 隔离后端/目录/统计验收 98/98；线上只读 HTTPS 验收 9/9，四个生产容器健康。生产 API 镜像 `sha256:d6e1e8afd8ecb136854993d86295057cbbb40fb41ad13f2a5ef0703427801d77`，保留 `before-stopwatch-v53` 回滚镜像。未向生产统计写入测试打开记录。
