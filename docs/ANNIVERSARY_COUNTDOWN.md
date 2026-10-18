# 纪念日

## 定位

- 工具编码：`anniversary_countdown`
- 分类 / 模式：`LIFE` / `LOCAL`
- 排序：631
- 数据迁移：`V79__seed_anniversary_countdown_tool.sql`
- 分层：Compose → `AnniversaryViewModel` → `AnniversaryUseCases` → `AnniversaryRepository`

在本机记录生日、相识、家庭、工作和其他重要日子。支持单次、每月或每年重复，显示倒计时、下次日期、完整经过年数、未来三次日期及原始日期的农历和生肖。

## 开源参考

查阅日期：2026-10-18。

| 项目 | 固定版本与许可 | 实际核验源码 | 采用内容与适配差异 |
| --- | --- | --- | --- |
| [mark216tw/anniversary-countdown-android](https://github.com/mark216tw/anniversary-countdown-android) | [`8854b4ce15ead5fa897ac8d9f47f6e077295889a`](https://github.com/mark216tw/anniversary-countdown-android/commit/8854b4ce15ead5fa897ac8d9f47f6e077295889a)；MIT | [Anniversary.kt](https://github.com/mark216tw/anniversary-countdown-android/blob/8854b4ce15ead5fa897ac8d9f47f6e077295889a/app/src/main/java/com/example/anniversarycountdown/data/Anniversary.kt)、[AnniversaryFormatting.kt](https://github.com/mark216tw/anniversary-countdown-android/blob/8854b4ce15ead5fa897ac8d9f47f6e077295889a/app/src/main/java/com/example/anniversarycountdown/ui/AnniversaryFormatting.kt)、[AnniversaryFormattingTest.kt](https://github.com/mark216tw/anniversary-countdown-android/blob/8854b4ce15ead5fa897ac8d9f47f6e077295889a/app/src/test/java/com/example/anniversarycountdown/ui/AnniversaryFormattingTest.kt) | 核验了“日期型事件在当天仍算今天”、下次周年按今天或未来日期计算、过期单次事件置后、未来纪念日按最近日期排序、月底 31 日压到短月最后一天，以及 2 月 29 日在非闰年选择 2 月 28 日或 3 月 1 日的测试。本项目采用 2 月 28 日规则，用设备本地 epoch day 自行实现每月/每年重复、倒计时和未来三次日期；没有复制源码、资源或测试，也未加入该项目的固定时区、时间字段、提醒、通知和桌面小组件。 |
| [6tail/lunar-java](https://github.com/6tail/lunar-java) | [`0194eb4574f33ab056fe7cac62a9d8bf24272478`](https://github.com/6tail/lunar-java/commit/0194eb4574f33ab056fe7cac62a9d8bf24272478)；MIT | 项目现有的 `Solar.fromYmd(...).lunar` 集成 | 只读取农历月、日、闰月和生肖。农历用于原始日期说明，不把公历重复规则改成农历规则，也不声称可换算全部历史节令。 |

## 已实现功能

- 新建和编辑纪念日：名称、可选备注、原始日期、生日/关系/家庭/工作/其他分类、单次/每月/每年重复及置顶。
- 首页统计全部纪念日、今天、未来 7 天和未来 30 天的数量；今天仍计入倒计时而不是提前滚到下一年。
- 卡片显示原始日期、分类、重复规则、下次日期和剩余/已过去天数；单次且已过期的事件明确显示已过去。
- 详情显示完整经过年数、距上次周年天数、未来三次日期，以及原始公历日期、星期、农历月日、闰月和生肖。
- 搜索名称及备注；全部、即将到来、今天、已过去筛选；置顶优先、下次日期、最近创建、名称四种排序。
- 新建、编辑、置顶、详情和删除确认；搜索/筛选/排序与打开中的编辑/详情对象写入 `SavedStateHandle` 或 `rememberSaveable`。
- 页面恢复前台后每分钟重新读取设备日期；跨午夜、时区变化或从后台返回会刷新倒计时。
- 中文和英文资源、独立 24dp 线性图标、窄屏换行、深色主题、2 倍字体、软键盘避让和可滚动编辑/详情对话框。

## 输入、数据与边界

- Room 升至 v10，新增 `anniversary_event`，保存标题、备注、原始 epoch day、重复规则、分类、置顶状态和毫秒创建/更新时间；`MIGRATION_9_10` 只新增表和两个索引。
- 最多 500 个纪念日；新增使用单条 Room SQL 原子检查上限，避免并发请求同时通过。标题 80、备注 500 个 Unicode code point；日期限制为 1900-01-01 至 2100-12-31。
- `NONE` 只发生一次并在日期过后归入“已过去”；`MONTHLY` 和 `YEARLY` 从今天或未来找下一次，今天的日期仍留在今天。
- 每月重复在短月使用当月最后一天，例如 31 日事件在 2 月落到 28/29 日、4 月落到 30 日；下一次月份仍按原始日号恢复。
- 每年 2 月 29 日在非闰年固定按 2 月 28 日计算；未来三次日期和完整年数使用同一规则。
- 名称会 trim 并把连续空白折叠为一个空格；备注只 trim 首尾。保存期间锁定编辑器和操作；写入失败保留草稿并显示错误。
- 日期使用设备本地 `LocalDate`，不上传位置、日期、备注或生肖信息。纪念日只保存于 Android Room，不新增服务端业务表。
- 不提供系统通知、定时提醒、时间点、固定时区、桌面小组件、农历重复、跨设备同步、备份或导入导出。卸载应用或清除应用数据会删除本机记录。

## 验证范围

定向 JUnit 13 项覆盖标题/备注 code point 预算、日期范围、空白折叠、闰日和短月钳制、当天语义、月度/年度下一次日期、单次过期、未来三次日期、完整年数、筛选排序、统计，以及 ViewModel 的 SavedState、增改、重复提交拦截、上限、置顶、删除、日期刷新和存储失败。

2026-10-18 Android 16 模拟器实测：新建年度生日、月度重复和过去单次事件；当天/过去筛选、搜索、排序、置顶、编辑及删除取消/确认；详情显示农历、生肖、完整年数与未来三次日期。真实 Room v9→v10 升级后 `user_version=10`，原有 12 条最近使用记录保留。深色主题和 2 倍字体下验证窄屏换行；另修复了 2 倍字体加软键盘时编辑正文被压缩的问题，正文现在可滚动，取消/保存随内容滚动到达。

本地门禁：Android Debug/Release 各 **754/754**，111 个测试套件；`assembleDebug`、`assembleRelease` 与 `bundleRelease` 成功，Debug APK **24,726,812** 字节、Release APK **16,990,732** 字节、AAB **16,436,932** 字节；Debug/Release Lint 均 **0 错误 / 91 条既有警告**，纪念日源码、资源、测试与 schema 无 finding；Release artifact 门禁通过。服务端 **226/226**、V79 离线静态契约 **111/111**、同步 SQLite **30/30**，后台 `vue-tsc --noEmit` 与生产构建通过。

2026-10-18 发布验收：严格隔离候选 **283/283** 通过，使用一次性 Docker 网络，仅连接生产 MySQL 容器与临时 Redis；未继承生产应用网络或第三方凭据。生产只读 HTTPS 验收 **182/182**；生产镜像 `sha256:a127bf430d0915abb15e555fc7284dfb76ab87813d228f110405de01ee85f34f`，Git/Docker 回滚标签 `before-anniversary-v79`。Flyway **79/79** 且失败 **0**，最新记录为 `79 seed anniversary countdown tool`；五个容器 healthy，官网、后台登录页和 API 健康接口均返回 200。
