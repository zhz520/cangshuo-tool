# 订阅管理

## 定位

- 工具编码：`subscription_manager`
- 分类 / 模式：`LIFE` / `LOCAL`
- 排序：634
- 数据迁移：`V82__seed_subscription_manager_tool.sql`
- Android 数据：Room v13 `subscription_item`
- 分层：Compose → `SubscriptionViewModel` → `SubscriptionUseCases` → `SubscriptionRepository`

在本机记录会员与订阅服务、服务商、分类、周期金额、支付方式、开始与下次续费日期。工具汇总启用中订阅的月均和年度支出，并提供自动续费展示、手动记录续费、暂停/恢复、搜索筛选排序和每日本地通知提醒。

## 开源参考

查阅日期：2026-10-18。

| 项目 | 固定版本与许可 | 实际核验源码 | 采用内容与适配差异 |
| --- | --- | --- | --- |
| [Grigoriym/Wallosmobile](https://github.com/Grigoriym/Wallosmobile) | [`9b8978fb2c5986d32a69df8a961a5e8862f969fc`](https://github.com/Grigoriym/Wallosmobile/commit/9b8978fb2c5986d32a69df8a961a5e8862f969fc)；Apache-2.0 | [UpcomingPaymentsCalculator.kt](https://github.com/Grigoriym/Wallosmobile/blob/9b8978fb2c5986d32a69df8a961a5e8862f969fc/feature/dashboard/domain/src/commonMain/kotlin/com/grappim/wallosmobile/feature/dashboard/domain/calculator/UpcomingPaymentsCalculator.kt)、[SubscriptionStatsCalculator.kt](https://github.com/Grigoriym/Wallosmobile/blob/9b8978fb2c5986d32a69df8a961a5e8862f969fc/feature/dashboard/domain/src/commonMain/kotlin/com/grappim/wallosmobile/feature/dashboard/domain/calculator/SubscriptionStatsCalculator.kt)、[Subscription.kt](https://github.com/Grigoriym/Wallosmobile/blob/9b8978fb2c5986d32a69df8a961a5e8862f969fc/feature/subscriptions/domain/src/commonMain/kotlin/com/grappim/wallosmobile/feature/subscriptions/domain/model/Subscription.kt) | 核验了周期、下次扣费、自动续费、提醒开关与提前天数的数据拆分，以及只统计启用项、把月费折算后计算年费的做法。本项目不连接 Wallos 服务端，不包含多币种和一次性订阅；金额改用整数分，自动续费日期只在本地即时计算展示，用户可手动“记录续费”持久化下一期。没有复制源码或资源。 |
| [Ike-li/subscription-charge-guardian](https://github.com/Ike-li/subscription-charge-guardian) | [`082ef5de06133dda7c229fb02eecc68b02b411c9`](https://github.com/Ike-li/subscription-charge-guardian/commit/082ef5de06133dda7c229fb02eecc68b02b411c9)；MIT | [Subscription.kt](https://github.com/Ike-li/subscription-charge-guardian/blob/082ef5de06133dda7c229fb02eecc68b02b411c9/app/src/main/java/com/example/data/Subscription.kt)、[SubscriptionRollForwardTest.kt](https://github.com/Ike-li/subscription-charge-guardian/blob/082ef5de06133dda7c229fb02eecc68b02b411c9/app/src/test/java/com/example/data/SubscriptionRollForwardTest.kt)、[ReminderScheduler.kt](https://github.com/Ike-li/subscription-charge-guardian/blob/082ef5de06133dda7c229fb02eecc68b02b411c9/app/src/main/java/com/example/worker/ReminderScheduler.kt)、[BillingReminderWorker.kt](https://github.com/Ike-li/subscription-charge-guardian/blob/082ef5de06133dda7c229fb02eecc68b02b411c9/app/src/main/java/com/example/worker/BillingReminderWorker.kt) | 核验了月末日期推进、跨多期顺延、关停自动续费后保留逾期、独立提醒提前量和每日本地检查。本项目额外采用“从原始日期按周期计数”避免 31 日连续顺延成 28 日，并覆盖季度、半年、年度和 1–3650 天自定义周期；提醒判断使用 `0..提前量` 区间，不要求恰好相等。未复制源码、资源或 WorkManager 依赖，改用无需精确闹钟权限的每日 `AlarmManager.setAndAllowWhileIdle`。 |

## 已实现功能

- 订阅资料：名称、服务商、八类分类、金额、周/月/季/半年/年/自定义周期、开始日期、下次续费日期、支付方式、备注、提醒开关、提前 0–30 天、自动续费开关和启用/暂停状态。
- 概览：启用中数量、月均支出、年度支出、7 天内续费、已逾期和已暂停。只统计启用中的订阅。
- 费用折算：周付按一年 52 期，月/季/半年/年按自然倍数，自定义周期按一年 365.2425 天估算；始终使用整数分，年度由周期金额直接计算，月均由年度除以 12 四舍五入。
- 自动续费：启用且自动续费的过期记录显示最近未来一期，不读取服务端；手动记录续费会把日期持久化推进到下一未来周期，月末日期始终从原锚点计算。暂停记录不推进、不计入支出。
- 搜索名称、服务商、支付方式和备注；筛选全部、订阅中、已暂停、7 天内续费；排序最近更新、下次续费、金额、名称。
- 卡片快捷记录续费、暂停/恢复、详情、编辑和确认删除；操作按条目名称提供 TalkBack 语义。
- 每日提醒：存在启用且开启提醒的应用内订阅时，在设备本地设置下一次 09:00 的 `RTC_WAKEUP` 检查；系统重启或应用升级后重新排定。检查会汇总即将到期和已逾期订阅，创建续费通知频道并发送一条摘要通知。
- Android 13+ 仅在存在提醒订阅且权限未授予时显示通知权限卡片；不申请精确闹钟权限。Android 休眠可能延后检查，页面内的“即将续费/已逾期”状态始终可用。
- 中英文、深色主题、2 倍字体、软键盘安全区域、窄屏换行和滚动位置适配。
- 加载、空数据、筛选无结果、保存中、字段校验、存储失败和 Snackbar 状态；连续同类型消息使用修订号避免去重丢失。

## 输入、数据与边界

- 名称和服务商必填，最多 80 个 Unicode 码点；支付方式最多 60；备注最多 500。
- 金额必填且大于 0，最大 `999999.99`，最多两位小数；使用人民币和整数分，不包含外币或换汇。
- 自定义周期为 1–3650 天；提醒提前量为 0–30 天。提醒开关和自动续费开关彼此独立。
- 日期范围 `1900-01-01..2200-12-31`；续费日不得早于开始日。原生日期选择器会限制可选范围。
- 最多 500 项。Room `@Transaction` 在同一事务中检查计数并插入，避免并发突破上限；更新不存在的记录会失败而不是静默成功。
- 已过期且自动续费的记录使用原日期作为锚点按周期计数计算最近未来一期；没有做跨时区账单时间或第三方支付平台确认。
- 日历月/季度/半年/年使用 `plusMonths`/`plusYears` 的自然月末钳制；自定义周期和每周使用固定天数。
- 搜索、筛选和排序写入 `SavedStateHandle`。页面每分钟检查设备本地日期，跨过午夜后刷新续费/逾期状态。
- 所有订阅和提醒设置只在应用私有 Room/SharedPreferences 中；不上传、不同步，不包含多币种、账单导入、支付平台 OAuth、云备份或桌面小组件。卸载或清除数据会删除全部记录。

## 验证范围

定向 JUnit 11 项覆盖 Unicode/金额/日期/周期预算、整数分费用折算、月末与跨多期推进、自动续费有效日期、提醒窗口边界、筛选/排序/统计、SavedState、提醒调度开关、创建编辑、失败草稿、条目上限、快速操作去重、删除和跨日刷新。

Android 16 模拟器验证新增订阅、金额与月/年汇总、到期徽标、通知权限申请、`dumpsys alarm` 中的每日闹钟、真实通知频道与摘要通知、记录续费推进日期、暂停/恢复、深色主题和 2 倍字体布局。另将保留 `calculator` 最近使用记录的完整 v12 数据库覆盖为 v13，确认 18 列、三个索引和账单锚点迁移成功。

本地门禁：Android Debug/Release 各 **789/789**，117 个测试套件；`assembleDebug`、`assembleRelease` 与 `bundleRelease` 成功，Debug APK **25,482,728** 字节、Release APK **17,275,433** 字节、AAB **16,702,293** 字节；Debug/Release Lint 均 **0 错误 / 91 条既有警告**，订阅管理源码、资源、测试与 schema 无 finding；Release artifact 门禁通过。服务端在 JDK 21 下 **226/226**、V82 离线静态契约 **152/152**，后台 `vue-tsc --noEmit` 与生产构建通过。

2026-10-18 发布验收：严格隔离候选 **323/323** 通过，使用一次性 Docker 网络、临时数据库/用户和临时 Redis，候选启动、V82、后台编辑保持、通知相关元数据、排行与 7/30/90 天统计均通过，结束后资源全部清理。生产只读 HTTPS **223/223**；生产镜像 `sha256:b1991f330085d95c951ac7ee89f9eb9b0536d7568aae37dc597969dfb4a421b0`，Git/Docker 回滚标签 `before-subscription-v82`。Flyway **82/82** 且失败 **0**，最新记录为 `82 seed subscription manager tool`；五个容器 healthy，官网、后台登录页和 API 健康接口均返回 200。
