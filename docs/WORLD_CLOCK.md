# 世界时间

## 定位

- 工具编码：`world_clock`
- 分类 / 模式：`LIFE` / `LOCAL`
- 排序：621
- 数据迁移：`V68__seed_world_clock_tool.sql`
- 分层：Compose → `WorldClockViewModel` → `WorldClockUseCases` → `WorldClockRepository`

工具读取设备墙钟、系统时区和 Android 自带 IANA 时区数据库。没有网络请求、登录要求或额外权限，也不保存城市、查询词或时间内容到服务端。

## 开源参考

查阅日期：2026-10-17。以下源码均按固定 commit 阅读；本轮没有复制第三方源码，只采用公开实现中可验证的时间语义和交互思路。

| 项目 | 固定版本与许可 | 已阅读的实际源码 | 采用内容与适配差异 |
| --- | --- | --- | --- |
| [depau/wearos-world-clock-tile](https://github.com/depau/wearos-world-clock-tile) | `5ba1be51a676fb69136f22847f4f18f681cecba9`；多数文件 Apache-2.0，`WorldClockTileService.kt`、`TileSettingsActivity.kt`、`TileSettingsViewModel.kt` 为 GPL-3.0 | [Timezone.kt](https://github.com/depau/wearos-world-clock-tile/blob/5ba1be51a676fb69136f22847f4f18f681cecba9/shared/src/main/java/gay/depau/worldclocktile/shared/utils/Timezone.kt#L11-L68)、[DAO.kt](https://github.com/depau/wearos-world-clock-tile/blob/5ba1be51a676fb69136f22847f4f18f681cecba9/shared/src/main/java/gay/depau/worldclocktile/shared/tzdb/DAO.kt#L68-L92)、[TimezoneSelectionDialog.kt](https://github.com/depau/wearos-world-clock-tile/blob/5ba1be51a676fb69136f22847f4f18f681cecba9/mobile/src/main/java/gay/depau/worldclock/mobile/composables/TimezoneSelectionDialog.kt#L44-L123)、[WorldClockTileService.kt](https://github.com/depau/wearos-world-clock-tile/blob/5ba1be51a676fb69136f22847f4f18f681cecba9/wear/src/main/java/gay/depau/worldclocktile/WorldClockTileService.kt#L68-L220) | 参考 UTC 再转目标时区、日期前后标记、城市/时区/别名统一搜索和 12/24 小时制。未复制 GPL 文件；未采用整数分钟时差、固定“昨天/今天/明天”范围或预生成 60 分钟 Tile 的实现。本项目直接从 `Instant.atZone()` 和 `ChronoUnit.DAYS` 得到日历日差，并允许国际日期变更线造成的 `-2 / +2` 天差。 |
| [PranshulGG/ClockMaster](https://github.com/PranshulGG/ClockMaster) | `4fdfc018dc6616c3c204ba2e95d961f50582bea4`；Apache-2.0 | [world_clock_screen.kt](https://github.com/PranshulGG/ClockMaster/blob/4fdfc018dc6616c3c204ba2e95d961f50582bea4/app/src/main/java/com/pranshulgg/clockmaster/screens/world_clock_screen.kt#L97-L167)、[timezoneSearchPage.kt](https://github.com/PranshulGG/ClockMaster/blob/4fdfc018dc6616c3c204ba2e95d961f50582bea4/app/src/main/java/com/pranshulgg/clockmaster/screens/timezoneSearchPage.kt#L69-L244)、[timezoneViewModel.kt](https://github.com/PranshulGG/ClockMaster/blob/4fdfc018dc6616c3c204ba2e95d961f50582bea4/app/src/main/java/com/pranshulgg/clockmaster/models/timezoneViewModel.kt#L14-L26) | 采用 `ZoneId.getAvailableZoneIds()` 全量离线目录，以及用 `1000 - now % 1000` 对齐到下一个秒边界刷新。适配为 `LifecycleStartEffect` 驱动的 ViewModel ticker，页面不可见时取消；拒绝其把城市字符串当 IANA 主键、保存过期 offset、伪造本地加载延迟和破坏性数据库迁移的做法。 |
| [jclllycf/best-mates-world-clock](https://github.com/jclllycf/best-mates-world-clock) | `0291121717cac5423e0aa9848717e135ed1d985c`；代码 Apache-2.0，GeoNames 数据 CC BY 4.0 | [CityCatalog.kt](https://github.com/jclllycf/best-mates-world-clock/blob/0291121717cac5423e0aa9848717e135ed1d985c/app/src/main/java/com/bestmates/worldclock/data/CityCatalog.kt#L21-L60)、[Scene.kt](https://github.com/jclllycf/best-mates-world-clock/blob/0291121717cac5423e0aa9848717e135ed1d985c/app/src/main/java/com/bestmates/worldclock/scene/Scene.kt#L88-L142)、[ConfigStore.kt](https://github.com/jclllycf/best-mates-world-clock/blob/0291121717cac5423e0aa9848717e135ed1d985c/app/src/main/java/com/bestmates/worldclock/data/ConfigStore.kt#L25-L49)、[ConfigCodecTest.kt](https://github.com/jclllycf/best-mates-world-clock/blob/0291121717cac5423e0aa9848717e135ed1d985c/app/src/test/java/com/bestmates/worldclock/data/ConfigCodecTest.kt#L17-L129) | 采用“英文名、本地名、别名统一归一化后搜索”和“非法/重复城市可恢复”的原则，补充少量中文城市别名与常用时区优先。没有引入其 GeoNames 数据库、在线搜索或桌面组件逻辑；本项目范围明确为页面内状态恢复，不把 `SavedStateHandle` 描述成跨安装持久化。 |

另行核验但未采用：

| 项目 | 固定版本与许可 | 排除原因 |
| --- | --- | --- |
| [DarkMinstrel/WorldClock](https://github.com/DarkMinstrel/WorldClock) | `7203ad86accb21f35648711d639515be69ee4826`；未找到 LICENSE | 固定 10 个城市的视觉演示，没有动态检索、UTC 偏移、DST 或状态恢复；缺少许可不能复用。 |
| [edgarogh/ElevenFortySix](https://github.com/edgarogh/ElevenFortySix) | `ac719a5e3c285b13eb6ff2ccbdfc1e9a1be46077`；MIT | 使用固定 12 个偏移区段和手工冬夏设置，会压平 30/45 分钟偏移和真实 IANA DST 规则；状态恢复还存在冬季字段重复绑定的问题。 |

## 已实现功能

- 设备时区始终显示，包含本地时间、日期、星期、UTC 偏移和 DST 状态。
- 最多添加 23 个其他城市，含设备时区共 24 个；默认提供上海、伦敦和纽约，首次启动会自动排除已与设备时区重复的条目。
- 时区目录来自 `ZoneId.getAvailableZoneIds()`，按 IANA ID 校验；常用城市优先，支持中文/英文别名、地区名、IANA ID、大小写、空格、下划线和全角字符归一化搜索。
- 支持自定义搜索、搜索计数、空结果、已添加标记、重复/非法/上限错误和显式关闭错误提示。
- 支持 12/24 小时制；12 小时制正确处理午夜 `12:00 AM`、正午 `12:00 PM` 和本地化 AM/PM。
- 每行显示日历日差，范围不只限于前一天/后一天；国际日期变更线可正确显示两天差。
- UTC 偏移使用 `ZoneOffset.totalSeconds`，正确显示 `UTC+05:45`、`UTC-03:30` 等非整点偏移。
- DST 分为固定偏移、标准时间、夏令时三种状态，并显示下一次偏移变更的本地时间与变更后的偏移。
- 页面可见时对齐整秒刷新；离开页面立即取消 ticker。设备时钟与系统时区更新后，下一次刷新重新计算。
- 已添加城市、12/24 小时制和搜索词写入 `SavedStateHandle`，旋转或页面恢复后保留；用户删空其他城市后不会再次注入默认城市。
- 复制/分享完整摘要，使用共享 `SystemReportActions`；中英文资源完整，不使用硬编码界面文案。

## 输入、输出与边界

- 时区输入只接受系统 `ZoneId.of()` 可识别的 IANA ID；`TimeZone.getTimeZone()` 会静默回退 GMT，因此没有用于存在性校验。
- `WorldClockUseCases.readings()` 对每个时区单独转换和读取规则；单个异常不会伪造成本地时间，而是丢弃该项并进入 `READ_FAILED`。
- 时区列表最多 24 个（1 个设备时区 + 23 个其他城市）；搜索词最多 80 个 UTF-16 单元。
- 重复 IANA ID、设备时区和已有城市都视为重复；常用城市集合只是排序提示，不改变系统 tzdb。
- 日期格式、星期和 AM/PM 跟随当前 Resources locale；时区规则和可用 ID 跟随设备 Android tzdb 版本。
- 页面不显示人为 loading，因为本地目录和读取是同步完成的；空搜索、无匹配、非法/重复/上限和读取失败分别反馈。
- 不同 Android 版本的 tzdb 可能对历史规则不同；本工具以当前设备规则为准，不做在线时间同步。

## 当前不支持

- 桌面 Widget、常驻通知、闹钟、跨设备同步和在线 NTP 校时。
- 用户自建城市、编辑显示名称、拖拽排序或多套城市分组。
- 跨多次主动退出应用的个人城市持久化；当前恢复范围是页面/进程状态，不是账户数据。
- 秒表级帧动画、表盘样式或背景壁纸。世界时间需要精确时间语义，装饰动画不作为功能。

## 分层与恢复

`LocalWorldClockRepository` 注入 `Clock`、系统时区提供器和可用时区集合，默认实现读取系统时间与 `ZoneId`。`WorldClockUseCases` 负责时区目录、搜索和读数转换，`WorldClockPolicy` 负责规范化、上限、编解码、偏移与 DST；ViewModel 只协调当前瞬间、选择和 SavedState，UI 不访问 `ZoneId`、系统时钟或网络。

Ticker 在 `LifecycleStartEffect` 的 START 期间运行，STOP 时取消。刷新延迟按 `floorMod(epochMillis, 1000)` 计算，而非从进入页面起固定等待一秒，因此显示秒数与系统时钟边界对齐。所有读数每次刷新都重新计算 offset 和 DST，不缓存可能过期的偏移。

## 验证范围

本轮新增定向测试覆盖：

- 上海/伦敦/纽约的 UTC、日期日差、夏令时和下一次偏移变更。
- 纽约 2026 年 3 月/11 月 DST 切换前后瞬间的标准时间与夏令时状态。
- 午夜/正午的 12 小时制、纽约标准/夏令时、日期线 `-2` 天差和非整点偏移。
- 中文/英文别名、单汉字、多关键词、大小写/下划线搜索、空结果和非法 ID。
- SavedState 恢复、默认城市去重、时区上限、重复/非法错误、查询长度和 12/24 小时制。
- ticker 整秒对齐、可见时刷新，以及停止后不再更新。

最终本地门禁：Android **604/604** 测试、`assembleDebug` 成功、Lint **0 错误 / 80 既有警告**，新增源码与资源没有 Lint finding；服务端 **226/226**。APK 为 **23,411,408** 字节。

Android 16 模拟器（1080×2424、中文界面）实际验证：

- 搜索 `world` 能打开“世界时间”，默认设备时区与上海、伦敦、纽约同时显示，日期、星期、UTC 偏移和 DST 状态一致。
- 12 小时制切换后显示“上午/下午”，伦敦与纽约的下一次偏移变更分别显示为 2026-10-25 与 2026-11-01。
- 在 635 个时区中搜索 `tokyo` 得到唯一结果；添加后计数从 3/23 变为 4/23，“完成”关闭弹窗，返回首页再打开仍保留东京和 12 小时制。
- 删除东京后列表恢复为上海、伦敦、纽约；复制与分享按钮和本地说明可见。

未执行或未声称通过：真机、TalkBack、极端字体缩放、跨主动卸载持久化、实际分享接收方和桌面 Widget。服务端生产候选与 HTTPS 验收结果在部署后补记，不会把本地模拟器结果写成已上线。
