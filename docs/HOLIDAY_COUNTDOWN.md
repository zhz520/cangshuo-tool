# 节日倒数

## 定位

- 工具编码：`holiday_countdown`
- 分类 / 模式：`LIFE` / `LOCAL`
- 排序：622
- 数据迁移：`V69__seed_holiday_countdown_tool.sql`
- 分层：Compose → `HolidayViewModel` → `BuildHolidayCountdownUseCase` → `HolidayRepository`

工具内置 22 个公历、农历和节气节日，显示下一次日期、星期、农历日期、未来三次发生日与实时倒计时。所有计算在设备本地完成，没有网络、登录或额外权限。

## 开源参考

查阅日期：2026-10-17。

| 项目 | 固定版本与许可 | 实际核验源码 | 采用内容与适配差异 |
| --- | --- | --- | --- |
| [6tail/lunar-java](https://github.com/6tail/lunar-java) | `0194eb4574f33ab056fe7cac62a9d8bf24272478`；MIT | [Lunar.java](https://github.com/6tail/lunar-java/blob/0194eb4574f33ab056fe7cac62a9d8bf24272478/src/main/java/com/nlf/calendar/Lunar.java#L165-L189)、[LunarYear.java](https://github.com/6tail/lunar-java/blob/0194eb4574f33ab056fe7cac62a9d8bf24272478/src/main/java/com/nlf/calendar/LunarYear.java)、[LunarMonth.java](https://github.com/6tail/lunar-java/blob/0194eb4574f33ab056fe7cac62a9d8bf24272478/src/main/java/com/nlf/calendar/LunarMonth.java#L80-L91)、[LunarTest.java](https://github.com/6tail/lunar-java/blob/0194eb4574f33ab056fe7cac62a9d8bf24272478/src/test/java/test/LunarTest.java#L221-L230) | 使用 Maven 依赖 `cn.6tail:lunar:1.7.7` 做公历、农历和节气转换。普通农历节日使用正数月；非法农历日期由库抛出异常，不静默改成另一日期。清明通过 `jieQiTable` 获取，不写死 4 月 4/5 日。许可证随 APK 打包为 `assets/licenses/lunar-java.txt`。 |
| [mark216tw/anniversary-countdown-android](https://github.com/mark216tw/anniversary-countdown-android) | `8854b4ce15ead5fa897ac8d9f47f6e077295889a`；MIT | [Anniversary.kt](https://github.com/mark216tw/anniversary-countdown-android/blob/8854b4ce15ead5fa897ac8d9f47f6e077295889a/app/src/main/java/com/example/anniversarycountdown/data/Anniversary.kt#L74-L170)、[AnniversaryFormatting.kt](https://github.com/mark216tw/anniversary-countdown-android/blob/8854b4ce15ead5fa897ac8d9f47f6e077295889a/app/src/main/java/com/example/anniversarycountdown/ui/AnniversaryFormatting.kt#L31-L42)、[测试](https://github.com/mark216tw/anniversary-countdown-android/blob/8854b4ce15ead5fa897ac8d9f47f6e077295889a/app/src/test/java/com/example/anniversarycountdown/ui/AnniversaryFormattingTest.kt#L188-L200) | 采用“当天仍算今天、当天午夜后不立即滚到下一年”的年重复语义，以及按下一次日期排序的交互原则。本项目只倒数内置节日，不引入个人纪念日、月重复、固定时区选择或桌面组件。 |
| [NateScarlet/holiday-cn](https://github.com/NateScarlet/holiday-cn) | `62a7973b914864416b5ecabdd2c300cd433af6b8`；MIT | [schema.json](https://github.com/NateScarlet/holiday-cn/blob/62a7973b914864416b5ecabdd2c300cd433af6b8/schema.json#L1-L30)、[2026.json](https://github.com/NateScarlet/holiday-cn/blob/62a7973b914864416b5ecabdd2c300cd433af6b8/2026.json)、[2027.json](https://github.com/NateScarlet/holiday-cn/blob/62a7973b914864416b5ecabdd2c300cd433af6b8/2027.json#L1-L7) | 仅用于确认“官方休息日/调休”应独立建模，本轮**没有导入该数据**。固定 commit 中 2027 年记录为空，不能据此推算 2027 年假期。工具不展示、估算或冒称官方放假与调休安排。 |

## 节日范围

传统节日（11）：春节、元宵节、龙头节、清明节、端午节、七夕节、中元节、中秋节、重阳节、腊八节、小年。

公历节日（11）：元旦、情人节、妇女节、劳动节、儿童节、教师节、国庆节、万圣夜、双十一、平安夜、圣诞节。

每个节日保留未来三次候选并展示首次为“下一个节日”；选择任一节日后，顶部倒计时和“未来三次”同步切换。

## 日期算法与边界

- 公历固定节日直接枚举当前公历年及后续年份，取不早于今天的日期。
- 农历固定节日枚举当前公历年附近多个农历年，调用 `Lunar.fromYmd(year, positiveMonth, day)` 后取 `solar`。例如农历腊八可能落在下一公历年。
- 普通节日只使用正数月。闰月不会产生第二个端午或中秋；2028 年闰五月已用单元测试确认普通五月和闰五月会分别显示。
- 清明来自 `Lunar.jieQiTable["清明"]`，节气日期不写死。
- 目标日期等于今天时保留“今天”，倒计时显示到北京时间的下一个午夜，不把今天立即跳到下一年。
- 节日日期与倒计时固定使用北京时间 `Asia/Shanghai`，避免非东八区设备在日期边界把中国节日提前或延后一天。
- 只显示日期时使用 `LocalDate` / `ChronoUnit.DAYS`；精确倒计时使用目标日期的 `atStartOfDay(zone)`，正确处理按时区规则变化的午夜。
- 库只提供节日自然日期。国务院公布的法定休息日、调休上班日可能包含不连续日期，本工具不推算，也不把普通周末自动推导为官方假期。
- 节日数据是内置白名单，不接受任意日期或远程可执行配置。

## 功能与界面

- 顶部显示当前选中的下一个节日、公历日期、星期、农历日期和秒级倒计时。
- 支持“全部 / 传统节日 / 公历节日”筛选；切换筛选后若当前选择不在筛选内，自动选择该组最近节日。
- 列表按下一次发生日排序，显示“今天 / 明天 / N 天后”、农历日期和节日类型。
- 点击列表节日切换主倒计时；过滤器和选择保存到 `SavedStateHandle`。
- 每秒对齐系统秒边界刷新；离开页面取消 ticker，跨北京时间午夜自动重新生成候选。
- 计算结果失败有固定错误提示和重试；本地计算不显示伪造 loading。
- 复制/分享未来节日摘要，使用共享 `SystemReportActions`。
- 22 个节日名称、格式、筛选和错误文案均维护中英文资源；新增共享 `ToolboxBadge` 用于紧凑状态标签。

## 当前不支持

- 官方法定假期区间、调休上班日、工作日提醒和通知。
- 用户自建生日、纪念日、自定义农历/公历重复规则。
- 科室/地区级地方节日数据；官方全国数据也没有城市字段。
- 桌面组件、后台通知、语音播报和历史已发生节日统计。

## 验证范围

定向测试覆盖：

- 春节 2027–2029、中秋、腊八跨公历年、清明节气、公历元旦。
- 闰五月与普通五月区分、当天保留为第一次发生日。
- 北京时间当天/未来倒计时、纽约 DST 47 小时边界、Repository 失败。
- SavedState 筛选和选择恢复、非法选择回退、筛选一致性、失败重试。
- 整秒 ticker 和跨北京时间午夜重建。

最终本地门禁：Android **621/621**、`assembleDebug` 成功、Lint **0 错误 / 80 既有警告**，新增源码和资源无 finding；服务端 **226/226**。APK 为 **23,042,360** 字节。

Android 16 模拟器（1080×2424、中文界面）实际验证：

- 搜索 `holiday` 能打开“节日倒数”，默认选中 2026-10-18 重阳节，显示农历 9 月 9 日、未来三次和“明天 · 06:14:52”倒计时。
- 全部 22 个节日按日期排序；切换“公历节日”后只保留 11 个并自动选中万圣夜，日期、农历和 14 天后状态一致。
- 点击列表中的万圣夜后，主倒计时、未来三次和选中背景同步更新；返回/重进页面后筛选与选中可恢复。
- 底部复制/分享、北京时间说明和“法定放假以官方通知为准”提示可见。

当前检查点尚未提交、构建 V69 服务端候选或进行生产 HTTPS 验收；这些结果在部署后追加。真机、TalkBack、极端字体缩放、官方调休数据与桌面通知仍未验收。
